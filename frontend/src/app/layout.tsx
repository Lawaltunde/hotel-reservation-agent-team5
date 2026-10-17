import type { Metadata } from "next";
import type { ReactNode } from "react";

import "./globals.css";

export const metadata: Metadata = {
  title: "Hotel Deal Agent",
  description: "Finds and negotiates the cheapest suitable hotel in Ontario",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en-CA">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
