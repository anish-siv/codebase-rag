package com.anish.codebase_rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class IngestionService {

    private final VectorStore vectorStore;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingestDirectory(String directoryPath) throws IOException {
        List<Document> documents = new ArrayList<>();
        Path rootPath = Paths.get(directoryPath);
        String repo = rootPath.toAbsolutePath().normalize().toString();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isCodeFile)
                 .forEach(filePath -> {
                     try {
                         String content = Files.readString(filePath);
                         List<Document> chunks = chunkContent(filePath.toString(), content, repo);
                         documents.addAll(chunks);
                     } catch (IOException e) {
                         System.err.println("Failed to read: " + filePath);
                     }
                 });
        }

        vectorStore.add(documents);
        System.out.println("Ingested " + documents.size() + " chunks from " + directoryPath);
        return documents.size();
    }

    /**
     * @deprecated kept for callers that don't have a repo identifier; prefer
     * {@link #chunkContent(String, String, String)}. Tags chunks with a null repo,
     * meaning they'll only surface in unscoped (repo=null) queries.
     */
    @Deprecated
    public List<Document> chunkContent(String sourcePath, String content) {
        return chunkContent(sourcePath, content, null);
    }

    public List<Document> chunkContent(String sourcePath, String content, String repo) {
        List<Document> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        int chunkSize = 50;
        int overlap = 10;
        int stride = chunkSize - overlap; // 40
        int chunkIndex = 0;

        for (int start = 0; start < lines.length; start += stride) {
            int end = Math.min(start + chunkSize, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(lines[i]).append("\n");
            }
            String chunkText = sb.toString().trim();
            if (!chunkText.isEmpty()) {
                Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("source", sourcePath);
                metadata.put("chunk", chunkIndex);
                if (repo != null) {
                    metadata.put("repo", repo);
                }
                Document doc = new Document(chunkText, metadata);
                chunks.add(doc);
                chunkIndex++;
            }
            if (end == lines.length) break;
        }
        return chunks;
    }

    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }

    public boolean isCodeFile(Path path) {
        return isCodeFile(path.toString());
    }

    public boolean isCodeFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".js") ||
               lower.endsWith(".ts") || lower.endsWith(".py") || lower.endsWith(".md");
    }
}
