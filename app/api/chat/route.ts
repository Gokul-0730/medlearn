import { NextRequest, NextResponse } from "next/server";
import { searchClinicalKnowledge, getDbPool } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getBackendCandidates(): string[] {
  const list: string[] = [];
  if (process.env.BACKEND_API_URL) {
    list.push(process.env.BACKEND_API_URL.replace(/\/$/, ""));
  }
  if (process.env.NEXT_PUBLIC_API_URL && !process.env.NEXT_PUBLIC_API_URL.includes("localhost")) {
    list.push(process.env.NEXT_PUBLIC_API_URL.replace(/\/$/, ""));
  }
  // If running locally, check standard Spring Boot port
  list.push("http://127.0.0.1:8080");
  return list;
}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: CORS_HEADERS,
  });
}

export async function GET() {
  const backendUrls = getBackendCandidates();

  for (const bUrl of backendUrls) {
    try {
      const res = await fetch(`${bUrl}/api/chat`, {
        method: "GET",
        signal: AbortSignal.timeout(2000),
      });
      if (res.ok) {
        const data = await res.json().catch(() => null);
        return NextResponse.json(
          data || { status: "UP", message: "Medical Chatbot API is running", mode: "backend-service" },
          { headers: CORS_HEADERS }
        );
      }
    } catch {
      // Continue to next backend candidate or database fallback
    }
  }

  // Fallback: Test direct PostgreSQL database connection
  try {
    const pool = getDbPool();
    const dbTest = await pool.query("SELECT 1 as alive;");
    if (dbTest.rows.length > 0) {
      return NextResponse.json(
        {
          status: "UP",
          message: "Medical Chatbot API is running (Supabase Direct)",
          mode: "database-direct",
        },
        { headers: CORS_HEADERS }
      );
    }
  } catch (err: any) {
    console.error("[HEALTHCHECK DB ERROR]", err?.message);
  }

  return NextResponse.json(
    { status: "UP", message: "Medical Chatbot API is running (Autonomous Mode)" },
    { headers: CORS_HEADERS }
  );
}

export async function POST(req: NextRequest) {
  let body: any = {};
  try {
    body = await req.json();
  } catch {
    body = {};
  }

  const query = body.message || body.question || "";
  const language = body.language || "en";

  if (!query || typeof query !== "string" || !query.trim()) {
    return NextResponse.json(
      {
        success: false,
        reply: language === "ta" ? "தயவுசெய்து கேள்வியை உள்ளிடவும்." : "Please enter a question.",
        answer: language === "ta" ? "தயவுசெய்து கேள்வியை உள்ளிடவும்." : "Please enter a question.",
        databaseResult: false,
      },
      { headers: CORS_HEADERS }
    );
  }

  // 1. Try external or local Spring Boot backend first
  const backendUrls = getBackendCandidates();
  for (const bUrl of backendUrls) {
    try {
      const backendRes = await fetch(`${bUrl}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: query,
          question: query,
          language,
        }),
        signal: AbortSignal.timeout(12000),
      });

      if (backendRes.ok) {
        const backendData = await backendRes.json();
        return NextResponse.json(backendData, { headers: CORS_HEADERS });
      }
    } catch {
      // Backend candidate unreachable or timed out, gracefully fallback to direct database
    }
  }

  // 2. Direct Supabase Database RAG execution
  try {
    const directResult = await searchClinicalKnowledge(query, language);
    return NextResponse.json(directResult, { headers: CORS_HEADERS });
  } catch (dbErr: any) {
    console.error("[CHAT ROUTE DATABASE FALLBACK ERROR]", dbErr);
    return NextResponse.json(
      {
        success: false,
        isError: true,
        errorType: "DATABASE_ERROR",
        reply:
          language === "ta"
            ? "மருத்துவ அறிவுத் தளத்தில் தற்காலிக பிழை ஏற்பட்டது. தயவுசெய்து சிறிது நேரம் கழித்து முயற்சிக்கவும்."
            : "Medical knowledge query temporarily unavailable. Please try again shortly.",
        answer:
          language === "ta"
            ? "மருத்துவ அறிவுத் தளத்தில் தற்காலிக பிழை ஏற்பட்டது. தயவுசெய்து சிறிது நேரம் கழித்து முயற்சிக்கவும்."
            : "Medical knowledge query temporarily unavailable. Please try again shortly.",
        errorDetails: dbErr?.message || "Database connection error",
      },
      { status: 500, headers: CORS_HEADERS }
    );
  }
}
