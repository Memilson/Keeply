"use client";

import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";

const GITHUB_URL = "https://github.com/Memilson/Keeply";
const DOCS_URL = "https://github.com/Memilson/Keeply/wiki";
const DOWNLOAD_URL = "https://github.com/Memilson/Keeply/releases";

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-[#F8F7FD] text-[#18163A]">
      <LandingNav />
      <HeroSection />
      <ProductPreviewSection />
      <ArchitectureSection />
      <AgentFirstSection />
      <FeaturesSection />
      <OpenSourceSection />
      <UseCasesSection />
      <SecuritySection />
      <FinalCTASection />
      <LandingFooter />
    </main>
  );
}

// ─── Nav ──────────────────────────────────────────────────────────────────────

function LandingNav() {
  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-white/10 bg-[#0D0C1A]/90 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6 lg:px-8">
        <Link href="/" aria-label="Keeply home">
          <KeeplyLogo size={28} wordmarkColor="#FFFFFF" />
        </Link>

        <nav className="hidden items-center gap-7 lg:flex">
          <a href={DOCS_URL} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-slate-400 transition-colors hover:text-white">
            Documentação
          </a>
          <a href={GITHUB_URL + "#roadmap"} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-slate-400 transition-colors hover:text-white">
            Roadmap
          </a>
          <a href={DOWNLOAD_URL} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-slate-400 transition-colors hover:text-white">
            Download
          </a>
        </nav>

        <div className="flex items-center gap-3">
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg border border-white/20 bg-white/10 px-4 text-sm font-semibold text-white transition hover:bg-white/20"
          >
            <IconGitHub className="h-4 w-4" />
            GitHub
          </a>
        </div>
      </div>
    </header>
  );
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

function HeroSection() {
  return (
    <section className="relative overflow-hidden bg-[#0D0C1A] pt-16">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.12] [background-image:linear-gradient(rgba(123,97,255,0.4)_1px,transparent_1px),linear-gradient(90deg,rgba(123,97,255,0.4)_1px,transparent_1px)] [background-size:48px_48px]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/4 top-0 h-[500px] w-[700px] -translate-x-1/2 rounded-full bg-[#7B61FF] opacity-[0.07] blur-[100px]"
      />

      <div className="relative mx-auto max-w-7xl px-6 py-28 lg:px-8 lg:py-36">
        <div className="flex flex-col gap-12 lg:flex-row lg:items-center">
          <div className="max-w-[540px] flex-shrink-0">
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-[#7B61FF]/40 bg-[#7B61FF]/10 px-3.5 py-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-[#7B61FF]" aria-hidden />
              <span className="text-[11px] font-semibold uppercase tracking-widest text-[#A78BFA]">
                Open-source · Agent-first
              </span>
            </div>

            <h1 className="text-[40px] font-black leading-[1.08] tracking-tight text-white lg:text-[52px]">
              Backup e restore<br />
              orquestrados por<br />
              <span className="text-[#7B61FF]">agentes distribuídos.</span>
            </h1>

            <p className="mt-6 max-w-[460px] text-base leading-[1.7] text-slate-400">
              Keeply é uma plataforma open-source para backup, deduplicação e restore.
              Agentes rodam localmente, transmitem dados de forma eficiente
              e são orquestrados pela interface web.
            </p>

            <div className="mt-8 flex flex-wrap gap-3">
              <a
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-11 cursor-pointer items-center gap-2.5 rounded-lg bg-[#7B61FF] px-5 text-sm font-bold text-white transition hover:bg-[#6046F0]"
              >
                <IconGitHub className="h-4 w-4" />
                View on GitHub
              </a>
              <a
                href={DOCS_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-11 cursor-pointer items-center gap-2 rounded-lg border border-white/20 bg-white/5 px-5 text-sm font-bold text-white transition hover:bg-white/10"
              >
                Read the Docs
              </a>
              <a
                href={DOWNLOAD_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-11 cursor-pointer items-center gap-2 px-4 text-sm font-bold text-slate-400 transition hover:text-white"
              >
                Run locally <span aria-hidden>→</span>
              </a>
            </div>

            <div className="mt-6 inline-flex items-center gap-3 rounded-lg border border-white/10 bg-white/5 px-4 py-2.5">
              <span className="font-mono text-xs text-slate-600">$</span>
              <code className="font-mono text-xs text-slate-300">docker compose up -d</code>
            </div>
          </div>

          <div className="w-full min-w-0 flex-1">
            <MiniConsole />
          </div>
        </div>
      </div>
    </section>
  );
}

function MiniConsole() {
  return (
    <div className="overflow-hidden rounded-xl border border-white/10 bg-[#100F1E] shadow-[0_32px_80px_rgba(0,0,0,0.5)]">
      <div className="flex items-center gap-1.5 border-b border-white/10 px-4 py-3">
        <span className="h-2.5 w-2.5 rounded-full bg-[#EF4444]/60" aria-hidden />
        <span className="h-2.5 w-2.5 rounded-full bg-[#F59E0B]/60" aria-hidden />
        <span className="h-2.5 w-2.5 rounded-full bg-[#10B981]/60" aria-hidden />
        <span className="ml-3 font-mono text-[11px] text-slate-500">keeply · agent console</span>
      </div>

      <div className="p-4">
        <p className="mb-2.5 text-[10px] font-semibold uppercase tracking-widest text-slate-600">
          Active agents
        </p>
        <div className="mb-4 grid gap-2 sm:grid-cols-3">
          {MOCK_AGENTS.map((agent) => (
            <div key={agent.name} className="rounded-lg border border-white/5 bg-white/5 p-3">
              <div className="flex items-center justify-between">
                <span className="truncate font-mono text-xs font-semibold text-slate-200">
                  {agent.name}
                </span>
                <span
                  className={`ml-1 h-2 w-2 flex-shrink-0 rounded-full ${agent.online ? "bg-[#10B981]" : "bg-slate-700"}`}
                  aria-hidden
                />
              </div>
              <div className="mt-1 text-[10px] text-slate-600">{agent.os} · {agent.version}</div>
              <div className="mt-1.5 text-[11px] font-medium text-slate-400">{agent.status}</div>
            </div>
          ))}
        </div>

        <p className="mb-2 text-[10px] font-semibold uppercase tracking-widest text-slate-600">
          Recent jobs
        </p>
        <div className="flex flex-col gap-1">
          {MOCK_JOBS.map((job) => (
            <div
              key={job.id}
              className="flex items-center justify-between rounded border border-white/5 bg-white/5 px-3 py-2"
            >
              <div className="flex min-w-0 items-center gap-2">
                <span className={`h-1.5 w-1.5 flex-shrink-0 rounded-full ${JOB_DOT[job.status]}`} aria-hidden />
                <span className="truncate font-mono text-xs text-slate-300">{job.path}</span>
              </div>
              <div className="ml-2 flex flex-shrink-0 items-center gap-2">
                <span className="hidden text-[10px] text-slate-600 sm:block">{job.size}</span>
                <span className={`rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide ${JOB_BADGE[job.status]}`}>
                  {job.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── Product Preview ──────────────────────────────────────────────────────────

function ProductPreviewSection() {
  return (
    <section className="bg-[#100F1E] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-10 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Interface</p>
            <h2 className="mt-2 text-3xl font-black leading-tight text-white lg:text-4xl">
              Console de orquestração,<br className="hidden lg:block" />
              não painel de analytics.
            </h2>
          </div>
          <p className="max-w-sm text-sm leading-relaxed text-slate-500">
            Visualize agentes, acompanhe jobs, configure políticas e inicie restores em um único lugar.
          </p>
        </div>
        <DashboardMock />
      </div>
    </section>
  );
}

function DashboardMock() {
  return (
    <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0D0C1A] shadow-[0_40px_100px_rgba(0,0,0,0.5)]">
      <div className="flex items-center justify-between border-b border-white/10 px-5 py-3">
        <div className="flex items-center gap-3">
          <KeeplyLogo size={20} wordmarkColor="#FFFFFF" />
          <span className="text-xs text-slate-500">/ Dashboard</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded border border-[#10B981]/30 bg-[#10B981]/10 px-2 py-0.5 text-[10px] font-semibold text-[#10B981]">
            3 agents online
          </span>
          <span className="rounded border border-white/10 bg-white/5 px-2 py-0.5 text-[10px] text-slate-500">
            14:32
          </span>
        </div>
      </div>

      <div className="grid lg:grid-cols-[200px_1fr]">
        <nav className="hidden border-r border-white/10 p-3 lg:flex lg:flex-col lg:gap-0.5">
          {MOCK_NAV_ITEMS.map(({ label, active, icon }) => (
            <div
              key={label}
              className={`flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-xs font-semibold transition ${
                active ? "bg-[#7B61FF]/20 text-[#A78BFA]" : "text-slate-600 hover:text-slate-400"
              }`}
            >
              <span className="h-3.5 w-3.5 flex-shrink-0">{icon}</span>
              {label}
            </div>
          ))}
        </nav>

        <div className="p-5">
          <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {MOCK_KPIS.map(({ label, value, sub, highlight }) => (
              <div key={label} className="rounded-lg border border-white/10 bg-white/5 p-4">
                <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-600">{label}</div>
                <div className={`mt-1.5 text-xl font-black ${highlight}`}>{value}</div>
                <div className="mt-0.5 text-[10px] text-slate-700">{sub}</div>
              </div>
            ))}
          </div>

          <div className="overflow-hidden rounded-lg border border-white/10 bg-white/5">
            <div className="flex items-center justify-between border-b border-white/10 px-4 py-2.5">
              <span className="text-xs font-semibold text-slate-300">Recent Jobs</span>
            </div>
            <div className="divide-y divide-white/5">
              {MOCK_JOBS_FULL.map((job) => (
                <div key={job.id} className="flex items-center justify-between px-4 py-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className={`h-1.5 w-1.5 flex-shrink-0 rounded-full ${JOB_DOT[job.status]}`} aria-hidden />
                    <div className="min-w-0">
                      <div className="truncate font-mono text-xs font-semibold text-slate-200">{job.path}</div>
                      <div className="text-[10px] text-slate-600">{job.agent} · {job.time}</div>
                    </div>
                  </div>
                  <div className="ml-3 flex flex-shrink-0 items-center gap-2">
                    <span className="hidden text-[10px] text-slate-600 sm:block">{job.size}</span>
                    <span className={`rounded px-2 py-0.5 text-[9px] font-bold uppercase tracking-wide ${JOB_BADGE[job.status]}`}>
                      {job.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Architecture ─────────────────────────────────────────────────────────────

function ArchitectureSection() {
  return (
    <section className="bg-[#F8F7FD] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-12 max-w-xl">
          <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Arquitetura</p>
          <h2 className="mt-3 text-3xl font-black leading-tight text-[#18163A] lg:text-4xl">
            Fluxo técnico, de ponta a ponta.
          </h2>
          <p className="mt-4 text-base leading-[1.7] text-[#6B6993]">
            O agente captura, processa e transmite. O backend armazena, indexa e orquestra.
            O restore é determinístico e verificável por hash.
          </p>
        </div>

        <div className="overflow-x-auto pb-4">
          <div className="flex min-w-[720px] items-stretch gap-0">
            {ARCH_STEPS.map((step, idx) => (
              <div key={step.label} className="flex min-w-0 flex-1 items-stretch">
                <div
                  className={`flex flex-1 flex-col rounded-xl border p-5 ${
                    step.highlight
                      ? "border-[#7B61FF]/30 bg-[#7B61FF]/5"
                      : "border-[#E4E1F0] bg-white"
                  }`}
                >
                  <div className={`mb-3 text-[10px] font-bold uppercase tracking-widest ${step.highlight ? "text-[#7B61FF]" : "text-[#6B6993]"}`}>
                    {String(idx + 1).padStart(2, "0")}
                  </div>
                  <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-[#EDE9FF] text-[#6046F0]">
                    {step.icon}
                  </div>
                  <div className="text-sm font-black text-[#18163A]">{step.label}</div>
                  <div className="mt-1.5 text-xs leading-relaxed text-[#6B6993]">{step.desc}</div>
                </div>
                {idx < ARCH_STEPS.length - 1 && (
                  <div className="flex w-8 flex-shrink-0 items-center justify-center text-[#C4B5FD]">
                    <IconArrowRight className="h-4 w-4" />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ─── Agent-first ──────────────────────────────────────────────────────────────

function AgentFirstSection() {
  return (
    <section className="bg-white px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-16 lg:grid-cols-2 lg:items-center">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Agent-first</p>
            <h2 className="mt-3 text-3xl font-black leading-tight text-[#18163A] lg:text-4xl">
              O agente é o núcleo.<br />Não um plugin.
            </h2>
            <p className="mt-5 max-w-[460px] text-base leading-[1.7] text-[#6B6993]">
              O agente Keeply roda diretamente no host. Ele executa o backup, aplica políticas
              localmente, deduplica dados antes de enviar e garante que o restore
              seja verificável por hash.
            </p>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            {AGENT_FEATURES.map((f) => (
              <div key={f.title} className="rounded-xl border border-[#E4E1F0] bg-[#F8F7FD] p-5">
                <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-[#EDE9FF] text-[#6046F0]">
                  {f.icon}
                </div>
                <h3 className="text-sm font-black text-[#18163A]">{f.title}</h3>
                <p className="mt-1 text-xs leading-relaxed text-[#6B6993]">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ─── Features ─────────────────────────────────────────────────────────────────

function FeaturesSection() {
  return (
    <section id="recursos" className="bg-[#F8F7FD] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-12 max-w-xl">
          <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Plataforma</p>
          <h2 className="mt-3 text-3xl font-black leading-tight text-[#18163A] lg:text-4xl">
            Tudo que uma infraestrutura<br className="hidden lg:block" /> de backup precisa.
          </h2>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <article key={f.title} className="rounded-xl border border-[#E4E1F0] bg-white p-6">
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-[#EDE9FF] text-[#6046F0]">
                {f.icon}
              </div>
              <h3 className="text-sm font-black text-[#18163A]">{f.title}</h3>
              <p className="mt-2 text-xs leading-relaxed text-[#6B6993]">{f.desc}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

// ─── Open Source ──────────────────────────────────────────────────────────────

function OpenSourceSection() {
  return (
    <section className="bg-[#0D0C1A] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-12 lg:grid-cols-2 lg:items-center">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Open-source</p>
            <h2 className="mt-3 text-3xl font-black leading-tight text-white lg:text-4xl">
              Transparente por design.<br />Auditável por padrão.
            </h2>
            <p className="mt-5 max-w-[460px] text-sm leading-[1.7] text-slate-400">
              Keeply é open-source. Código aberto, arquitetura documentada, roadmap público.
              Você sabe exatamente o que está rodando na sua infraestrutura.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg bg-white/10 px-5 text-sm font-bold text-white transition hover:bg-white/20"
              >
                <IconGitHub className="h-4 w-4" />
                Ver no GitHub
              </a>
              <a
                href={GITHUB_URL + "#roadmap"}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-white/20 px-5 text-sm font-bold text-slate-300 transition hover:text-white"
              >
                Roadmap público
              </a>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            {OPEN_SOURCE_POINTS.map((p) => (
              <div key={p.title} className="rounded-xl border border-white/10 bg-white/5 p-5">
                <div className="mb-2 text-sm font-bold text-white">{p.title}</div>
                <div className="text-xs leading-relaxed text-slate-400">{p.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ─── Use Cases ────────────────────────────────────────────────────────────────

function UseCasesSection() {
  return (
    <section className="bg-white px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-12 max-w-xl">
          <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Casos de uso</p>
          <h2 className="mt-3 text-3xl font-black leading-tight text-[#18163A] lg:text-4xl">
            Para quem precisa de controle real.
          </h2>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {USE_CASES.map((uc) => (
            <div key={uc.title} className="rounded-xl border border-[#E4E1F0] bg-[#F8F7FD] p-6">
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-[#EDE9FF] text-[#6046F0]">
                {uc.icon}
              </div>
              <h3 className="text-sm font-black text-[#18163A]">{uc.title}</h3>
              <p className="mt-2 text-xs leading-relaxed text-[#6B6993]">{uc.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

// ─── Security ─────────────────────────────────────────────────────────────────

function SecuritySection() {
  return (
    <section className="bg-[#F8F7FD] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="mb-12 max-w-xl">
          <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">
            Segurança e confiabilidade
          </p>
          <h2 className="mt-3 text-3xl font-black leading-tight text-[#18163A] lg:text-4xl">
            Verificável em cada etapa.
          </h2>
          <p className="mt-4 text-base leading-[1.7] text-[#6B6993]">
            Arquitetura projetada para integridade. Não confie apenas no backup — verifique o restore.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {SECURITY_POINTS.map((p) => (
            <div key={p.title} className="rounded-xl border border-[#E4E1F0] bg-white p-6">
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-[#EDE9FF] text-[#6046F0]">
                {p.icon}
              </div>
              <h3 className="text-sm font-bold text-[#18163A]">{p.title}</h3>
              <p className="mt-1.5 text-xs leading-relaxed text-[#6B6993]">{p.desc}</p>
              {p.roadmap && (
                <span className="mt-3 inline-block rounded-full bg-[#EDE9FF] px-2.5 py-0.5 text-[10px] font-semibold text-[#6046F0]">
                  Roadmap
                </span>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

// ─── Final CTA ────────────────────────────────────────────────────────────────

function FinalCTASection() {
  return (
    <section className="bg-[#0D0C1A] px-6 py-24 lg:px-8">
      <div className="mx-auto max-w-3xl text-center">
        <h2 className="text-3xl font-black leading-tight text-white lg:text-4xl">
          Comece agora. É open-source.
        </h2>
        <p className="mx-auto mt-5 max-w-md text-base leading-[1.7] text-slate-400">
          Clone, implante e configure em minutos. Sem trial, sem cartão, sem lock-in.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-11 cursor-pointer items-center gap-2.5 rounded-lg bg-[#7B61FF] px-6 text-sm font-bold text-white transition hover:bg-[#6046F0]"
          >
            <IconGitHub className="h-4 w-4" />
            View on GitHub
          </a>
          <a
            href={DOCS_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-11 cursor-pointer items-center gap-2 rounded-lg border border-white/20 bg-white/5 px-6 text-sm font-bold text-white transition hover:bg-white/10"
          >
            Read the Docs
          </a>
          <a
            href={DOWNLOAD_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-11 cursor-pointer items-center gap-2 px-5 text-sm font-bold text-slate-400 transition hover:text-white"
          >
            Quickstart <span aria-hidden>→</span>
          </a>
        </div>
      </div>
    </section>
  );
}

// ─── Footer ───────────────────────────────────────────────────────────────────

function LandingFooter() {
  return (
    <footer className="border-t border-[#E4E1F0] bg-[#F8F7FD] px-6 py-14 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col gap-10 lg:flex-row lg:items-start lg:justify-between">
          <div className="max-w-xs">
            <KeeplyLogo size={32} />
            <p className="mt-3 text-sm leading-[1.7] text-[#6B6993]">
              Plataforma open-source agent-first para backup e restore de infraestrutura.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-10 sm:grid-cols-3">
            {FOOTER_COLS.map((col) => (
              <div key={col.title}>
                <h4 className="mb-3 text-[10px] font-black uppercase tracking-widest text-[#18163A]">
                  {col.title}
                </h4>
                <ul className="flex flex-col gap-2">
                  {col.links.map((link) => (
                    <li key={link.label}>
                      <a
                        href={link.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="cursor-pointer text-sm text-[#6B6993] transition hover:text-[#7B61FF]"
                      >
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-3 border-t border-[#E4E1F0] pt-8 lg:flex-row lg:items-center lg:justify-between">
          <p className="text-xs text-[#6B6993]">© 2026 Keeply. Open-source.</p>
          <div className="flex flex-wrap gap-4 text-xs text-[#6B6993]">
            <a href="#" className="transition hover:text-[#7B61FF]">Privacidade</a>
            <a href="#" className="transition hover:text-[#7B61FF]">Termos</a>
            <a href="mailto:angelolealpl14@gmail.com" className="transition hover:text-[#7B61FF]">Contato</a>
          </div>
        </div>
      </div>
    </footer>
  );
}

// ─── Icons ────────────────────────────────────────────────────────────────────

function IconGitHub({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
    </svg>
  );
}

function IconServer({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <rect x="2" y="2" width="20" height="8" rx="2" />
      <rect x="2" y="14" width="20" height="8" rx="2" />
      <circle cx="6" cy="6" r="1" fill="currentColor" stroke="none" />
      <circle cx="6" cy="18" r="1" fill="currentColor" stroke="none" />
    </svg>
  );
}

function IconLayers({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polygon points="12 2 2 7 12 12 22 7 12 2" />
      <polyline points="2 17 12 22 22 17" />
      <polyline points="2 12 12 17 22 12" />
    </svg>
  );
}

function IconLink({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71" />
    </svg>
  );
}

function IconDatabase({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
      <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
    </svg>
  );
}

function IconFileText({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
  );
}

function IconRestore({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M3 12a9 9 0 1 0 3-6.7" />
      <path d="M3 4v6h6" />
    </svg>
  );
}

function IconShield({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

function IconAlert({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}

function IconActivity({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
    </svg>
  );
}

function IconGrid({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <rect x="3" y="3" width="7" height="7" />
      <rect x="14" y="3" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" />
      <rect x="3" y="14" width="7" height="7" />
    </svg>
  );
}

function IconZap({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
    </svg>
  );
}

function IconCheck({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

function IconArchive({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polyline points="21 8 21 21 3 21 3 8" />
      <rect x="1" y="3" width="22" height="5" />
      <line x1="10" y1="12" x2="14" y2="12" />
    </svg>
  );
}

function IconHash({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <line x1="4" y1="9" x2="20" y2="9" />
      <line x1="4" y1="15" x2="20" y2="15" />
      <line x1="10" y1="3" x2="8" y2="21" />
      <line x1="16" y1="3" x2="14" y2="21" />
    </svg>
  );
}

function IconKey({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <circle cx="7.5" cy="15.5" r="5.5" />
      <path d="M21 2l-9.6 9.6" />
      <path d="M15.5 7.5l3 3L22 7l-3-3" />
    </svg>
  );
}

function IconLock({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <rect x="3" y="11" width="18" height="11" rx="2" />
      <path d="M7 11V7a5 5 0 0110 0v4" />
    </svg>
  );
}

function IconClipboard({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2" />
      <rect x="8" y="2" width="8" height="4" rx="1" />
    </svg>
  );
}

function IconArrowRight({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <line x1="5" y1="12" x2="19" y2="12" />
      <polyline points="12 5 19 12 12 19" />
    </svg>
  );
}

function IconHome({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
      <polyline points="9 22 9 12 15 12 15 22" />
    </svg>
  );
}

function IconUsers({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 00-3-3.87" />
      <path d="M16 3.13a4 4 0 010 7.75" />
    </svg>
  );
}

function IconCode({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <polyline points="16 18 22 12 16 6" />
      <polyline points="8 6 2 12 8 18" />
    </svg>
  );
}

// ─── Data ─────────────────────────────────────────────────────────────────────

const JOB_DOT: Record<string, string> = {
  done: "bg-[#10B981]",
  running: "bg-[#7B61FF]",
  failed: "bg-[#EF4444]",
};

const JOB_BADGE: Record<string, string> = {
  done: "bg-[#10B981]/15 text-[#10B981]",
  running: "bg-[#7B61FF]/15 text-[#A78BFA]",
  failed: "bg-[#EF4444]/15 text-[#EF4444]",
};

const MOCK_AGENTS = [
  { name: "node-prod-01", os: "Linux", version: "v0.4.1", status: "Backup running", online: true },
  { name: "node-prod-02", os: "Linux", version: "v0.4.1", status: "Idle", online: true },
  { name: "node-staging", os: "Ubuntu", version: "v0.4.0", status: "Offline", online: false },
];

const MOCK_JOBS = [
  { id: 1, path: "/var/data/postgres", size: "4.2 GB", status: "done" },
  { id: 2, path: "/home/deploy/app", size: "812 MB", status: "running" },
  { id: 3, path: "/etc/nginx", size: "64 KB", status: "done" },
  { id: 4, path: "/var/log/apps", size: "1.8 GB", status: "failed" },
];

const MOCK_KPIS = [
  { label: "Agents", value: "3", sub: "2 online · 1 offline", highlight: "text-white" },
  { label: "Jobs today", value: "14", sub: "12 done · 2 running", highlight: "text-white" },
  { label: "Data protected", value: "62 GB", sub: "+4.2 GB today", highlight: "text-[#A78BFA]" },
  { label: "Failed", value: "1", sub: "Last: 2h ago", highlight: "text-[#EF4444]" },
];

const MOCK_JOBS_FULL = [
  { id: 1, path: "/var/data/postgres", agent: "node-prod-01", time: "14:28", size: "4.2 GB", status: "done" },
  { id: 2, path: "/home/deploy/app", agent: "node-prod-02", time: "14:31", size: "812 MB", status: "running" },
  { id: 3, path: "/etc/nginx/conf.d", agent: "node-prod-01", time: "13:55", size: "64 KB", status: "done" },
  { id: 4, path: "/var/log/apps", agent: "node-staging", time: "12:00", size: "1.8 GB", status: "failed" },
  { id: 5, path: "/home/user/docs", agent: "node-prod-01", time: "11:40", size: "220 MB", status: "done" },
];

const MOCK_NAV_ITEMS = [
  { label: "Overview", active: false, icon: <IconGrid className="h-3.5 w-3.5" /> },
  { label: "Agents", active: false, icon: <IconServer className="h-3.5 w-3.5" /> },
  { label: "Jobs", active: true, icon: <IconActivity className="h-3.5 w-3.5" /> },
  { label: "Restore", active: false, icon: <IconRestore className="h-3.5 w-3.5" /> },
  { label: "Policies", active: false, icon: <IconShield className="h-3.5 w-3.5" /> },
  { label: "Alerts", active: false, icon: <IconAlert className="h-3.5 w-3.5" /> },
];

const ARCH_STEPS = [
  { label: "Agent", desc: "Roda localmente. Monitora fontes definidas por política.", icon: <IconServer className="h-4 w-4" />, highlight: true },
  { label: "Chunk & Dedup", desc: "Divide por conteúdo. Elimina dados já enviados.", icon: <IconLayers className="h-4 w-4" />, highlight: false },
  { label: "Backend API", desc: "Recebe chunks, verifica integridade e coordena jobs.", icon: <IconLink className="h-4 w-4" />, highlight: false },
  { label: "Object Storage", desc: "Armazena chunks e snapshots. Compatível com S3/MinIO.", icon: <IconDatabase className="h-4 w-4" />, highlight: false },
  { label: "Manifest", desc: "Índice imutável de snapshots. Base determinística do restore.", icon: <IconFileText className="h-4 w-4" />, highlight: false },
  { label: "Restore", desc: "Reconstrói a partir do manifesto. Verificável por hash.", icon: <IconRestore className="h-4 w-4" />, highlight: true },
];

const AGENT_FEATURES = [
  { title: "Execução local", desc: "O agente roda no host, sem depender de rede para executar.", icon: <IconServer className="h-5 w-5" /> },
  { title: "Controle por política", desc: "Fontes, retenção e frequência definidos centralmente.", icon: <IconShield className="h-5 w-5" /> },
  { title: "Envio eficiente", desc: "Deduplicação antes de enviar. Só chunks novos trafegam.", icon: <IconZap className="h-5 w-5" /> },
  { title: "Restore verificável", desc: "Cada chunk tem hash. O manifesto garante integridade.", icon: <IconCheck className="h-5 w-5" /> },
  { title: "Comunicação REST", desc: "O agente se registra, reporta status e recebe comandos via API.", icon: <IconLink className="h-5 w-5" /> },
];

const FEATURES = [
  { title: "Backup Jobs", desc: "Jobs programados por política. Snapshots incrementais com controle de retenção.", icon: <IconArchive className="h-5 w-5" /> },
  { title: "Restore Workflows", desc: "Restore de arquivo, diretório ou snapshot completo, pela interface ou API.", icon: <IconRestore className="h-5 w-5" /> },
  { title: "Device & Agent Management", desc: "Cadastro, status e controle de agentes. Visualização de dispositivos protegidos.", icon: <IconServer className="h-5 w-5" /> },
  { title: "Deduplication", desc: "Chunking por conteúdo-endereçável. Reduz tráfego e armazenamento.", icon: <IconLayers className="h-5 w-5" /> },
  { title: "Storage Backend", desc: "Compatível com MinIO e S3. Chunks e manifestos em object storage.", icon: <IconDatabase className="h-5 w-5" /> },
  { title: "Alerts & Observability", desc: "Jobs com falha, agentes offline e anomalias reportados no painel.", icon: <IconAlert className="h-5 w-5" /> },
  { title: "Policy Orchestration", desc: "Políticas centralizadas por grupo ou dispositivo, distribuídas automaticamente.", icon: <IconShield className="h-5 w-5" /> },
];

const OPEN_SOURCE_POINTS = [
  { title: "Código auditável", desc: "Leia, inspecione e contribua. Sem caixas pretas na sua infraestrutura." },
  { title: "Self-hosted", desc: "Rode completamente em sua infra. Sem dependência de serviço externo." },
  { title: "Roadmap público", desc: "Veja o que está sendo desenvolvido e discuta prioridades." },
  { title: "Contribuições abertas", desc: "Issues, PRs e discussões abertas. Construído com a comunidade." },
];

const USE_CASES = [
  { title: "Homelab", desc: "Backup de VMs, containers e dados pessoais. Controle total, sem assinatura.", icon: <IconHome className="h-5 w-5" /> },
  { title: "MSP / Lab técnico", desc: "Gerencie backups de múltiplos clientes em uma plataforma auditável.", icon: <IconUsers className="h-5 w-5" /> },
  { title: "DevOps / SRE", desc: "Integre backup no pipeline. Políticas como código, restore testável em staging.", icon: <IconCode className="h-5 w-5" /> },
  { title: "Infraestrutura pequena", desc: "Sem solução enterprise. Simples de implantar, fácil de operar.", icon: <IconServer className="h-5 w-5" /> },
];

const SECURITY_POINTS = [
  { title: "Hash por chunk", desc: "Cada chunk tem hash de conteúdo. Integridade verificável localmente e no restore.", icon: <IconHash className="h-5 w-5" />, roadmap: false },
  { title: "Manifesto de snapshot", desc: "Cada snapshot gera um manifesto imutável. Restore reproduzível.", icon: <IconFileText className="h-5 w-5" />, roadmap: false },
  { title: "Autenticação por token", desc: "Agentes se autenticam com token. Controle de acesso por dispositivo.", icon: <IconKey className="h-5 w-5" />, roadmap: false },
  { title: "Isolamento de agente", desc: "Cada agente opera de forma isolada. Falha em um não afeta os demais.", icon: <IconShield className="h-5 w-5" />, roadmap: false },
  { title: "Criptografia em trânsito", desc: "Comunicação via HTTPS. Criptografia end-to-end planejada para dados em repouso.", icon: <IconLock className="h-5 w-5" />, roadmap: true },
  { title: "Auditoria de jobs", desc: "Log completo de execuções, falhas e restores. Rastreabilidade total.", icon: <IconClipboard className="h-5 w-5" />, roadmap: false },
];

const FOOTER_COLS = [
  {
    title: "Produto",
    links: [
      { label: "Download", href: DOWNLOAD_URL },
      { label: "GitHub", href: GITHUB_URL },
    ],
  },
  {
    title: "Desenvolvedores",
    links: [
      { label: "Documentação", href: DOCS_URL },
      { label: "Contribuir", href: GITHUB_URL + "/blob/main/CONTRIBUTING.md" },
    ],
  },
  {
    title: "Suporte",
    links: [
      { label: "Issues", href: GITHUB_URL + "/issues" },
      { label: "Contato", href: "mailto:angelolealpl14@gmail.com" },
    ],
  },
];
