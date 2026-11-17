# MVP verification

Local validation performed before the final MVP delivery commit:

| Check | Result |
|---|---|
| Maven `verify` on Java 21 | Passed |
| Unit/controller tests | 42 passed, 0 skipped |
| PostgreSQL 17 integration tests | 16 passed, 0 skipped |
| Concurrent SELL race | One accepted, one rejected; quantity remains valid |
| Concurrent duplicate request | One inserted trade; retry returns original |
| Deletion racing first trade | Either empty portfolio deleted or history retained; no lost trade |
| Held database row lock | Retryable 503; no partial trade |
| Docker Compose configuration | Valid |
| Multi-stage Docker image build | Passed |
| Container application/database health | Healthy |
| Running API smoke test | Passed |
| App restart and image recreation | Original three-trade history and $300 synthetic P&L retained |

The smoke test uses synthetic AAPL=$125 pricing and the documented average-cost example. It verifies a $1,125 position value, $136.80 realized gain, $163.20 unrealized gain, and $300 total P&L. It also checks retry identity and rejected overselling through real HTTP requests.

Reproduce with:

```sh
./mvnw verify
# Create .env from .env.example and set a local password first.
docker compose --profile app up --build -d --wait
python3 scripts/smoke-test.py
```

These are local build and test results, not production deployment or performance claims. GitHub Actions independently runs Java verification and a container smoke job on subsequent pushes. Check the Actions run for its actual result.
