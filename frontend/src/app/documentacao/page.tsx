import Link from "next/link";
import type { ReactNode } from "react";
import { KeeplyLogo } from "@/components/KeeplyLogo";
import { LiveDemoBanner } from "@/components/LiveDemoBanner";

const HEADER_LINKS = [
  { label: "Documentação", href: "/documentacao" },
  { label: "Roadmap", href: "/roadmap" },
  { label: "Download", href: "/download" },
];

const DOC_LINKS = [
  { label: "Arquitetura do Agente", href: "https://github.com/Memilson/Keeply/blob/main/docs/agent.md" },
  { label: "Arquitetura do Backend", href: "https://github.com/Memilson/Keeply/blob/main/docs/backend.md" },
  { label: "Arquitetura do Banco", href: "https://github.com/Memilson/Keeply/blob/main/docs/database.md" },
  { label: "Arquitetura MinIO", href: "https://github.com/Memilson/Keeply/blob/main/docs/minio.md" },
];

const README_URL = "https://raw.githubusercontent.com/Memilson/Keeply/main/README.md";
const GITHUB_BLOB_BASE = "https://github.com/Memilson/Keeply/blob/main/";

async function getReadme() {
  try {
    const response = await fetch(README_URL, {
      next: { revalidate: 300 },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    return await response.text();
  } catch {
    return "Nao foi possivel carregar o README do GitHub no momento.";
  }
}

export default async function DocumentacaoPage() {
  const readme = await getReadme();

  return (
    <main className="min-h-screen bg-[#fbfaff] text-[#090b24]">
      <header className="border-b border-[#e8e6f4] bg-white/92 backdrop-blur-xl">
        <div className="mx-auto flex h-[76px] max-w-[1540px] items-center justify-between px-8 lg:px-12">
          <Link href="/" aria-label="Keeply home">
            <KeeplyLogo size={34} />
          </Link>

          <nav className="hidden items-center gap-8 lg:flex">
            {HEADER_LINKS.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className={`text-[14px] font-semibold transition hover:text-[#5d48ff] ${
                  link.href === "/documentacao" ? "text-[#5d48ff]" : "text-[#17183d]"
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-4">
            <Link href="/login" className="hidden text-[14px] font-semibold text-[#17183d] transition hover:text-[#5d48ff] sm:inline">
              Entrar
            </Link>
            <Link
              href="/register"
              className="rounded-[8px] bg-[#634cff] px-6 py-3 text-[14px] font-bold text-white shadow-[0_10px_24px_rgba(99,76,255,0.28)] transition hover:bg-[#543de8]"
            >
              Começar grátis
            </Link>
          </div>
        </div>
      </header>

      <LiveDemoBanner />

      <section className="px-8 py-16">
        <div className="mx-auto max-w-[1100px]">
          <div className="mb-8 flex flex-wrap gap-3">
            <a
              href="https://github.com/Memilson/Keeply.git"
              target="_blank"
              rel="noreferrer"
              className="rounded-full border border-[#ddd5f5] bg-white px-4 py-2 text-[14px] font-semibold text-[#5d48ff]"
            >
              Repositorio GitHub
            </a>
            {DOC_LINKS.map((link) => (
              <a
                key={link.label}
                href={link.href}
                target="_blank"
                rel="noreferrer"
                className="rounded-full border border-[#ddd5f5] bg-[#faf9ff] px-4 py-2 text-[14px] font-semibold text-[#5d48ff]"
              >
                {link.label}
              </a>
            ))}
          </div>

          <article className="rounded-[18px] border border-[#e5def7] bg-white p-6 shadow-[0_10px_30px_rgba(86,62,180,0.05)]">
            <div className="space-y-5 text-[15px] leading-7 text-[#17183d]">{renderMarkdown(readme)}</div>
          </article>
        </div>
      </section>
    </main>
  );
}

function renderMarkdown(markdown: string): ReactNode[] {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const nodes: ReactNode[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (!line.trim()) {
      i += 1;
      continue;
    }

    if (line.startsWith("```")) {
      const language = line.slice(3).trim();
      const buffer: string[] = [];
      i += 1;
      while (i < lines.length && !lines[i].startsWith("```")) {
        buffer.push(lines[i]);
        i += 1;
      }
      i += 1;
      nodes.push(
        <pre key={`code-${i}`} className="overflow-x-auto rounded-[14px] bg-[#f7f5ff] p-5 text-[14px] leading-7 text-[#17183d]">
          {language ? `${language}\n` : ""}
          {buffer.join("\n")}
        </pre>
      );
      continue;
    }

    if (/^#{1,3}\s+/.test(line)) {
      const level = line.match(/^#+/)?.[0].length ?? 1;
      const text = line.replace(/^#{1,3}\s+/, "");
      const content = renderInline(text, `h-${i}`);
      if (level === 1) {
        nodes.push(<h1 key={`h1-${i}`} className="text-[32px] font-black leading-tight text-[#090b24]">{content}</h1>);
      } else if (level === 2) {
        nodes.push(<h2 key={`h2-${i}`} className="text-[26px] font-black leading-tight text-[#090b24]">{content}</h2>);
      } else {
        nodes.push(<h3 key={`h3-${i}`} className="text-[20px] font-black leading-tight text-[#090b24]">{content}</h3>);
      }
      i += 1;
      continue;
    }

    if (/^\|/.test(line)) {
      const tableLines: string[] = [];
      while (i < lines.length && /^\|/.test(lines[i])) {
        tableLines.push(lines[i]);
        i += 1;
      }
      nodes.push(renderTable(tableLines, `table-${i}`));
      continue;
    }

    if (/^\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\d+\.\s+/, ""));
        i += 1;
      }
      nodes.push(
        <ol key={`ol-${i}`} className="list-decimal space-y-2 pl-6 text-[#62678f]">
          {items.map((item, index) => (
            <li key={`oli-${i}-${index}`}>{renderInline(item, `oli-${i}-${index}`)}</li>
          ))}
        </ol>
      );
      continue;
    }

    if (/^- /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^- /.test(lines[i])) {
        items.push(lines[i].replace(/^- /, ""));
        i += 1;
      }
      nodes.push(
        <ul key={`ul-${i}`} className="list-disc space-y-2 pl-6 text-[#62678f]">
          {items.map((item, index) => (
            <li key={`uli-${i}-${index}`}>{renderInline(item, `uli-${i}-${index}`)}</li>
          ))}
        </ul>
      );
      continue;
    }

    const paragraph: string[] = [];
    while (i < lines.length && lines[i].trim() && !startsStructuredBlock(lines[i])) {
      paragraph.push(lines[i]);
      i += 1;
    }
    nodes.push(
      <p key={`p-${i}`} className="text-[#62678f]">
        {renderInline(paragraph.join(" "), `p-${i}`)}
      </p>
    );
  }

  return nodes;
}

function startsStructuredBlock(line: string) {
  return (
    /^#{1,3}\s+/.test(line) ||
    /^```/.test(line) ||
    /^\|/.test(line) ||
    /^\d+\.\s+/.test(line) ||
    /^- /.test(line)
  );
}

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\[[^\]]+\]\([^)]+\)|`[^`]+`|\*\*[^*]+\*\*)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let index = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }

    const token = match[0];
    if (token.startsWith("`")) {
      nodes.push(
        <code key={`${keyPrefix}-code-${index}`} className="rounded bg-[#f3efff] px-1.5 py-0.5 text-[0.95em] text-[#4f39ea]">
          {token.slice(1, -1)}
        </code>
      );
    } else if (token.startsWith("**")) {
      nodes.push(
        <strong key={`${keyPrefix}-strong-${index}`} className="font-bold text-[#17183d]">
          {token.slice(2, -2)}
        </strong>
      );
    } else if (token.startsWith("[")) {
      const parts = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (parts) {
        const href = parts[2].startsWith("http") ? parts[2] : `${GITHUB_BLOB_BASE}${parts[2]}`;
        nodes.push(
          <a
            key={`${keyPrefix}-link-${index}`}
            href={href}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-[#5d48ff] hover:text-[#4f39ea]"
          >
            {parts[1]}
          </a>
        );
      }
    }

    lastIndex = pattern.lastIndex;
    index += 1;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }

  return nodes;
}

function renderTable(lines: string[], key: string) {
  const rows = lines.map((line) =>
    line
      .split("|")
      .slice(1, -1)
      .map((cell) => cell.trim())
  );
  const header = rows[0] ?? [];
  const body = rows.slice(2);

  return (
    <div key={key} className="overflow-x-auto rounded-[14px] border border-[#ece7fb]">
      <table className="min-w-full text-left text-[14px] text-[#17183d]">
        <thead className="bg-[#f7f5ff]">
          <tr>
            {header.map((cell, index) => (
              <th key={`${key}-h-${index}`} className="px-4 py-3 font-bold">
                {renderInline(cell, `${key}-h-${index}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="bg-white">
          {body.map((row, rowIndex) => (
            <tr key={`${key}-r-${rowIndex}`} className="border-t border-[#ece7fb]">
              {row.map((cell, cellIndex) => (
                <td key={`${key}-c-${rowIndex}-${cellIndex}`} className="px-4 py-3">
                  {renderInline(cell, `${key}-c-${rowIndex}-${cellIndex}`)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
