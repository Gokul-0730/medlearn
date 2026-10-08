# Medical Chatbot

## Repository Map

- `frontend/` is the Next.js 16 App Router client. Follow [frontend/AGENTS.md](frontend/AGENTS.md) before changing Next.js code; it contains generated version-specific guidance. See [frontend/README.md](frontend/README.md) for the starter documentation.
- `backend/` is the Spring Boot service. Follow [backend/AGENTS.md](backend/AGENTS.md) for package, API, and run details.
- `supabase/` contains local Supabase configuration in [supabase/config.toml](supabase/config.toml). No migrations or seed file are currently present.
- `backend/postman/` contains the backend collection; the root `postman/` tree contains parallel workspace resources. Preserve both unless the task explicitly targets one.

## Local Workflow

Run commands from the directory named below:

```powershell
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

```powershell
cd frontend
npm run lint
npm run build
npm run dev
```

The frontend normally runs on `http://localhost:3000`; the backend runs on `http://localhost:8080`. There are no application-level test suites or test scripts currently committed, so lint, build, and Maven verification are the baseline checks.

## Integration Boundaries

- The browser sends `POST /api/chat` to the backend with `{ "message": "..." }` and expects `{ "reply": "..." }`.
- Backend CORS currently allows only `http://localhost:3000`; update it deliberately when adding another client origin.
- Frontend Supabase clients in `frontend/lib/` are separate from the backend's direct JPA/PostgreSQL connection. Do not assume frontend auth is attached to backend requests.

## Configuration And Secrets

- Never add credentials, tokens, private keys, or database URLs with passwords to source, prompts, logs, Postman exports, or client-exposed `NEXT_PUBLIC_*` variables.
- The current backend properties contain database credentials and the frontend environment has sensitive values; treat them as compromised, do not repeat them, and move runtime configuration to untracked environment variables or external configuration as part of any related change.
- Be cautious with `spring.jpa.hibernate.ddl-auto=update`: it can change a remote schema at startup.

## Change Guidance

- Keep changes within the owning app (`frontend/` or `backend/`) unless the API or shared configuration requires a cross-project edit.
- Before frontend work, read the relevant installed Next.js guidance under `frontend/node_modules/next/dist/docs/` as required by [frontend/AGENTS.md](frontend/AGENTS.md).
- Prefer existing Supabase skills under `frontend/.agents/skills/` or `backend/.agents/skills/` for Supabase, auth, database, or RLS changes.
- Treat `frontend/.next/` and `backend/target/` as generated output; do not edit or review them as source.
