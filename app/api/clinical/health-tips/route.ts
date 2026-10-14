import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export async function OPTIONS() {
  return new NextResponse(null, { status: 204, headers: CORS_HEADERS });
}

export async function GET() {
  const backendUrl = process.env.BACKEND_API_URL || "http://127.0.0.1:8080";
  try {
    const res = await fetch(`${backendUrl}/api/clinical/health-tips`, {
      method: "GET",
      signal: AbortSignal.timeout(2000),
    });
    if (res.ok) {
      const data = await res.json();
      return NextResponse.json(data, { headers: CORS_HEADERS });
    }
  } catch {}

  const tips = [
    {
      category: "Cardiovascular Health",
      tip: "Aim for at least 150 minutes of moderate-intensity aerobic physical activity per week to preserve endothelial function.",
      icon: "❤️",
      level: "ESSENTIAL",
    },
    {
      category: "Hydration & Renal Function",
      tip: "Maintain approximately 30-35 mL of water per kg body weight daily to sustain effective glomerular filtration.",
      icon: "💧",
      level: "DAILY",
    },
    {
      category: "Sleep Architecture",
      tip: "Ensure 7 to 9 hours of uninterrupted sleep for adequate nocturnal blood pressure dipping and neurocognitive restoration.",
      icon: "🌙",
      level: "RECOVERY",
    },
    {
      category: "Immunonutrition",
      tip: "Incorporate polyphenol-rich fruits, leafy greens, and omega-3 fatty acids to downregulate systemic inflammatory cytokines.",
      icon: "🥗",
      level: "PREVENTIVE",
    },
  ];

  return NextResponse.json(tips, { headers: CORS_HEADERS });
}
