# Codebase RAG

A Spring Boot application that lets you ask natural language questions 
about any codebase and get answers grounded in the actual source code.

## How it works

1. **Ingest** a codebase — source files are chunked, embedded via 
   OpenAI's text-embedding-3-small, and stored in pgvector
2. **Query** in natural language — the question is embedded, similarity 
   search retrieves the most relevant chunks, and Claude generates a 
   grounded answer using only the retrieved context

## Tech Stack

- Java 21 / Spring Boot 4.1
- Spring AI 2.0 (RetrievalAugmentationAdvisor, VectorStoreDocumentRetriever)
- pgvector (PostgreSQL vector extension) via Docker
- OpenAI text-embedding-3-small for embeddings
- Anthropic Claude (claude-sonnet-4-6) for answer generation

## Endpoints

POST /api/ingest?path=/path/to/repo — ingest a codebase  
GET /api/query?question=your+question — query the ingested codebase

## Setup

1. Run pgvector: `docker run -d --name pgvector-db -p 5432:5432 
   -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres 
   -e POSTGRES_DB=ragdb pgvector/pgvector:pg16`
2. Set environment variables: `ANTHROPIC_API_KEY` and `OPENAI_API_KEY`
3. Run: `./mvnw spring-boot:run`
