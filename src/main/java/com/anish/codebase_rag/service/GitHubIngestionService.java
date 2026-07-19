package com.anish.codebase_rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubIngestionService {

    private static final long MAX_FILE_SIZE = 50 * 1024; // 50 KB

    private final IngestionService ingestionService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubIngestionService(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public record IngestionSummary(int filesIngested, int chunksIngested) {}

    public IngestionSummary ingestFromGitHub(String repoUrl) throws IOException, InterruptedException {
        String normalized = repoUrl.replaceFirst("https?://github\\.com/", "").replaceAll("\\.git$", "");
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + repoUrl);
        }
        String owner = parts[0];
        String repo = parts[1];

        String treeUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/HEAD?recursive=1";
        HttpRequest treeRequest = HttpRequest.newBuilder()
            .uri(URI.create(treeUrl))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "codebase-rag")
            .build();

        HttpResponse<String> treeResponse = httpClient.send(treeRequest, HttpResponse.BodyHandlers.ofString());
        if (treeResponse.statusCode() != 200) {
            throw new IOException("GitHub API returned HTTP " + treeResponse.statusCode()
                + " for " + treeUrl + ": " + treeResponse.body());
        }

        JsonNode tree = objectMapper.readTree(treeResponse.body()).get("tree");
        if (tree == null || !tree.isArray()) {
            throw new IOException("Unexpected GitHub API response: missing 'tree' array");
        }

        List<Document> allDocs = new ArrayList<>();
        int filesIngested = 0;
        String repoId = owner + "/" + repo;

        for (JsonNode node : tree) {
            if (!"blob".equals(node.path("type").asText())) continue;

            String path = node.path("path").asText();
            if (!ingestionService.isCodeFile(path)) continue;

            long size = node.path("size").asLong(0);
            if (size > MAX_FILE_SIZE) {
                System.out.println("Skipping large file (" + size + " bytes): " + path);
                continue;
            }

            String rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/HEAD/" + path;

            try {
                HttpRequest fileRequest = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .header("User-Agent", "codebase-rag")
                    .build();
                HttpResponse<String> fileResponse = httpClient.send(fileRequest, HttpResponse.BodyHandlers.ofString());

                if (fileResponse.statusCode() != 200) {
                    System.err.println("Skipping " + path + " (HTTP " + fileResponse.statusCode() + ")");
                    continue;
                }

                String sourcePath = "github:" + owner + "/" + repo + "/" + path;
                List<Document> chunks = ingestionService.chunkContent(sourcePath, fileResponse.body(), repoId);
                allDocs.addAll(chunks);
                filesIngested++;
            } catch (Exception e) {
                System.err.println("Failed to fetch " + rawUrl + ": " + e.getMessage());
            }
        }

        if (!allDocs.isEmpty()) {
            ingestionService.addDocuments(allDocs);
        }

        System.out.println("GitHub ingestion complete: " + filesIngested + " files, " + allDocs.size() + " chunks from " + owner + "/" + repo);
        return new IngestionSummary(filesIngested, allDocs.size());
    }
}
