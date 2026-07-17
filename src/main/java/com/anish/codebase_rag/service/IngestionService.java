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
import java.util.stream.Stream;

@Service
public class IngestionService {

    private final VectorStore vectorStore;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestDirectory(String directoryPath) throws IOException {
        List<Document> documents = new ArrayList<>();
        Path rootPath = Paths.get(directoryPath);

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isCodeFile)
                 .forEach(filePath -> {
                     try {
                         String content = Files.readString(filePath);
                         List<Document> chunks = chunkFile(filePath, content);
                         documents.addAll(chunks);
                     } catch (IOException e) {
                         System.err.println("Failed to read: " + filePath);
                     }
                 });
        }

        vectorStore.add(documents);
        System.out.println("Ingested " + documents.size() + " chunks from " + directoryPath);
    }

    private List<Document> chunkFile(Path filePath, String content) {
        List<Document> chunks = new ArrayList<>(); 
        String[] lines = content.split("\n"); 
        StringBuilder currentChunk = new StringBuilder(); 
        int chunkIndex = 0; 
        int chunkSize = 50;

        for (int i = 0; i < lines.length; i++) {
            currentChunk.append(lines[i]).append("\n");

            if ((i + 1) % chunkSize == 0 || i == lines.length - 1) {
                String chunkText = currentChunk.toString().trim();
                if (!chunkText.isEmpty()) {
                    Document doc = new Document(
                        chunkText,
                        java.util.Map.of(
                            "source", filePath.toString(),
                            "chunk", chunkIndex
                        )
                    );
                    chunks.add(doc);
                    chunkIndex++;
                }
                currentChunk = new StringBuilder();
            }
        }
        return chunks;
    }

    private boolean isCodeFile(Path path) {
        String fileName = path.toString().toLowerCase();
        return fileName.endsWith(".java") ||
               fileName.endsWith(".js") ||
               fileName.endsWith(".ts") ||
               fileName.endsWith(".py") ||
               fileName.endsWith(".md");
    }
}