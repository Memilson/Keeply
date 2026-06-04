import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { TabTitleMessages } from "@/components/TabTitleMessages";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  metadataBase: new URL("https://github.com/Memilson/Keeply"),
  title: {
    default: "Keeply - Backup e restore por agentes",
    template: "%s | Keeply",
  },
  description:
    "Keeply é uma plataforma open-source para backup, restore, deduplicação e orquestração de agentes distribuídos para infraestrutura, servidores, homelabs e DevOps.",
  keywords: [
    "Keeply",
    "backup open source",
    "backup self hosted",
    "backup agent-first",
    "backup por agentes",
    "backup distribuído",
    "restore de arquivos",
    "restore de infraestrutura",
    "deduplicação de backup",
    "backup incremental",
    "snapshot backup",
    "object storage backup",
    "S3 backup",
    "MinIO backup",
    "backup para servidores",
    "backup Linux",
    "backup DevOps",
    "backup SRE",
    "backup homelab",
    "backup MSP",
    "orquestração de backup",
    "plataforma de backup",
    "software de backup",
    "backup e restore",
    "backup infrastructure",
    "distributed backup agents",
    "open source backup platform",
    "self-hosted backup platform",
  ],
  authors: [{ name: "Angelo Leal", url: "https://github.com/Memilson/Keeply" }],
  creator: "Angelo Leal",
  publisher: "Keeply",
  category: "technology",
  applicationName: "Keeply",
  alternates: {
    canonical: "/",
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  },
  openGraph: {
    type: "website",
    locale: "pt_BR",
    url: "/",
    siteName: "Keeply",
    title: "Keeply - Backup e restore por agentes",
    description:
      "Plataforma open-source para backup, restore, deduplicação e orquestração de agentes distribuídos.",
    emails: ["angelolealpl14@gmail.com"],
  },
  twitter: {
    card: "summary",
    title: "Keeply - Backup e restore por agentes",
    description:
      "Backup open-source, self-hosted e agent-first para infraestrutura, servidores, homelabs e DevOps.",
  },
  icons: {
    icon: "/icon.svg",
    shortcut: "/icon.svg",
    apple: "/icon.svg",
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
