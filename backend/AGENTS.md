# Backend Agent Guidance

## Stack And Layout

- Spring Boot `4.0.1`, Java `26`, Spring Web, Spring Data JPA, and PostgreSQL are declared in [pom.xml](pom.xml).
- [DemoApplication.java](src/main/java/com/example/demo/DemoApplication.java) is the entrypoint in package `com.example.demo`.
- The feature code is currently nested under `com.example.demo.com.medicalchatbot`: controller, service, and repository classes should keep package declarations and imports aligned with their paths.
- [ChatController.java](src/main/java/com/example/demo/com/medicalchatbot/controller/ChatController.java) owns `/api/chat`; [AiService.java](src/main/java/com/example/demo/com/medicalchatbot/service/AiService.java) performs repository-backed keyword matching rather than calling an external AI provider.

## API Contract

- `GET /api/chat` is a health response.
- `POST /api/chat` accepts `{ "message": "..." }` and returns `{ "reply": "..." }`.
- CORS is currently limited to `http://localhost:3000`.

## Commands

Run from `backend/`:

```powershell
$env:SUPABASE_DB_URL = "jdbc:postgresql://<pooler-host>:6543/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME = "postgres.<project-ref>"
$env:SUPABASE_DB_PASSWORD = "<database-password>"
$env:FRONTEND_ORIGIN = "http://localhost:3000"
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

There are currently no `src/test` tests. A clean Maven verification is the first check for Java changes; distinguish baseline compilation failures from regressions introduced by a change.

## Data And Configuration

- [application.properties](src/main/resources/application.properties) connects directly to a Supabase PostgreSQL pooler and uses `ddl-auto=update`; do not assume the local Supabase service is the database used by this app.
- Runtime database settings are required through `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD`; copy the names from [.env.example](.env.example), but never commit populated values.
- Never commit or repeat database credentials. Use environment-backed Spring properties or untracked local configuration, and rotate credentials that have already appeared in source or environment files.
- The active entity/repository files live under `com.example.demo.com.medicalchatbot.repository`. Before editing persistence code, verify the entity's package, filename, imports, and table mapping; a duplicate malformed `model/MedicalEntry,java` file and stale `target/` output are not authoritative source.
- The backend Postman request is under `postman/collections/MEDICAL CHATBOT/`; use `POST` with a JSON body when testing chat behavior rather than relying on the health `GET`.
