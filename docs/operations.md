# Local operations and public-repository readiness

This is a local, single-user paper portfolio tracker. Publishing source code is separate from publishing a running service. Do not expose the unauthenticated application to the internet.

## Container run

1. Copy `.env.example` to `.env` and set a local password.
2. Run `docker compose --profile app up --build -d --wait`.
3. Run `python3 scripts/smoke-test.py`.
4. Open http://localhost:8080/swagger-ui/index.html.

The app runs as a non-root user. Only localhost ports are published. The database and app have health checks, and the app waits for database readiness. The default `docker compose up -d --wait` starts only the database for Java development. Do not run a host Java process and the container app on the same port.

`APP_PORT` and `DB_PORT` optionally change host ports. For example, set `APP_PORT=8081` in `.env` and run the smoke test against `http://localhost:8081`. If changing `DB_PORT` for host Java development, update `DB_URL` to the same host port; the container app always connects to the internal database port.

The Docker build packages the app; the separate `./mvnw verify` CI gate runs tests. A successful image build alone does not establish test success.

## Persistence and migrations

`docker compose --profile app restart app` restarts the service while retaining PostgreSQL data. Retrieve an existing portfolio ID afterward to confirm persistence. `docker compose --profile app down` retains the named volume. Removing the named volume deletes the local database; do this only when intentionally discarding your paper data.

Flyway runs before JPA schema validation. Applied migrations are immutable. A migration failure prevents startup; fix forward with a reviewed migration or restore from a backup. Do not enable automatic Hibernate schema updates.

Backup while the database is running:

```sh
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > portfolio-backup.sql
```

Restore into an empty local database, after stopping the app to prevent writes:

```sh
docker compose stop app
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < portfolio-backup.sql
```

A restore must target an empty schema/database with compatible migration history. Keep backups private; they contain portfolio history.

## Health and troubleshooting

- `/actuator/health` reports UP only when the database health check succeeds; detailed internals are hidden.
- View logs with `docker compose logs app` or `docker compose logs db`.
- Database credentials must match the initialized volume. Changing `.env` alone does not change existing database users.
- A 503 `LOCK_TIMEOUT` means retry a trade using its original idempotency key.
- Incomplete analytics remain HTTP 200 with explicit quote statuses and null current totals. `MOCK` never means live data.
- Tests use disposable PostgreSQL containers and fail if Docker is unavailable.

## Before making the repository public

Keep `.env`, credentials, private keys, account statements, database dumps, and personal data out of both current files and Git history. `.env.example` contains placeholders only; `.gitignore` excludes local environment files and SQL backups. Enable GitHub secret scanning/push protection and review CI results. If a credential was ever committed, revoke/rotate it; deleting the latest copy does not remove history.

Use real commits with corresponding tests and accurate notes. Git pushes and CI test runs are not production deployments. Do not claim performance metrics or a hosted service without evidence.

Before a future public deployment, add Spring Security authentication and a required portfolio owner tied to the authenticated principal. Scope every read/write to that owner and test cross-user access. Use managed secrets, HTTPS, private database connectivity, and CSRF protection if using cookie sessions. Those features are outside this local MVP.
