import { PublicNav } from "@/components/PublicNav";

const GITHUB_URL = "https://github.com/Memilson/Keeply";

const STEPS = [
  {
    step: "01",
    title: "Clone o repositório",
    desc: "Baixe o código-fonte para sua máquina.",
    code: `git clone https://github.com/Memilson/Keeply.git\ncd Keeply`,
    lang: "bash",
  },
  {
    step: "02",
    title: "Copie o arquivo de ambiente",
    desc: "Configure as variáveis antes de subir a stack.",
    code: `cp .env.example .env\n# edite o .env conforme necessário`,
    lang: "bash",
  },
  {
    step: "03",
    title: "Suba a stack com Docker Compose",
    desc: "Backend, frontend e MinIO sobem juntos.",
    code: `docker compose up -d`,
    lang: "bash",
  },
  {
    step: "04",
    title: "Acesse a interface web",
    desc: "Abra no navegador e faça o primeiro login.",
    code: `http://localhost:3000`,
    lang: "url",
  },
];

const REQUIREMENTS = [
  { label: "Docker", detail: "v24+" },
  { label: "Docker Compose", detail: "v2+" },
  { label: "Sistema operacional", detail: "Linux Ubuntu (recomendado)" },
  { label: "Memória mínima", detail: "2 GB RAM" },
];

const PLANNED = [
  "Pacotes .deb e .rpm para instalação direta",
  "Binários pré-compilados para Windows",
  "Imagem Docker do agente no Docker Hub",
  "Script de instalação one-liner",
];

export default function DownloadPage() {
  return (
    <main className="min-h-screen bg-[#0D0C1A]">
      <PublicNav active="/download" />

      {/* page header */}
      <div className="border-b border-white/10 px-6 py-8 lg:px-8">
        <div className="mx-auto max-w-7xl">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Download</p>
              <h1 className="mt-1.5 text-xl font-black text-white">Run locally.</h1>
              <p className="mt-1.5 text-sm text-slate-500">
                Keeply roda com Docker Compose. Clone, configure e suba em minutos.
              </p>
            </div>
            <div className="flex gap-2">
              <a
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg bg-[#7B61FF] px-4 text-xs font-bold text-white transition hover:bg-[#6046F0]"
              >
                <GitHubIcon className="h-3.5 w-3.5" />
                GitHub
              </a>
              <a
                href={`${GITHUB_URL}/releases`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg border border-white/15 bg-white/5 px-4 text-xs font-semibold text-slate-300 transition hover:bg-white/10 hover:text-white"
              >
                Releases
              </a>
            </div>
          </div>
        </div>
      </div>

      {/* content */}
      <div className="mx-auto max-w-7xl px-6 py-8 lg:px-8">
        <div className="grid gap-8 lg:grid-cols-[1fr_280px]">

          {/* main — install steps */}
          <div>
            <p className="mb-5 text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Quickstart</p>

            <div className="space-y-3">
              {STEPS.map((s) => (
                <div key={s.step} className="rounded-xl border border-white/10 bg-[#100F1E]">
                  <div className="flex items-center gap-3 border-b border-white/10 px-5 py-3.5">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#7B61FF]/20 font-mono text-[10px] font-bold text-[#A78BFA]">
                      {s.step}
                    </span>
                    <div>
                      <p className="text-sm font-bold text-white">{s.title}</p>
                      <p className="text-xs text-slate-500">{s.desc}</p>
                    </div>
                  </div>
                  <div className="p-4">
                    {s.lang === "url" ? (
                      <div className="flex items-center gap-2 rounded-lg border border-white/10 bg-black/30 px-4 py-3">
                        <svg className="h-3.5 w-3.5 shrink-0 text-[#7B61FF]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                          <circle cx="12" cy="12" r="10" />
                          <line x1="2" y1="12" x2="22" y2="12" />
                          <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
                        </svg>
                        <a
                          href={s.code}
                          className="cursor-pointer font-mono text-xs text-[#A78BFA] underline decoration-[#7B61FF]/30 transition hover:text-white"
                        >
                          {s.code}
                        </a>
                      </div>
                    ) : (
                      <pre className="overflow-x-auto rounded-lg border border-white/10 bg-black/30 px-4 py-3 font-mono text-xs leading-6 text-slate-300">
                        <span className="mr-2 select-none text-slate-600">$</span>{s.code.replace(/\n/g, "\n$ ")}
                      </pre>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* sidebar */}
          <div className="space-y-4">
            {/* requirements */}
            <div className="rounded-xl border border-white/10 bg-[#100F1E] p-5">
              <p className="mb-4 text-[10px] font-bold uppercase tracking-widest text-slate-500">Requisitos</p>
              <ul className="space-y-3">
                {REQUIREMENTS.map((r) => (
                  <li key={r.label} className="flex items-center justify-between gap-2">
                    <span className="text-xs text-slate-400">{r.label}</span>
                    <span className="rounded bg-white/5 px-2 py-0.5 font-mono text-[10px] text-slate-300">{r.detail}</span>
                  </li>
                ))}
              </ul>
            </div>

            {/* planned */}
            <div className="rounded-xl border border-white/10 bg-[#100F1E] p-5">
              <p className="mb-4 text-[10px] font-bold uppercase tracking-widest text-slate-500">Em breve</p>
              <ul className="space-y-2">
                {PLANNED.map((item) => (
                  <li key={item} className="flex items-start gap-2 text-xs text-slate-500">
                    <span className="mt-[5px] h-1 w-1 shrink-0 rounded-full bg-[#7B61FF]/60" aria-hidden />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            {/* contact */}
            <div className="rounded-xl border border-white/10 bg-[#100F1E] p-5">
              <p className="mb-2 text-xs font-bold text-white">Acesso técnico</p>
              <p className="text-xs leading-relaxed text-slate-500">
                Dúvidas sobre instalação ou distribuição personalizada:
              </p>
              <a
                href="mailto:angelolealpl14@gmail.com"
                className="mt-3 block cursor-pointer text-xs text-[#A78BFA] transition hover:text-white"
              >
                angelolealpl14@gmail.com
              </a>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
    </svg>
  );
}
