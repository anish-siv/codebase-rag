package com.anish.codebase_rag.controller;

import com.anish.codebase_rag.model.QueryResponse;
import com.anish.codebase_rag.service.GitHubIngestionService;
import com.anish.codebase_rag.service.IngestionService;
import com.anish.codebase_rag.service.RagService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final IngestionService ingestionService;
    private final RagService ragService;
    private final GitHubIngestionService gitHubIngestionService;
    private final JdbcTemplate jdbcTemplate;

    public RagController(IngestionService ingestionService,
                         RagService ragService,
                         GitHubIngestionService gitHubIngestionService,
                         JdbcTemplate jdbcTemplate) {
        this.ingestionService = ingestionService;
        this.ragService = ragService;
        this.gitHubIngestionService = gitHubIngestionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestParam String path) throws IOException {
        int chunks = ingestionService.ingestDirectory(path);
        return Map.of("message", "Ingestion complete for path: " + path, "chunks", chunks);
    }

    @PostMapping("/ingest/github")
    public Map<String, Object> ingestGitHub(@RequestParam String url) throws IOException, InterruptedException {
        GitHubIngestionService.IngestionSummary summary = gitHubIngestionService.ingestFromGitHub(url);
        return Map.of(
            "message", "GitHub ingestion complete for: " + url,
            "filesIngested", summary.filesIngested(),
            "chunksIngested", summary.chunksIngested()
        );
    }

    @GetMapping("/query")
    public QueryResponse query(@RequestParam String question) {
        return ragService.query(question);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Integer chunkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store", Integer.class);

        List<String> sources = jdbcTemplate.queryForList(
            "SELECT DISTINCT metadata->>'source' FROM vector_store WHERE metadata->>'source' IS NOT NULL",
            String.class);

        return Map.of(
            "totalChunks", chunkCount != null ? chunkCount : 0,
            "totalFiles", sources.size(),
            "sources", sources
        );
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clear() {
        int deleted = jdbcTemplate.update("DELETE FROM vector_store");
        return Map.of("message", "Cleared vector store", "chunksDeleted", deleted);
    }
}
