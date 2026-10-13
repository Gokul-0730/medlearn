# MedLearn Production Deployment Guide

This guide contains the exact steps and configuration files to deploy MedLearn to **Vercel** (Frontend) and connect it with **Supabase** (Database & Auth) and **Spring Boot** (Medical Backend).

---

## 1. System Architecture

```
[Users on Mobile / Tablet / Desktop]
                  │
                  ▼ (HTTPS)
      ┌───────────────────────┐
      │  Vercel Frontend      │  (Next.js App Router)
      │  medlearn.vercel.app  │
      └───────────┬───────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌───────────────┐   ┌───────────────────────┐
│ Supabase      │   │ Spring Boot Backend   │
│ - Auth        │   │ - Clinical RAG & AI   │
│ - PostgreSQL  │   │ - Docker / Render     │
└───────────────┘   └───────────────────────┘
```

---

## 2. Deploying Frontend to Vercel

### Step 1: Open Vercel
1. Log in to [vercel.com](https://vercel.com).
2. Click **Add New...** → **Project**.
3. Import your GitHub repository: `Gokul-0730/medlearn` (or your new repo).

### Step 2: Configure Project Settings
- **Framework Preset**: `Next.js`
- **Root Directory**: Click **Edit** and select `frontend` (⚠️ Critical: MedLearn frontend code is in the `frontend` subfolder).
- **Build Command**: `npm run build` (Default)
- **Output Directory**: `.next` (Default)
- **Install Command**: `npm install` (Default)

### Step 3: Add Environment Variables in Vercel
In the **Environment Variables** section, add the following variables for **Production**, **Preview**, and **Development**:

| Variable Name | Value | Purpose |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_SUPABASE_URL` | `https://iggagkzlvqovfntqbyil.supabase.co` | Supabase API connection |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | `sb_publishable_zu7N73ni_rI11Yi3U9E2fA_w_XwoWJO` | Supabase public client key |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_zu7N73ni_rI11Yi3U9E2fA_w_XwoWJO` | Supabase client alias |
| `NEXT_PUBLIC_APP_URL` | `https://<your-project>.vercel.app` | Public app domain for auth redirects |
| `NEXT_PUBLIC_API_URL` | `https://<your-backend>.onrender.com` *(Optional)* | Backend URL once hosted |
| `BACKEND_API_URL` | `https://<your-backend>.onrender.com` *(Optional)* | Server proxy URL once hosted |

> **Note**: While the Spring Boot backend is being hosted, the frontend can be deployed immediately with the Supabase variables. All client features (Auth, Dashboard, UI, Navigation, Responsive View) work instantly.

### Step 4: Click Deploy
Click **Deploy**. Vercel will build the Next.js application in ~40 seconds and assign your live HTTPS domain:
`https://<your-project-name>.vercel.app`

---

## 3. Supabase Authentication Setup

To ensure login, signup, and email confirmation links work correctly on mobile and desktop:

1. Open your [Supabase Dashboard](https://supabase.com/dashboard/project/iggagkzlvqovfntqbyil).
2. Go to **Authentication** → **URL Configuration**.
3. Update **Site URL**:
   ```
   https://<your-project-name>.vercel.app
   ```
4. Add to **Redirect URLs**:
   ```
   https://<your-project-name>.vercel.app/**
   https://*.vercel.app/**
   http://localhost:3000/**
   ```
5. Click **Save Changes**.

---

## 4. Deploying Java Spring Boot Backend (Render / Docker)

The backend is packaged with a production-grade multi-stage Dockerfile (`backend/Dockerfile`) and a Render Blueprint (`render.yaml`).

### Deploy via Render (Free Tier):
1. Log in to [render.com](https://render.com).
2. Click **New +** → **Web Service**.
3. Connect your GitHub repository.
4. Set:
   - **Root Directory**: `backend`
   - **Runtime**: `Docker`
   - **Instance Type**: `Free`
5. Add Environment Variables:
   - `PORT`: `8080`
   - `FRONTEND_ORIGIN`: `https://<your-project-name>.vercel.app`
   - `DB_URL`: `jdbc:postgresql://db.iggagkzlvqovfntqbyil.supabase.co:5432/postgres?sslmode=require`
   - `DB_USERNAME`: `postgres`
   - `DB_PASSWORD`: `<your-supabase-db-password>`
6. Click **Create Web Service**.
7. Once deployed, copy your backend URL (e.g. `https://medlearn-backend.onrender.com`) and paste it into Vercel's `NEXT_PUBLIC_API_URL` and `BACKEND_API_URL` environment variables.

---

## 5. Post-Deployment Verification Checklist

- [ ] Open the Vercel HTTPS URL on a desktop browser.
- [ ] Open the Vercel HTTPS URL on an iPhone / Android smartphone.
- [ ] Verify iOS Liquid Glass sidebar, background, and navigation render correctly.
- [ ] Test User Registration and Email confirmation.
- [ ] Test User Login & Logout with Supabase.
- [ ] Test MCQs, Clinical Cases, and Viva practice modules.
- [ ] Test Emergency SOS button & location detection.
