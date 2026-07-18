package com.anish.codebase_rag.service;

import com.anish.codebase_rag.model.QueryResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public QueryResponse query(String question) {
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(question).topK(5).build()
        );

        StringBuilder context = new StringBuilder();
        LinkedHashSet<String> seenSources = new LinkedHashSet<>();

        for (Document doc : docs) {
            context.append(doc.getText()).append("\n\n---\n\n");
            Object source = doc.getMetadata().get("source");
            if (source != null) {
                seenSources.add(source.toString());
            }
        }

        String answer = chatClient.prompt()
            .system("You are a helpful assistant that answers questions about a codebase. " +
                    "Use the provided context to answer accurately. " +
                    "Reference specific code details when relevant. " +
                    "If the context does not contain enough information, say so.")
            .user("Context:\n" + context + "\nQuestion: " + question)
            .call()
            .content();

        return new QueryResponse(answer, new ArrayList<>(seenSources));
    }
}
