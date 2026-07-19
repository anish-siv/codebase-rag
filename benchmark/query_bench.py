#!/usr/bin/env python3
"""Benchmark /api/query latency and top-5 retrieval hit-rate against the
ingested spring-projects/spring-petclinic repo."""
import json
import time
import urllib.parse
import urllib.request

BASE = "http://localhost:8082/api/query"

# (question, expected substring(s) that should appear in at least one returned source)
CASES = [
    ("What fields does the Owner class have?", ["owner/Owner.java"]),
    ("How does the OwnerController handle listing owners by last name?", ["owner/OwnerController.java"]),
    ("What HTTP endpoints does PetController define for adding or editing a pet?", ["owner/PetController.java"]),
    ("How is a Vet's specialty represented in the data model?", ["vet/Specialty.java", "vet/Vet.java"]),
    ("What does VisitController do when saving a new visit for a pet?", ["owner/VisitController.java"]),
    ("How does CrashController simulate an error for testing?", ["system/CrashController.java"]),
    ("What caching configuration is used in this application?", ["system/CacheConfiguration.java"]),
    ("How is a pet's type formatted for display in the UI?", ["owner/PetTypeFormatter.java"]),
    ("What validation rules exist for a Pet, like required fields?", ["owner/PetValidator.java"]),
    ("What is the main entry point class that boots this Spring Boot application?", ["PetClinicApplication.java"]),
    ("What repository methods exist for querying Vets from the database?", ["vet/VetRepository.java"]),
    ("How does WelcomeController map requests to the home page?", ["system/WelcomeController.java"]),
    ("What common fields do Owner and Vet share via a shared base or Person class?", ["model/Person.java", "model/BaseEntity.java"]),
    ("What relational databases are covered by the integration tests in this project?", ["MySqlIntegrationTests.java", "PostgresIntegrationTests.java"]),
    ("According to the README, how do you run this application locally?", ["README.md"]),
]

results = []
for i, (question, expected) in enumerate(CASES, 1):
    q = urllib.parse.urlencode({"question": question})
    url = f"{BASE}?{q}"
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=60) as resp:
            body = json.loads(resp.read().decode())
    except Exception as e:
        body = {"answer": f"ERROR: {e}", "sources": []}
    elapsed = time.perf_counter() - t0

    sources = body.get("sources", [])
    hit = any(any(exp in s for s in sources) for exp in expected)
    results.append({
        "i": i,
        "question": question,
        "expected": expected,
        "sources": sources,
        "elapsed_s": round(elapsed, 2),
        "hit": hit,
        "answer_preview": (body.get("answer") or "")[:160],
    })
    print(f"[{i:2}/{len(CASES)}] {elapsed:6.2f}s  hit={hit!s:5}  {question[:60]}")

with open("benchmark/results/query_bench.json", "w") as f:
    json.dump(results, f, indent=2)

times = sorted(r["elapsed_s"] for r in results)
n = len(times)
def pct(p):
    idx = min(n - 1, int(round(p * (n - 1))))
    return times[idx]

hits = sum(1 for r in results if r["hit"])
print("\n--- summary ---")
print(f"n={n}  min={times[0]:.2f}s  p50={pct(0.5):.2f}s  p95={pct(0.95):.2f}s  max={times[-1]:.2f}s  mean={sum(times)/n:.2f}s")
print(f"top-5 retrieval hit-rate: {hits}/{n} = {100*hits/n:.0f}%")
