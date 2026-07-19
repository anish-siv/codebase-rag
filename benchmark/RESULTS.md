# Benchmark Results — codebase-rag

Run on Jul 18, 2026. App: Java 21 / Spring Boot 4.1 / Spring AI 2.0, local pgvector
(Docker, `pgvector/pgvector:pg16`), OpenAI `text-embedding-3-small`, Anthropic
`claude-sonnet-4-6`. Single instance, sequential ingestion, no batching/caching.

## 1. Ingestion

| Test | Files | Chunks | Chunks/file | Wall time | Files/sec | Chunks/sec |
|---|---|---|---|---|---|---|
| Local ingest (this repo, `/api/ingest`) | 9 | 14 | 1.56 | 2.58s | 3.49 | 5.43 |
| GitHub ingest, no clone (`spring-projects/spring-petclinic`, `/api/ingest/github`) | 50 | 123 | 2.46 | 12.11s | 4.13 | 10.16 |

- GitHub ingestion fetches the repo tree via GitHub's Trees API (`git/trees/HEAD?recursive=1`)
  and file contents via `raw.githubusercontent.com`, with no `git clone` step.
- Filters to 5 extensions (`.java .js .ts .py .md`) and a 50KB per-file cap; all 50
  matched files in spring-petclinic were under the cap (repo tree not truncated).
- Fully sequential — one HTTP fetch and one embed call path per run (batched as a
  single `vectorStore.add()`), no thread pool/parallelism.

## 2. Query latency (15 real questions against the ingested spring-petclinic corpus)

| Metric | Value |
|---|---|
| n | 15 |
| min | 5.94s |
| p50 | 7.60s |
| p95 | 8.73s |
| max | 10.36s |
| mean | 7.50s |

End-to-end includes: question embedding call → pgvector cosine top-5 search → Claude
`claude-sonnet-4-6` generation (non-streaming). Claude generation is the dominant cost.

## 3. Retrieval quality (top-5 hit-rate)

14/15 = **93%** — the question's expected source file appeared in the top-5 retrieved
chunks' `source` metadata.

The one miss ("What common fields do Owner and Vet share via a shared base or Person
class?") retrieved `Owner.java`, `Vet.java`, `PetController.java` but not `Person.java`
itself — yet Claude still answered correctly because the retrieved `Owner.java`/`Vet.java`
chunks contain the line `extends Person`. Answer was grounded-adjacent, not a pure miss.

Full per-question detail: `benchmark/results/query_bench.json`.

## 4. Repo-scoped isolation (verification test)

Originally, `vector_store` had no repo identifier and every query
searched across every ingested repo with no isolation — a real
cross-contamination bug, not just a theoretical one (see `GUIDE.md` §12
point 1 for the full story). Fixed by tagging each chunk's metadata with
a `repo` field at ingestion and adding an optional `Filter.Expression`
filter at query time. Verified by deliberately reproducing the original
bug scenario and confirming the fix:

1. Ingested this repo locally (37 chunks) and `spring-projects/spring-petclinic`
   from GitHub (123 chunks) into the same table, **without clearing in
   between** — the exact scenario that used to cause contamination.
2. Asked the identical question ("What fields does the `Owner` class
   have?" — a question entirely about petclinic's domain) three ways:

| Scope | Sources returned |
|---|---|
| `repo=<local path>` | Only local-repo files (`GUIDE.md`, `benchmark/RESULTS.md`, `HELP.md`, `QueryResponse.java`) — zero petclinic leakage |
| `repo=spring-projects/spring-petclinic` | Only `Owner.java`/`OwnerController.java` — zero local-repo leakage |
| *(no `repo` param)* | Petclinic sources — old unscoped behavior preserved exactly, confirming backward compatibility |

3. `DELETE /api/clear?repo=<local path>` removed exactly the 37 local
   chunks (`chunksDeleted: 37`) and left all 123 petclinic chunks and
   their `repo` tag untouched — confirmed via `/api/status` immediately
   after.

## Repro

```bash
# 1. infra
docker run -d --name pgvector-db -p 5432:5432 -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ragdb pgvector/pgvector:pg16

# 2. app (reads .env for ANTHROPIC_API_KEY / OPENAI_API_KEY)
./run.sh

# 3. benchmarks
curl -s -X POST "http://localhost:8082/api/ingest?path=$(pwd)"
curl -s -X DELETE http://localhost:8082/api/clear
curl -s -X POST "http://localhost:8082/api/ingest/github?url=https://github.com/spring-projects/spring-petclinic"
python3 benchmark/query_bench.py

# 4. repo-scoped isolation (no clear between the two ingests, on purpose)
curl -s -X DELETE http://localhost:8082/api/clear
curl -s -X POST "http://localhost:8082/api/ingest?path=$(pwd)"
curl -s -X POST "http://localhost:8082/api/ingest/github?url=https://github.com/spring-projects/spring-petclinic"
curl -s "http://localhost:8082/api/status" | python3 -c "import json,sys; print(json.load(sys.stdin)['repos'])"
curl -s -G "http://localhost:8082/api/query" --data-urlencode "question=What fields does the Owner class have?" --data-urlencode "repo=$(pwd)"
curl -s -G "http://localhost:8082/api/query" --data-urlencode "question=What fields does the Owner class have?" --data-urlencode "repo=spring-projects/spring-petclinic"
curl -s -X DELETE "http://localhost:8082/api/clear?repo=$(pwd)"
```
