package com.anish.codebase_rag.controller;

import com.anish.codebase_rag.service.IngestionService;
import com.anish.codebase_rag.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class RagController {

    private final IngestionService ingestionService;
    private final RagService ragService;

    public RagController(IngestionService ingestionService, RagService ragService) {
        this.ingestionService = ingestionService;
        this.ragService = ragService;
    }

    @PostMapping("/ingest")
    public String ingest(@RequestParam String path) throws IOException {
        ingestionService.ingestDirectory(path);
        return "Ingestion complete for path: " + path;
    }

    @GetMapping("/query")
    public String query(@RequestParam String question) {
        return ragService.query(question);
    }
}