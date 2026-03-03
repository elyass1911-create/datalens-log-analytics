# Finalization Checklist

1. Replace CI badge placeholder in `README.md`:
   - `https://github.com/<your-org>/<your-repo>/actions/workflows/ci.yml/badge.svg`
   - `https://github.com/<your-org>/<your-repo>/actions/workflows/ci.yml`

2. Start infrastructure and app:
   - `docker compose up -d`
   - `./mvnw spring-boot:run`

3. Run tests with Docker enabled:
   - `./mvnw test`

4. Generate proof artifacts:
   - `pwsh ./scripts/final-proof.ps1 -BaseUrl http://localhost:8081 -Dataset 100000 -Iterations 5`

5. Copy real outputs from `docs/proof/` into `README.md`:
   - benchmark JSON snippets
   - explain snippets
   - benchmark snapshot table values

6. Push branch and verify green CI run.
