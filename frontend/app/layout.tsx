import type { Metadata, Viewport } from "next";
import "./globals.css";
import { AuthProvider } from "../lib/auth-context";
import { SpeedInsights } from "@vercel/speed-insights/next";

const siteUrl = process.env.NEXT_PUBLIC_APP_URL || "https://medlearn.com";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "MedLearn - Medical Clinical Learning & AI Hub",
    template: "%s | MedLearn",
  },
  description:
    "AI-powered clinical learning, medical chatbot, evidence-based triage, clinical MCQs, viva questions, case studies, and emergency SOS for medical students and healthcare professionals.",
  keywords: [
    "MedLearn",
    "medical learning platform",
    "medical student learning",
    "medical MCQ",
    "medical viva questions",
    "medical case studies",
    "medical symptoms learning",
    "medical chatbot for students",
    "clinical triage",
    "evidence based medicine",
  ],
  authors: [{ name: "MedLearn Healthcare Team" }],
  creator: "MedLearn",
  publisher: "MedLearn",
  alternates: {
    canonical: "/",
  },
  openGraph: {
    title: "MedLearn - Medical Clinical Learning & AI Hub",
    description:
      "Comprehensive medical learning platform: interactive clinical cases, medical chatbot, viva prep, symptoms triage, and emergency SOS.",
    url: siteUrl,
    siteName: "MedLearn",
    locale: "en_US",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "MedLearn - Medical Clinical Learning & AI Hub",
    description:
      "AI-powered clinical learning, medical chatbot, and board exam prep for healthcare learners.",
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-video-preview": -1,
      "max-image-preview": "large",
      "max-snippet": -1,
    },
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "MedLearn",
  },
  formatDetection: {
    telephone: false,
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 5,
  userScalable: true,
  viewportFit: "cover",
  themeColor: "#059669",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebApplication",
    name: "MedLearn",
    url: siteUrl,
    description:
      "AI-powered clinical learning, medical chatbot, evidence-based triage, clinical MCQs, viva questions, case studies, and emergency SOS.",
    applicationCategory: "EducationalApplication",
    operatingSystem: "All",
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  };

  return (
    <html lang="en">
      <head>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      </head>
      <body>
        <AuthProvider>{children}</AuthProvider>
        <SpeedInsights />
      </body>
    </html>
  );
}