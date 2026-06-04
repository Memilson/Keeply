import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { TabTitleMessages } from "@/components/TabTitleMessages";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Keeply - Backup e restore por agentes",
  description:
    "Plataforma open-source agent-first para backup, deduplicação e restore de infraestrutura.",
  icons: {
    icon: "/icon.svg",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body className={inter.className}>
        <TabTitleMessages />
        {children}
      </body>
    </html>
  );
}
