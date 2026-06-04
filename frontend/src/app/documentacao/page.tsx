import type { ReactNode } from "react";
import { PublicNav } from "@/components/PublicNav";

const GITHUB_URL = "https://github.com/Memilson/Keeply";
const README_URL = "https://raw.githubusercontent.com/Memilson/Keeply/main/README.md";
const GITHUB_BLOB_BASE = "https://github.com/Memilson/Keeply/blob/main/";

const DOC_SECTIONS = [
  {
    label: "Visão geral",
    links: [
      { label: "README", href: `${GITHUB_URL}#readme` },
      { label: "Repositório", href: GITHUB_URL, external: true },
    ],
  },
  {
    label: "Componentes",
    links: [
      { label: "Agente", href: `${GITHUB_URL}/blob/main/docs/agent.md`, external: true },
      { label: "Backend", href: `${GITHUB_URL}/blob/main/docs/backend.md`, external: true },
      { label: "Banco de dados", href: `${GITHUB_URL}/blob/main/docs/database.md`, external: true },
      { label: "MinIO / Storage", href: `${GITHUB_URL}/blob/main/docs/minio.md`, external: true },
    ],
  },
  {
    label: "Operações",
    links: [
      { label: "Deploy na nuvem", href: `${GITHUB_URL}/blob/main/docs/deploy-cloud.md`, external: true },
      { label: "Exemplos cURL", href: `${GITHUB_URL}/blob/main/docs/curl.md`, external: true },
      { label: "Progresso", href: `${GITHUB_URL}/blob/main/docs/progresso.md`, external: true },
    ],
  },
];

async function getReadme() {
  try {
    const res = await fetch(README_URL, { next: { revalidate: 300 } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } catch {
    return "Não foi possível carregar o README do GitHub no momento.";
  }
}

export default async function DocumentacaoPage() {
  const readme = await getReadme();

  return (
    <main className="min-h-screen bg-[#0D0C1A]">
      <PublicNav active="/documentacao" />

      {/* page header */}
      <div className="border-b border-white/10 px-6 py-8 lg:px-8">
        <div className="mx-auto max-w-7xl">
          <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Documentação</p>
          <h1 className="mt-1.5 text-xl font-black text-white">Arquitetura e referência técnica.</h1>
          <p className="mt-1.5 text-sm text-slate-500">
            README do repositório e documentos técnicos de cada componente.
          </p>
        </div>
      </div>

      {/* two-column layout */}
      <div className="mx-auto max-w-7xl px-6 py-8 lg:px-8">
        <div className="flex gap-8 lg:items-start">

          {/* sidebar */}
          <aside className="hidden w-52 shrink-0 lg:block">
            <div className="sticky top-24 space-y-6">
              {DOC_SECTIONS.map((section) => (
                <div key={section.label}>
                  <p className="mb-2 text-[10px] font-bold uppercase tracking-widest text-slate-600">
                    {section.label}
                  </p>
                  <ul className="space-y-0.5">
                    {section.links.map((link) => (
                      <li key={link.label}>
                        <a
                          href={link.href}
                          target={link.external ? "_blank" : undefined}
                          rel={link.external ? "noopener noreferrer" : undefined}
                          className="group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-1.5 text-sm text-slate-400 transition-colors hover:bg-white/5 hover:text-white"
                        >
                          {link.label}
                          {link.external && (
                            <svg className="h-3 w-3 opacity-0 transition-opacity group-hover:opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                              <path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6" />
                              <polyline points="15 3 21 3 21 9" />
                              <line x1="10" y1="14" x2="21" y2="3" />
                            </svg>
                          )}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}

              <div className="border-t border-white/10 pt-5">
                <a
                  href={`${GITHUB_URL}/issues`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex cursor-pointer items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-3 py-2.5 text-xs font-semibold text-slate-400 transition hover:border-[#7B61FF]/30 hover:text-white"
                >
                  <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                  </svg>
                  Abrir issue
                </a>
              </div>
            </div>
          </aside>

          {/* main content */}
          <div className="min-w-0 flex-1">
            {/* mobile doc links */}
            <div className="mb-6 flex flex-wrap gap-2 lg:hidden">
              {DOC_SECTIONS.flatMap((s) => s.links).map((link) => (
                <a
                  key={link.label}
                  href={link.href}
                  target={link.external ? "_blank" : undefined}
                  rel={link.external ? "noopener noreferrer" : undefined}
                  className="inline-flex h-7 cursor-pointer items-center rounded-full border border-white/10 bg-white/5 px-3 text-xs font-medium text-slate-400 transition hover:text-white"
                >
                  {link.label}
                </a>
              ))}
            </div>

            {/* readme card */}
            <div className="rounded-xl border border-white/10 bg-[#100F1E]">
              <div className="flex items-center gap-1.5 border-b border-white/10 px-5 py-3">
                <span className="h-2 w-2 rounded-full bg-[#EF4444]/50" aria-hidden />
                <span className="h-2 w-2 rounded-full bg-[#F59E0B]/50" aria-hidden />
                <span className="h-2 w-2 rounded-full bg-[#10B981]/50" aria-hidden />
                <span className="ml-2 font-mono text-[10px] text-slate-600">README.md</span>
                <span className="ml-auto">
                  <a
                    href={`${GITHUB_URL}/blob/main/README.md`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="cursor-pointer text-[10px] text-slate-600 transition hover:text-[#7B61FF]"
                  >
                    Ver no GitHub ↗
                  </a>
                </span>
              </div>

              <div className="space-y-5 p-6 text-sm leading-[1.8] text-slate-300 sm:p-8">
                {renderMarkdown(readme)}
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

// ─── Markdown renderer ────────────────────────────────────────────────────────

function renderMarkdown(markdown: string): ReactNode[] {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const nodes: ReactNode[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (!line.trim()) { i += 1; continue; }

    if (line.startsWith("```")) {
      const lang = line.slice(3).trim();
      const buffer: string[] = [];
      i += 1;
      while (i < lines.length && !lines[i].startsWith("```")) {
        buffer.push(lines[i]);
        i += 1;
      }
      i += 1;
      nodes.push(
        <pre key={`code-${i}`} className="overflow-x-auto rounded-lg border border-white/10 bg-black/40 px-4 py-3.5 font-mono text-xs leading-6 text-slate-300">
          {lang && <span className="mb-2 block text-[10px] font-semibold uppercase tracking-widest text-slate-600">{lang}</span>}
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
        nodes.push(
          <h1 key={`h1-${i}`} className="border-b border-white/10 pb-2 text-lg font-black text-white">
            {content}
          </h1>
        );
      } else if (level === 2) {
        nodes.push(
          <h2 key={`h2-${i}`} className="text-base font-black text-white">{content}</h2>
        );
      } else {
        nodes.push(
          <h3 key={`h3-${i}`} className="text-sm font-black text-slate-200">{content}</h3>
        );
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
        <ol key={`ol-${i}`} className="list-decimal space-y-1.5 pl-5 text-slate-400">
          {items.map((item, idx) => (
            <li key={`oli-${i}-${idx}`}>{renderInline(item, `oli-${i}-${idx}`)}</li>
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
        <ul key={`ul-${i}`} className="space-y-1.5 pl-1 text-slate-400">
          {items.map((item, idx) => (
            <li key={`uli-${i}-${idx}`} className="flex items-start gap-2.5">
              <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-[#7B61FF]" aria-hidden />
              <span>{renderInline(item, `uli-${i}-${idx}`)}</span>
            </li>
          ))}
        </ul>
      );
      continue;
    }

    const paragraph: string[] = [];
    while (i < lines.length && lines[i].trim() && !startsBlock(lines[i])) {
      paragraph.push(lines[i]);
      i += 1;
    }
    nodes.push(
      <p key={`p-${i}`} className="text-slate-400">
        {renderInline(paragraph.join(" "), `p-${i}`)}
      </p>
    );
  }

  return nodes;
}

function startsBlock(line: string) {
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
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index));
    const token = match[0];

    if (token.startsWith("`")) {
      nodes.push(
        <code key={`${keyPrefix}-c-${index}`} className="rounded border border-white/10 bg-white/5 px-1.5 py-0.5 font-mono text-[0.875em] text-[#A78BFA]">
          {token.slice(1, -1)}
        </code>
      );
    } else if (token.startsWith("**")) {
      nodes.push(
        <strong key={`${keyPrefix}-s-${index}`} className="font-bold text-slate-200">
          {token.slice(2, -2)}
        </strong>
      );
    } else if (token.startsWith("[")) {
      const parts = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (parts) {
        const href = parts[2].startsWith("http") ? parts[2] : `${GITHUB_BLOB_BASE}${parts[2]}`;
        nodes.push(
          <a key={`${keyPrefix}-l-${index}`} href={href} target="_blank" rel="noreferrer"
            className="cursor-pointer font-medium text-[#A78BFA] underline decoration-[#7B61FF]/30 underline-offset-2 transition hover:text-white hover:decoration-[#7B61FF]">
            {parts[1]}
          </a>
        );
      }
    }

    lastIndex = pattern.lastIndex;
    index += 1;
  }

  if (lastIndex < text.length) nodes.push(text.slice(lastIndex));
  return nodes;
}

function renderTable(lines: string[], key: string) {
  const rows = lines.map((line) =>
    line.split("|").slice(1, -1).map((cell) => cell.trim())
  );
  const header = rows[0] ?? [];
  const body = rows.slice(2);

  return (
    <div key={key} className="overflow-x-auto rounded-lg border border-white/10">
      <table className="min-w-full text-left text-xs">
        <thead className="border-b border-white/10 bg-white/5">
          <tr>
            {header.map((cell, idx) => (
              <th key={`${key}-h-${idx}`} className="px-4 py-2.5 font-bold text-slate-200">
                {renderInline(cell, `${key}-h-${idx}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {body.map((row, ri) => (
            <tr key={`${key}-r-${ri}`} className="border-t border-white/5 transition-colors hover:bg-white/3">
              {row.map((cell, ci) => (
                <td key={`${key}-c-${ri}-${ci}`} className="px-4 py-2.5 text-slate-400">
                  {renderInline(cell, `${key}-c-${ri}-${ci}`)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
