package com.anish.codebase_rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final ChatClient chatClient;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) { 
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .vectorStore(vectorStore)
                                        .build())
                                .build()
                )
                .build();
    }

    public String query(String question) { 
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}