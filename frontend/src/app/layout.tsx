import type { Metadata } from "next";
import { Atkinson_Hyperlegible_Mono, Atkinson_Hyperlegible_Next, Young_Serif } from "next/font/google";
import type { ReactNode } from "react";

import "./globals.css";

// Self-hosted at build time by next/font, so the static export makes no requests to Google.
const display = Young_Serif({ subsets: ["latin"], weight: "400", variable: "--font-young-serif", display: "swap" });
const body = Atkinson_Hyperlegible_Next({ subsets: ["latin"], variable: "--font-atkinson", display: "swap" });
const mono = Atkinson_Hyperlegible_Mono({ subsets: ["latin"], variable: "--font-atkinson-mono", display: "swap" });

export const metadata: Metadata = {
  title: "Hotel Deal Agent",
  description: "Describe your stay once; an agent phones Ontario hotels and emails you the cheapest confirmed-available price.",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en-CA" className={`${display.variable} ${body.variable} ${mono.variable}`}>
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
