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
| min | 4.71s |
| p50 | 7.32s |
| p95 | 8.98s |
| max | 10.36s |
| mean | 7.31s |

(Re-measured alongside §5 below when re-verifying the repo-scoping fix
didn't change unscoped behavior; original run was 5.94/7.60/8.73/10.36/7.50s
— within normal run-to-run LLM latency variance.)

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

## 5. Repo-scoped filtering: latency overhead

The isolation test above proves *correctness*. This measures *cost*: does
adding a `Filter.Expression` (`metadata->>'repo' = ?`) to the pgvector
query change latency? Re-ran the same 15-question benchmark from §2 twice
against the same single-repo corpus (`spring-petclinic`, 123 chunks) —
once unscoped, once with `repo=spring-projects/spring-petclinic` — so any
difference is attributable to the filter itself, not a different corpus.

| Mode | p50 | p95 | mean | max | Hit-rate |
|---|---|---|---|---|---|
| Unscoped (original code path) | 7.32s | 8.98s | 7.31s | 10.36s | 14/15 (93%) |
| Scoped (`repo=` filter applied) | 7.27s | 8.48s | 7.39s | 10.78s | 14/15 (93%) |

No measurable difference — the ~50-200ms deltas are noise from Claude's
generation call (which dominates at ~7s), not signal from the filter.
Makes sense: there's no index on `metadata->>'repo'` (or on `embedding` —
see `GUIDE.md` §10), so both modes already do a full sequential scan of
`vector_store`; adding one more predicate evaluated per row during that
same scan is negligible next to a multi-second LLM call. The exact same
single question missed retrieval in both runs (`Person.java`, same as
§3), confirming the filter didn't change which chunks get retrieved when
only one repo is ingested — expected, since scoping to the only ingested
repo can't exclude anything.

Full per-question detail: `benchmark/results/query_bench.json` (unscoped),
`benchmark/results/query_bench_scoped.json` (scoped).

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

# 5. scoped vs. unscoped latency (single-repo corpus, so it's an apples-to-apples comparison)
curl -s -X DELETE http://localhost:8082/api/clear
curl -s -X POST "http://localhost:8082/api/ingest/github?url=https://github.com/spring-projects/spring-petclinic"
python3 benchmark/query_bench.py
python3 benchmark/query_bench.py --repo spring-projects/spring-petclinic
```
