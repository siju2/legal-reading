# Codex guidance

## Project map

- `src/main/java/com/example/project/legal/`: legal archive, chat, retrieval, and official-source checks.
- `src/main/resources/legal-ai/`: curated legal sources loaded by the chat service.
- `src/main/resources/templates/` and `static/`: Thymeleaf pages, CSS, and browser behavior.
- `local-ai/`: authenticated Python bridge, Ollama search, and separate data/training tools. See `local-ai/README.md` before changing this area.
- `pom.xml`: Java 17 and Spring Boot build.
- Read [README.md](README.md) for app setup and [docs/README.md](docs/README.md) for the document index. `INTEGRATIONS.md` and `local-ai/README.md` cover AI/PDF runtime details. Dated SRS and design files are historical references, not current behavior.

## Common tasks

- Compile: `./scripts/verify.ps1` (PowerShell: `./scripts/verify.ps1`).
- Run the test suite only when requested: `./scripts/verify.ps1 -RunTests`.
- Run from STS with `ProjectApplication`, or use `mvn spring-boot:run` after local DB settings are ready.

## Working rules

- Trace controller, service, browser, and local-AI call paths before changing a behavior.
- Keep legal explanations tied to official sources. Distinguish law text, court holdings, and search snippets; do not invent rules, deadlines, cases, or completed model training.
- Do not expose secrets, local runtime files, case notes, or personal data in logs, searches, commits, or model datasets.
- Do not start or restart Spring, Ollama, or training jobs, and do not modify the database, unless the user asks.
- Do not commit or push unless the user asks.

## Verification

- The verification script runs `mvn -q -DskipTests compile` by default.
- Run tests only when the user requests them or the active task explicitly requires them.
- Report checks that were skipped and any database or runtime behavior that was not exercised.
