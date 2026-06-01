import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";

export default function LandingPage() {
  return (
    <div className="flex flex-col bg-white text-[#111827]">

      {/* NAV */}
      <header className="sticky top-0 z-50 border-b border-gray-100 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-6 px-6 py-4">
          <KeeplyLogo />
          <nav className="hidden items-center gap-8 text-sm text-gray-500 md:flex">
            <a href="#features" className="hover:text-gray-900">Recursos</a>
            <a href="#how" className="hover:text-gray-900">Como funciona</a>
            <a href="#pricing" className="hover:text-gray-900">Planos</a>
            <a href="#faq" className="hover:text-gray-900">FAQ</a>
          </nav>
          <div className="flex items-center gap-3">
            <Link href="/login" className="text-sm font-medium text-gray-600 hover:text-gray-900">
              Entrar
            </Link>
            <Link href="/register" className="kp-btn-primary rounded-lg px-5 py-2 text-sm font-semibold">
              Começar grátis
            </Link>
          </div>
        </div>
      </header>

      {/* HERO */}
      <section className="border-b border-gray-100 bg-white py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="grid gap-16 lg:grid-cols-2 lg:items-center">
            <div>
              <span className="inline-flex items-center gap-2 rounded-full bg-violet-50 px-3 py-1 text-xs font-medium text-violet-700 ring-1 ring-violet-100">
                <span className="h-1.5 w-1.5 rounded-full bg-violet-500" />
                Backup inteligente para equipes
              </span>
              <h1 className="mt-6 text-5xl font-semibold leading-tight tracking-tight text-gray-900">
                Proteja cada máquina.<br />
                Restaure em minutos.
              </h1>
              <p className="mt-5 max-w-lg text-lg text-gray-500">
                Snapshots incrementais automáticos, compressão real e painel centralizado.
                Instale o agente em qualquer máquina e comece em menos de 2 minutos.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link href="/register" className="kp-btn-primary rounded-lg px-6 py-3 text-sm font-semibold">
                  Criar conta gratuita
                </Link>
                <Link
                  href="/login"
                  className="rounded-lg border border-gray-200 bg-white px-6 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50"
                >
                  Já tenho conta →
                </Link>
              </div>
              <p className="mt-4 text-xs text-gray-400">
                Sem cartão de crédito · Sem configuração complexa
              </p>
              <div className="mt-10 grid grid-cols-3 gap-6 border-t border-gray-100 pt-8">
                {[
                  { v: "99.9%", l: "Uptime" },
                  { v: "~3x", l: "Compressão" },
                  { v: "< 2 min", l: "Setup" },
                ].map((s) => (
                  <div key={s.l}>
                    <p className="text-2xl font-bold text-gray-900">{s.v}</p>
                    <p className="mt-0.5 text-sm text-gray-400">{s.l}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Dashboard preview */}
            <div className="relative">
              <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl shadow-gray-100">
                {/* browser bar */}
                <div className="flex items-center gap-2 border-b border-gray-100 bg-gray-50 px-4 py-3">
                  <span className="h-3 w-3 rounded-full bg-red-400" />
                  <span className="h-3 w-3 rounded-full bg-yellow-400" />
                  <span className="h-3 w-3 rounded-full bg-green-400" />
                  <div className="ml-3 flex-1 rounded bg-gray-200 px-3 py-1 text-xs text-gray-400">
                    app.keeply.io/dashboard
                  </div>
                </div>
                <div className="flex bg-white">
                  {/* sidebar preview */}
                  <div className="hidden w-44 shrink-0 border-r border-gray-100 bg-[#15102a] p-3 sm:block">
                    <div className="mb-4 flex items-center gap-2 px-2 py-1">
                      <div className="h-4 w-4 rounded bg-violet-500/40" />
                      <div className="h-2.5 w-12 rounded bg-white/20" />
                    </div>
                    {["Visão geral", "Máquinas", "Backups", "Proteção"].map((l, i) => (
                      <div
                        key={l}
                        className={`mb-1 flex items-center gap-2 rounded-lg px-2 py-1.5 ${
                          i === 0 ? "bg-violet-600/50" : ""
                        }`}
                      >
                        <div className="h-3 w-3 rounded-sm bg-white/20" />
                        <span className={`text-[11px] ${i === 0 ? "text-white" : "text-white/40"}`}>{l}</span>
                      </div>
                    ))}
                  </div>
                  {/* main area */}
                  <div className="flex-1 p-4">
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                      {[
                        { l: "Máquinas", v: "12", color: "text-violet-600" },
                        { l: "Backups", v: "248", color: "text-emerald-600" },
                        { l: "Volume", v: "1.4 TB", color: "text-sky-600" },
                        { l: "Sucesso", v: "99.2%", color: "text-violet-600" },
                      ].map((k) => (
                        <div key={k.l} className="rounded-lg border border-gray-100 bg-gray-50 p-2.5">
                          <p className="text-[9px] text-gray-400">{k.l}</p>
                          <p className={`mt-0.5 text-base font-bold ${k.color}`}>{k.v}</p>
                        </div>
                      ))}
                    </div>
                    <div className="mt-3 flex h-20 items-end gap-1 rounded-lg border border-gray-100 bg-gray-50 p-2.5">
                      {[30, 55, 40, 80, 60, 90, 45, 70, 85, 50, 75, 95, 60, 88].map((h, i) => (
                        <div key={i} className="flex-1 rounded-sm bg-violet-500/70" style={{ height: `${h}%` }} />
                      ))}
                    </div>
                    <div className="mt-3 space-y-1.5">
                      {[
                        { m: "workstation-01", s: "Concluído", ok: true },
                        { m: "servidor-fiscal", s: "Em execução", ok: false },
                        { m: "notebook-design", s: "Concluído", ok: true },
                      ].map((r) => (
                        <div key={r.m} className="flex items-center gap-2 rounded-lg border border-gray-100 px-2.5 py-1.5">
                          <div className="h-6 w-6 shrink-0 rounded-md bg-violet-100" />
                          <div className="min-w-0 flex-1">
                            <div className="h-2 w-20 rounded bg-gray-200" />
                          </div>
                          <span className={`rounded-full px-2 py-0.5 text-[9px] font-medium ${r.ok ? "bg-emerald-50 text-emerald-600" : "bg-violet-50 text-violet-600"}`}>
                            {r.s}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
              {/* subtle glow behind card */}
              <div className="pointer-events-none absolute -bottom-8 -right-8 -z-10 h-64 w-64 rounded-full bg-violet-100 blur-3xl" />
            </div>
          </div>
        </div>
      </section>

      {/* TRUST BAR */}
      <section className="border-b border-gray-100 bg-gray-50 py-10">
        <p className="text-center text-xs font-medium uppercase tracking-widest text-gray-400">
          Confiado por equipes que não podem perder dados
        </p>
        <div className="mx-auto mt-6 flex max-w-4xl flex-wrap items-center justify-center gap-x-12 gap-y-3">
          {["Startups", "Agências", "Desenvolvedores", "PMEs", "Freelancers", "DevOps"].map((l) => (
            <span key={l} className="text-sm font-semibold text-gray-300">{l}</span>
          ))}
        </div>
      </section>

      {/* FEATURES */}
      <section id="features" className="bg-white py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="mx-auto max-w-2xl text-center">
            <span className="text-xs font-semibold uppercase tracking-widest text-violet-600">Recursos</span>
            <h2 className="mt-3 text-4xl font-semibold tracking-tight text-gray-900">
              Tudo que você precisa para proteger sua operação
            </h2>
          </div>
          <div className="mt-16 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <div key={f.title} className="rounded-xl border border-gray-100 bg-white p-6 hover:border-violet-200 hover:shadow-sm transition-all">
                <div className="grid h-10 w-10 place-items-center rounded-lg bg-violet-50 text-violet-600">
                  {f.icon}
                </div>
                <h3 className="mt-5 text-base font-semibold text-gray-900">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-500">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}
      <section id="how" className="border-y border-gray-100 bg-gray-50 py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="mx-auto max-w-2xl text-center">
            <span className="text-xs font-semibold uppercase tracking-widest text-violet-600">Como funciona</span>
            <h2 className="mt-3 text-4xl font-semibold tracking-tight text-gray-900">
              Em produção em menos de 5 minutos
            </h2>
          </div>
          <div className="mt-16 grid gap-8 md:grid-cols-3">
            {STEPS.map((s, i) => (
              <div key={s.title} className="relative text-center">
                <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-violet-600 text-white shadow-lg shadow-violet-100">
                  <span className="text-xl font-bold">{i + 1}</span>
                </div>
                {i < STEPS.length - 1 && (
                  <div className="absolute left-[calc(50%+3.5rem)] top-7 hidden h-0.5 w-[calc(100%-7rem)] bg-gray-200 md:block" />
                )}
                <h3 className="mt-5 text-base font-semibold text-gray-900">{s.title}</h3>
                <p className="mt-2 text-sm text-gray-500">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* STATS */}
      <section className="bg-white py-20">
        <div className="mx-auto max-w-5xl px-6">
          <div className="grid gap-8 text-center md:grid-cols-4">
            {STATS.map((s) => (
              <div key={s.label}>
                <p className="text-4xl font-bold text-gray-900">{s.value}</p>
                <p className="mt-2 text-sm text-gray-400">{s.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* PRICING */}
      <section id="pricing" className="border-y border-gray-100 bg-gray-50 py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="mx-auto max-w-2xl text-center">
            <span className="text-xs font-semibold uppercase tracking-widest text-violet-600">Planos</span>
            <h2 className="mt-3 text-4xl font-semibold tracking-tight text-gray-900">
              Simples, previsível, sem surpresas
            </h2>
          </div>
          <div className="mt-16 grid gap-5 md:grid-cols-3">
            {PLANS.map((p) => (
              <div
                key={p.name}
                className={`relative flex flex-col rounded-2xl bg-white p-8 ${
                  p.featured
                    ? "border-2 border-violet-500 shadow-lg shadow-violet-50"
                    : "border border-gray-200"
                }`}
              >
                {p.featured && (
                  <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
                    <span className="rounded-full bg-violet-600 px-4 py-1 text-xs font-semibold text-white">
                      Mais popular
                    </span>
                  </div>
                )}
                <p className="text-xs font-semibold uppercase tracking-wider text-violet-600">{p.name}</p>
                <div className="mt-3 flex items-end gap-1">
                  <span className="text-4xl font-bold text-gray-900">{p.price}</span>
                  {p.period && <span className="mb-1 text-gray-400">{p.period}</span>}
                </div>
                <p className="mt-2 text-sm text-gray-500">{p.desc}</p>
                <ul className="mt-6 flex-1 space-y-2.5">
                  {p.features.map((f) => (
                    <li key={f} className="flex items-start gap-2 text-sm text-gray-600">
                      <span className="mt-0.5 font-bold text-violet-500">✓</span>
                      {f}
                    </li>
                  ))}
                </ul>
                <Link
                  href="/register"
                  className={`mt-8 rounded-lg px-4 py-2.5 text-center text-sm font-semibold transition-colors ${
                    p.featured
                      ? "kp-btn-primary"
                      : "border border-gray-200 text-gray-700 hover:bg-gray-50"
                  }`}
                >
                  {p.cta}
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section id="faq" className="bg-white py-24">
        <div className="mx-auto max-w-3xl px-6">
          <h2 className="text-center text-3xl font-semibold tracking-tight text-gray-900">
            Perguntas frequentes
          </h2>
          <div className="mt-10 space-y-3">
            {FAQ.map((q) => (
              <div key={q.q} className="rounded-xl border border-gray-100 bg-white px-6 py-5">
                <h3 className="text-sm font-semibold text-gray-900">{q.q}</h3>
                <p className="mt-2 text-sm text-gray-500">{q.a}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="border-t border-gray-100 bg-gray-50 py-24">
        <div className="mx-auto max-w-3xl px-6 text-center">
          <h2 className="text-4xl font-semibold tracking-tight text-gray-900">
            Sua operação está protegida?
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-lg text-gray-500">
            Comece em menos de 5 minutos. Sem cartão, sem burocracia.
          </p>
          <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
            <Link href="/register" className="kp-btn-primary rounded-lg px-8 py-3.5 text-base font-semibold">
              Criar conta gratuita
            </Link>
            <Link
              href="/login"
              className="rounded-lg border border-gray-200 bg-white px-8 py-3.5 text-base font-semibold text-gray-700 hover:bg-gray-50"
            >
              Entrar na conta
            </Link>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="border-t border-gray-100 bg-white">
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="grid gap-10 md:grid-cols-5">
            <div className="md:col-span-2">
              <KeeplyLogo />
              <p className="mt-4 max-w-xs text-sm text-gray-400 leading-relaxed">
                Backup inteligente, restauração simples e total controle sobre todas as suas máquinas.
              </p>
            </div>
            {FOOTER_COLS.map((col) => (
              <div key={col.heading}>
                <p className="text-xs font-semibold uppercase tracking-widest text-gray-400">{col.heading}</p>
                <ul className="mt-4 space-y-2.5">
                  {col.links.map((l) => (
                    <li key={l}>
                      <a href="#" className="text-sm text-gray-500 hover:text-gray-900">{l}</a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-gray-100 pt-8 text-xs text-gray-400 md:flex-row">
            <p>© {new Date().getFullYear()} Keeply. Todos os direitos reservados.</p>
            <div className="flex gap-6">
              <a href="#" className="hover:text-gray-600">Privacidade</a>
              <a href="#" className="hover:text-gray-600">Termos</a>
              <a href="#" className="hover:text-gray-600">Segurança</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

const FEATURES = [
  {
    title: "Snapshots incrementais",
    desc: "Apenas os blocos que mudaram são enviados — velocidade máxima, custo mínimo.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></svg>,
  },
  {
    title: "Compressão e deduplicação",
    desc: "Armazene até 3x mais com compressão de blocos e deduplicação automática.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M3 5v6c0 1.7 4 3 9 3s9-1.3 9-3V5" /><path d="M3 11v6c0 1.7 4 3 9 3s9-1.3 9-3v-6" /></svg>,
  },
  {
    title: "Restauração granular",
    desc: "Restaure um único arquivo, uma pasta ou o sistema inteiro a qualquer snapshot anterior.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" /><path d="M3 3v5h5" /><path d="M12 7v5l4 2" /></svg>,
  },
  {
    title: "Painel centralizado",
    desc: "Todas as suas máquinas, snapshots e alertas em um único painel moderno.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></svg>,
  },
  {
    title: "Agente leve",
    desc: "Processo em background com consumo mínimo de CPU e memória.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" /><circle cx="7" cy="7.5" r="0.7" fill="currentColor" /><circle cx="7" cy="16.5" r="0.7" fill="currentColor" /></svg>,
  },
  {
    title: "Multi-plataforma",
    desc: "Suporte a Windows, Linux e macOS — proteja toda a sua infra.",
    icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6l-8-3Z" /><path d="m9 12 2 2 4-4" /></svg>,
  },
];

const STEPS = [
  { title: "Crie sua conta", desc: "Cadastro em segundos. Seu painel fica disponível imediatamente." },
  { title: "Instale o agente", desc: "Um único comando na máquina. O agente se registra sozinho." },
  { title: "Tudo protegido", desc: "Snapshots automáticos, compressão e deduplicação. Acompanhe pelo painel." },
];

const STATS = [
  { value: "99.9%", label: "Uptime do serviço" },
  { value: "~3x", label: "Compressão média" },
  { value: "< 2 min", label: "Setup do agente" },
  { value: "24/7", label: "Monitoramento" },
];

const PLANS = [
  {
    name: "Solo", price: "Grátis", period: "", desc: "Para desenvolvedores e uso pessoal.",
    features: ["Até 2 máquinas", "10 GB armazenamento", "Snapshots diários", "7 dias de histórico"],
    cta: "Começar grátis", featured: false,
  },
  {
    name: "Pro", price: "R$ 49", period: "/mês", desc: "Para equipes que precisam de confiabilidade.",
    features: ["Até 10 máquinas", "500 GB armazenamento", "Snapshots de hora em hora", "30 dias de histórico", "Restauração granular", "Suporte prioritário"],
    cta: "Começar grátis", featured: true,
  },
  {
    name: "Enterprise", price: "Custom", period: "", desc: "Para empresas com escala e requisitos especiais.",
    features: ["Máquinas ilimitadas", "Armazenamento ilimitado", "Snapshots contínuos", "SSO / SAML", "SLA garantido", "Onboarding dedicado"],
    cta: "Falar com vendas", featured: false,
  },
];

const FAQ = [
  { q: "É o mesmo login do agente?", a: "Sim — o painel web e o agente compartilham a mesma conta. Registre-se pelo painel e use as mesmas credenciais no agente." },
  { q: "O agente consome muitos recursos?", a: "Não. Menos de 1% de CPU em idle. Upload apenas dos blocos modificados, sem impactar sua rede perceptivelmente." },
  { q: "Onde ficam os dados armazenados?", a: "Em nosso object storage compatível com S3. Em planos Enterprise você pode usar seu próprio bucket." },
  { q: "Consigo restaurar um arquivo específico?", a: "Sim. No painel você navega pelos arquivos de qualquer snapshot e faz download individual ou em lote." },
  { q: "O que acontece se perder conexão durante o backup?", a: "O agente retoma de onde parou. Apenas os blocos ainda não enviados são transferidos na próxima tentativa." },
];

const FOOTER_COLS = [
  { heading: "Produto", links: ["Recursos", "Planos", "Roadmap", "Changelog"] },
  { heading: "Empresa", links: ["Sobre", "Blog", "Segurança", "Privacidade"] },
  { heading: "Suporte", links: ["Documentação", "Status", "Comunidade", "Contato"] },
];
