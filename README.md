# MedLearn — Clinical Education & Medical AI Platform

MedLearn is a full-stack medical learning and clinical decision-support application built with Next.js, Java Spring Boot, and Supabase PostgreSQL with pgvector. It features an evidence-grounded AI medical chatbot, clinical symptoms triage, emergency SOS with GPS accuracy validation, multiple-choice practice (MCQ), oral viva exam simulator, and interactive clinical case studies with responsive iOS Liquid Glass styling.

---

## Technology Stack

- **Frontend**: Next.js 16 (App Router, Turbopack), React 19, TypeScript, Tailwind CSS, Lucide Icons.
- **Backend**: Java 21, Spring Boot 4.0.1, Spring JDBC, HikariCP, PostgreSQL Driver, Maven.
- **Database & Auth**: Supabase PostgreSQL (pgvector extension, Full-Text Search, pg_trgm), Supabase Auth (GoTrue).
- **RAG & Search**: Hybrid retrieval combining PostgreSQL Full-Text Search (`to_tsvector`/`websearch_to_tsquery`), Trigram Similarity (`similarity()`), exact medical keyword matching, and medical synonym expansion against 16,400+ clinical evidence chunks (NIH MedQuAD).
- **Deployment**: Vercel (Frontend), Render / Docker (Spring Boot Backend), Supabase Cloud (Database).

---

## Directory Structure

```
medical-chatbot/
├── frontend/                     # Next.js 16 App Router application
│   ├── app/                      # Routes, metadata, and Liquid Glass layout
│   │   ├── layout.tsx            # Root layout with AuthProvider & styles
│   │   ├── page.tsx              # Main interactive MedLearn clinical application
│   │   ├── globals.css           # Design tokens, liquid glass styling
│   │   ├── robots.ts             # SEO robots.txt generator
│   │   └── sitemap.ts            # SEO sitemap.xml generator
│   ├── components/               # Reusable React components (AuthScreen, UserMenu)
│   ├── lib/                      # Supabase client, auth context, middleware helpers
│   ├── public/                   # Static SVG icons and visual assets
│   ├── next.config.ts            # Next.js config with /api/:path* backend proxy
│   ├── package.json              # Frontend dependencies and npm scripts
│   ├── tsconfig.json             # TypeScript compiler settings
│   └── .env.example              # Frontend environment template
├── backend/                      # Java Spring Boot service
│   ├── src/main/java/            # Spring Boot controllers, services, repositories
│   │   └── com/example/demo/
│   │       ├── controller/       # REST APIs: /api/chat, /api/emergency/sos, clinical
│   │       ├── service/          # Hybrid search, RAG answer synthesizer, chunker
│   │       ├── repository/       # pgvector & PostgreSQL knowledge queries
│   │       └── model/            # ChatRequest, EmergencySos, MedicalChunk DTOs
│   ├── src/test/java/            # Unit & Integration tests (Database, RAG pipeline)
│   ├── pom.xml                   # Maven project configuration (Java 21)
│   ├── mvnw / mvnw.cmd           # Maven Wrapper
│   ├── Dockerfile                # Multi-stage production container build
│   └── .env.example              # Backend environment template
├── database/                     # Database architecture documentation
│   └── README.md                 # Migration and schema guide
├── supabase/                     # Supabase database configuration & migrations
│   ├── migrations/               # SQL migrations (pgvector, hybrid search schema)
│   ├── schema_auth_profiles.sql  # User profile table and RLS policies
│   └── config.toml               # Supabase CLI local configuration
├── docs/                         # Production documentation
│   └── DEPLOYMENT.md             # Complete step-by-step production deployment guide
├── render.yaml                   # Render Blueprint configuration for backend
├── .gitignore                    # Git ignore rules for node_modules, target, envs
└── README.md                     # Project overview and reference (this file)
```

---

## Prerequisites

1. **Node.js**: v20 or higher.
2. **Java JDK**: Version 21 (Eclipse Temurin, OpenJDK, or Oracle).
3. **Maven**: Built-in Maven wrapper (`mvnw.cmd` on Windows, `./mvnw` on Linux/macOS).
4. **Supabase Account**: A free Supabase project with PostgreSQL.

---

## Local Development Setup

### 1. Configure Environment Variables

**Frontend (`frontend/.env.local`)**:
```env
NEXT_PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-supabase-anon-key
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-supabase-anon-key
NEXT_PUBLIC_APP_URL=http://localhost:3000
NEXT_PUBLIC_API_URL=http://localhost:8080
```

**Backend (`backend/.env`)**:
```env
DB_URL=jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres?sslmode=require
DB_USERNAME=postgres
DB_PASSWORD=your-database-password
FRONTEND_ORIGIN=http://localhost:3000
PORT=8080
```

### 2. Start the Java Spring Boot Backend
In a terminal, run:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
*(On Linux/macOS: `./mvnw spring-boot:run`)*.  
The backend will start on port `8080`. Verify health at `http://localhost:8080/api/chat`.

### 3. Start the Next.js Frontend
In a second terminal, run:
```powershell
cd frontend
npm run dev
```
Open **http://localhost:3000** in your browser.

---

## Database & RAG Knowledge Base Setup

1. **Run Database Migrations**:
   In your Supabase Dashboard SQL Editor, run:
   - `supabase/schema_auth_profiles.sql` (Creates `profiles` table, handle updates, and RLS).
   - `supabase/migrations/20261012_rag_hybrid_pipeline.sql` (Enables `vector` and `pg_trgm`, creates `medical_knowledge` and `medical_chunks` tables, and creates the hybrid search stored procedure).

2. **RAG Search Pipeline**:
   The backend's `HybridSearchService` combines:
   - **Vector similarity** (cosine distance via pgvector).
   - **Full-Text Search** (`tsvector` and `websearch_to_tsquery` ranking).
   - **Trigram Similarity** (typo tolerance for medical terms like "hypertenshion").
   - **Reciprocal Rank Fusion (RRF)** to produce grounded clinical citations.

---

## Production Deployment

### Frontend Deployment (Vercel)
1. Import repository on [Vercel](https://vercel.com).
2. Set **Root Directory** to `frontend` *(⚠️ Crucial)*.
3. Configure environment variables (`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, `NEXT_PUBLIC_API_URL`, `BACKEND_API_URL`).
4. Click **Deploy**.

### Backend Deployment (Render / Docker)
1. Create a Web Service on [Render](https://render.com).
2. Set Runtime to **Docker**, Root Directory to `backend`.
3. Provide environment variables: `PORT=8080`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FRONTEND_ORIGIN`.
4. Point Vercel's `NEXT_PUBLIC_API_URL` to your Render public URL.

For exact step-by-step instructions, see **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

---

## Troubleshooting

- **CORS Errors**: Ensure `FRONTEND_ORIGIN` on the backend matches your Vercel domain or `http://localhost:3000`. `WebConfig.java` allows `*.vercel.app` by default.
- **Supabase Auth Redirect Error**: Add your Vercel URL to Supabase Dashboard → **Authentication** → **URL Configuration** → **Redirect URLs**.
- **Backend Database Timeout**: Check your Supabase database password and verify connection pooling settings in `application.properties`.
