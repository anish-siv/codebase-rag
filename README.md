# Codebase RAG

A Spring Boot application that answers natural language questions about 
any codebase, grounded in the actual source code with file-level citations.

## Features

- **Local ingestion** — point it at any local directory
- **GitHub ingestion** — paste any public GitHub repo URL, no cloning needed
- **Source citations** — every answer shows which files it retrieved from
- **Index management** — view ingested files/chunk counts, clear the index
- **Web UI** — chat interface with dark theme, no external dependencies
- **Chunk overlap** — 10-line overlap between chunks prevents context loss 
  at boundaries

## How it works

1. Source files are chunked (50 lines, 10-line overlap) and embedded via 
   OpenAI text-embedding-3-small, stored in pgvector
2. At query time, the question is embedded and pgvector finds the 
   semantically closest chunks via cosine similarity
3. Retrieved chunks are injected into a Claude prompt via Spring AI's 
   RetrievalAugmentationAdvisor
4. Claude generates a grounded answer — citations show exactly which 
   files were used

## Tech Stack

- Java 21 / Spring Boot 4.1 / Spring AI 2.0
- pgvector (PostgreSQL) via Docker
- OpenAI text-embedding-3-small
- Anthropic Claude claude-sonnet-4-6
- Vanilla HTML/CSS/JS frontend

## Setup

1. `docker run -d --name pgvector-db -p 5432:5432 -e POSTGRES_USER=postgres 
   -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ragdb pgvector/pgvector:pg16`
2. Set `ANTHROPIC_API_KEY` and `OPENAI_API_KEY` environment variables
3. `./mvnw spring-boot:run`
4. Open `http://localhost:8082`
