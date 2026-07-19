# Codebase RAG

A Spring Boot application that answers natural language questions about 
any codebase, grounded in the actual source code with file-level citations.

🔗 **Live demo:** [codebase-rag-xxsc.onrender.com](https://codebase-rag-xxsc.onrender.com/) 
— pre-loaded with [`spring-projects/spring-petclinic`](https://github.com/spring-projects/spring-petclinic). 
Querying is open to anyone; ingest/clear are gated behind an admin key (see 
[Deployment](#deployment)). First request may take ~30s to respond if the 
free-tier instance had spun down from inactivity.

## Features

- **Local ingestion** — point it at any local directory
- **GitHub ingestion** — paste any public GitHub repo URL, no cloning needed
- **Source citations** — every answer shows which files it retrieved from
- **Index management** — view ingested files/chunk counts, clear the index
- **Web UI** — chat interface with dark theme, no external dependencies
- **Chunk overlap** — 10-line overlap between chunks prevents context loss 
  at boundaries
- **Repo-scoped querying** — ingest multiple repos without clearing 
  between them, then scope a question (or a clear) to just one via the 
  Query Scope selector, so answers don't mix sources across repos

## How it works

1. Source files are chunked (50 lines, 10-line overlap) and embedded via 
   OpenAI text-embedding-3-small, stored in pgvector
2. At query time, the question is embedded and pgvector's `VectorStore` 
   finds the top-5 semantically closest chunks via cosine similarity
3. Retrieved chunks are concatenated into context and passed to Claude 
   via Spring AI's `ChatClient`
4. Claude generates a grounded answer — citations show exactly which 
   files were used

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/ingest?path=` | Ingest a local directory |
| `POST` | `/api/ingest/github?url=` | Ingest a public GitHub repo (no cloning) |
| `GET` | `/api/query?question=&repo=` | Ask a question; optional `repo` scopes retrieval to one ingested repo |
| `GET` | `/api/status` | View indexed file/chunk counts and the list of distinct ingested repos |
| `DELETE` | `/api/clear?repo=` | Clear the vector store; optional `repo` clears just that repo |

Ingestion is filtered to 5 extensions (`.java .js .ts .py .md`); GitHub 
ingestion additionally caps individual files at 50KB. Each chunk is tagged 
with a `repo` identifier (the local directory path, or `owner/repo` for 
GitHub) — omit `repo` on `/api/query` to search across all ingested repos 
at once (useful for a single-repo workflow; scope explicitly once you've 
ingested more than one).

## Benchmarks

Measured against a real 50-file public repo (`spring-projects/spring-petclinic`) 
and 15 real questions — see [`benchmark/RESULTS.md`](benchmark/RESULTS.md) 
for methodology and full results.

| Metric | Result |
|---|---|
| GitHub ingestion throughput | 50 files / 123 chunks in 12.1s (~4.1 files/sec) |
| Query latency (end-to-end) | p50 7.3s, p95 9.0s |
| Top-5 retrieval hit-rate | 14/15 (93%) |
| Repo-scoped filter overhead | None measurable (p50 7.3s unscoped vs. 7.3s scoped) |

## Limitations

- **Query latency** — Claude generation dominates end-to-end latency 
  (5.3–9.7s per call, measured), vs. under 2.4s for embedding + pgvector 
  retrieval combined; streaming responses would improve perceived 
  responsiveness
- **GitHub ingestion is sequential** — files are fetched one HTTP request 
  at a time with no concurrency; parallelizing fetches would improve 
  throughput on large repos (embedding calls themselves are already 
  batched by Spring AI's default token-count-based batching strategy)
- **No incremental updates** — re-ingesting a repo after file changes 
  requires clearing and re-indexing from scratch; there's no dedup or 
  upsert-by-source logic
- **GitHub tree truncation** — the GitHub Trees API caps recursive results 
  at 100,000 entries / 7MB; very large monorepos may return incomplete 
  file lists (not detected or handled)
- **Fixed chunk boundaries** — 50-line splits don't respect function or 
  class boundaries; semantic/AST-aware chunking would improve retrieval 
  precision

## Tech Stack

- Java 21 / Spring Boot 4.1 / Spring AI 2.0
- pgvector (PostgreSQL) via Docker
- OpenAI text-embedding-3-small
- Anthropic Claude claude-sonnet-4-6
- Vanilla HTML/CSS/JS frontend

## Setup

Requires **JDK 21** (check with `java -version`; if you have an older 
default JDK on your `PATH`, point `JAVA_HOME` at a JDK 21 install before 
running).

1. `docker run -d --name pgvector-db -p 5432:5432 -e POSTGRES_USER=postgres 
   -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ragdb pgvector/pgvector:pg16`
2. `cp .env.example .env` and fill in your `ANTHROPIC_API_KEY` and 
   `OPENAI_API_KEY` (never commit this file — it's gitignored)
3. `./run.sh` (sources `.env` automatically and boots the app)
4. Open `http://localhost:8082`

## Deployment

The live demo runs on [Render](https://render.com) (Docker web service, 
free tier) backed by [Neon](https://neon.tech) (serverless Postgres with 
the pgvector extension). The `Dockerfile` is a multi-stage build (Maven 
build stage → JRE runtime stage, non-root user), and `render.yaml` is a 
Render Blueprint that provisions the service and declares the required 
env vars (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `ADMIN_KEY`, 
`SPRING_DATASOURCE_*`) as secrets to be filled in on deploy.

Because the app is public, mutating endpoints (`/api/ingest`, 
`/api/ingest/github`, `/api/clear`) are gated behind a shared-secret 
`X-Admin-Key` header, enforced by `AdminAuthInterceptor`. If `ADMIN_KEY` 
is unset (the local-dev default), the gate is a no-op; setting it in the 
deployment environment turns it on. Read-only querying (`/api/query`, 
`/api/status`) is never gated — the point of the demo is for anyone to 
ask it questions.
