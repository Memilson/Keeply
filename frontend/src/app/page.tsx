"use client";

import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";

export default function LandingPage() {
  return (
    <main className="min-h-screen overflow-hidden bg-[#fbfaff] text-[#090b24]">
      <div className="fixed left-0 right-0 top-0 z-50 border-b border-[#6f56ff] bg-[#7b61ff]">
        <div className="mx-auto flex min-h-[44px] max-w-[1540px] items-center justify-center gap-3 px-6 text-center text-[14px] font-semibold text-white lg:px-12">
          <span className="whitespace-nowrap">Live demo</span>
          <span className="grid h-6 w-6 place-items-center rounded-full border border-white/70">
            <svg aria-hidden="true" viewBox="0 0 12 12" className="h-3.5 w-3.5 fill-current">
              <path d="M2 5.25h5.19L5.1 3.16 6.16 2.1 10.06 6l-3.9 3.9L5.1 8.84l2.09-2.09H2V5.25Z" />
            </svg>
          </span>
          <span>Veja a plataforma em ação e conheça o fluxo completo de proteção e restauração.</span>
        </div>
      </div>

      <header className="fixed left-0 right-0 top-[44px] z-50 border-b border-[#e8e6f4] bg-white/92 backdrop-blur-xl">
        <div className="mx-auto flex h-[76px] max-w-[1540px] items-center justify-between px-8 lg:px-12">
          <Link href="/" aria-label="Keeply home">
            <KeeplyLogo size={34} />
          </Link>

          <nav className="hidden items-center gap-8 lg:flex">
            {HEADER_LINKS.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className="text-[14px] font-semibold text-[#17183d] transition hover:text-[#5d48ff]"
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

      <section className="relative pt-[120px]">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_20%,rgba(99,76,255,0.16),transparent_34%),radial-gradient(circle_at_46%_50%,rgba(124,98,255,0.09),transparent_35%)]" />
        <div className="absolute inset-x-0 top-[120px] h-px bg-[#e8e6f4]" />

        <div className="relative mx-auto grid min-h-[660px] max-w-[1540px] grid-cols-1 items-center gap-12 px-8 pb-14 pt-14 lg:grid-cols-[0.64fr_1.36fr] lg:px-12 lg:pt-4">
          <div className="z-10 max-w-[460px]">
            <h1 className="text-[26px] font-black leading-[1.18] text-[#090b24] sm:text-[32px] lg:text-[36px]">
              Proteja endpoints, servidores
              <br />
              e dados com confiança.
              <br />
              <span className="text-[#5f4bff]">Restaure em minutos,</span>
              <br />
              gerencie tudo em um só lugar.
            </h1>

            <p className="mt-5 max-w-[440px] text-[14px] font-medium leading-[1.65] text-[#565b83]">
              Backups automáticos, proteção contínua e políticas centralizadas
              para manter sua equipe e seus dados sempre seguros.
              Recuperação rápida. Implantação simples.
            </p>

            <div className="mt-7 flex flex-col gap-4 sm:flex-row">
              <Link
                href="/register"
                className="inline-flex h-[50px] min-w-[195px] items-center justify-center gap-4 rounded-[8px] bg-[#604bff] px-6 text-[14px] font-bold text-white shadow-[0_14px_28px_rgba(96,75,255,0.32)] transition hover:bg-[#513ee8]"
              >
                Criar conta gratuita
                <span aria-hidden>›</span>
              </Link>
              <Link
                href="/login"
                className="inline-flex h-[50px] min-w-[155px] items-center justify-center gap-3 rounded-[8px] border border-[#dcd9ec] bg-white px-6 text-[14px] font-bold text-[#090b24] shadow-[0_12px_26px_rgba(29,23,78,0.08)] transition hover:border-[#bdb5ff]"
              >
                <span className="grid h-7 w-7 place-items-center rounded-full border-2 border-[#604bff] text-[#604bff]">
                  <svg
                    aria-hidden="true"
                    viewBox="0 0 12 12"
                    className="h-3.5 w-3.5 translate-x-[1px] fill-current"
                  >
                    <path d="M3 2.2v7.6L9 6 3 2.2Z" />
                  </svg>
                </span>
                Ver Demo
              </Link>
            </div>
          </div>

          <div className="relative min-h-[470px] lg:min-h-[650px]">
            <div className="absolute inset-0 bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.72)_18%,rgba(255,255,255,0)_55%)] lg:hidden" />
            <div className="absolute inset-0 opacity-55 [background-image:linear-gradient(#dedbfd_1px,transparent_1px),linear-gradient(90deg,#dedbfd_1px,transparent_1px)] [background-size:42px_42px] [mask-image:radial-gradient(ellipse_at_center,black_8%,transparent_72%)]" />
            <img
              src="/landing3D.png"
              alt="Ilustração 3D de proteção, backups, restaurações e políticas centralizadas"
              width={611}
              height={408}
              className="relative z-10 h-auto w-full max-w-[940px] drop-shadow-[0_38px_55px_rgba(83,65,192,0.24)] lg:absolute lg:right-[-12px] lg:top-1/2 lg:-translate-y-1/2"
            />
          </div>
        </div>

        <TrustBar />
      </section>

      <section id="recursos" className="bg-white px-8 py-24">
        <div className="mx-auto max-w-[1320px]">
          <div className="max-w-[760px]">
            <p className="text-sm font-bold text-[#604bff]">Plataforma</p>
            <h2 className="mt-3 text-[42px] font-black leading-tight text-[#090b24]">
              Uma plataforma simples para proteger, acompanhar e restaurar seus dados.
            </h2>
            <p className="mt-5 max-w-[680px] text-[18px] leading-8 text-[#62678f]">
              A Keeply centraliza backups, dispositivos e restaurações em um só painel.
              Você acompanha o ambiente, executa rotinas com controle e recupera dados sem processo manual.
            </p>
          </div>

          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {FEATURES.map((feature) => (
              <article key={feature.title} className="rounded-[8px] border border-[#e8e6f4] bg-[#fbfaff] p-7">
                <div className="grid h-12 w-12 place-items-center rounded-[8px] bg-[#ede9ff] text-[#604bff]">
                  {feature.icon}
                </div>
                <h3 className="mt-6 text-xl font-black text-[#090b24]">{feature.title}</h3>
                <p className="mt-3 text-base leading-7 text-[#62678f]">{feature.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <footer className="border-t border-[#e8e6f4] bg-[#f8f6ff] px-8 py-10">
        <div className="mx-auto max-w-[1320px]">
          <div className="flex flex-col gap-6 border-b border-[#ddd8f2] pb-8 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:gap-8">
              <p className="text-[16px] font-semibold text-[#17183d]">Acompanhe a Keeply</p>
              <div className="flex flex-wrap items-center gap-4 text-[#17183d]">
                {SOCIALS.map((social) => (
                  <a
                    key={social.label}
                    href={social.href}
                    aria-label={social.label}
                    className="grid h-10 w-10 place-items-center rounded-full border border-[#d8d2f1] bg-white transition hover:border-[#5d48ff] hover:text-[#5d48ff]"
                  >
                    {social.icon}
                  </a>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:gap-6">
              <p className="text-[16px] font-semibold text-[#17183d]">Atualizações e novidades da plataforma</p>
              <Link
                href="/register"
                className="inline-flex h-[48px] items-center justify-center rounded-[8px] bg-[#0d63f3] px-6 text-[15px] font-bold text-white transition hover:bg-[#0b57d8]"
              >
                Criar conta
              </Link>
            </div>
          </div>

          <div className="flex flex-col gap-6 pt-8 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center">
              <KeeplyLogo size={42} />
            </div>

            <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-[15px] text-[#275dc6]">
              {FOOTER_LINKS.map((link) => (
                <a key={link.label} href={link.href} className="transition hover:text-[#5d48ff]">
                  {link.label}
                </a>
              ))}
            </div>

            <p className="text-[15px] text-[#17183d]">© 2026 Keeply</p>
          </div>
        </div>
      </footer>
    </main>
  );
}

function TrustBar() {
  const carouselLogos = [...TRUST_LOGOS, ...TRUST_LOGOS];

  return (
    <div className="relative mb-12 w-full overflow-hidden">
      <div className="border-y border-[#dcd9f0] bg-white/60 py-7 shadow-[0_18px_50px_rgba(70,60,150,0.08)] backdrop-blur-xl">
        <div className="flex items-center justify-center gap-5 text-center text-[13px] font-black text-[#5960ae]">
          <span className="hidden h-px w-8 bg-[#9c93ff] sm:block" />
          <span className="grid h-6 w-6 place-items-center rounded-full border border-[#9c93ff] text-[#604bff]">♢</span>
          <span>CONFIADO POR EQUIPES QUE NÃO PODEM PERDER DADOS</span>
          <span className="hidden h-px w-8 bg-[#9c93ff] sm:block" />
        </div>

        <div className="trust-carousel mt-8 text-[#5f63b2]">
          <div className="trust-carousel-track">
            {carouselLogos.map((logo, index) => (
              <div key={`${logo.name}-${index}`} className="trust-carousel-item">
                <span className="text-[30px] text-[#6666bd]" aria-hidden>{logo.mark}</span>
                <span>{logo.name}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

const TRUST_LOGOS = [
  { name: "Macrosoft", mark: "⬢" },
  { name: "EBM", mark: "◇" },
  { name: "OOGLE", mark: "⬡" },
  { name: "NEXORA", mark: "×" },
  { name: "CLOUDVANT", mark: "☁" },
  { name: "DATAVIA", mark: "◐" },
  { name: "Netflox", mark: "▰" },
  { name: "Spotifly", mark: "◉" },
  { name: "Amazin", mark: "⌁" },
  { name: "Notionary", mark: "▣" },
  { name: "Slackers", mark: "✣" },
  { name: "GitHubby", mark: "⌘" },
  { name: "Teslão", mark: "△" },
  { name: "OpenMaybe", mark: "◎" },
  { name: "Figmaço", mark: "⬟" },
  { name: "Zoomerang", mark: "◌" },
  { name: "PayPalito", mark: "◆" },
  { name: "Cloudflare-ish", mark: "☼" },
];

const SOCIALS = [
  {
    label: "X",
    href: "#",
    icon: <span className="text-[15px] font-bold">X</span>,
  },
  {
    label: "Blog",
    href: "#",
    icon: <span className="text-[15px] font-bold">↗</span>,
  },
  {
    label: "RSS",
    href: "#",
    icon: <span className="text-[15px] font-bold">◔</span>,
  },
  {
    label: "YouTube",
    href: "#",
    icon: <span className="text-[15px] font-bold">▶</span>,
  },
  {
    label: "LinkedIn",
    href: "#",
    icon: <span className="text-[15px] font-bold">in</span>,
  },
  {
    label: "GitHub",
    href: "#",
    icon: <span className="text-[15px] font-bold">gh</span>,
  },
];

const FOOTER_LINKS = [
  { label: "Informações legais", href: "#" },
  { label: "Privacidade", href: "#" },
  { label: "Cookies", href: "#" },
  { label: "Contato", href: "mailto:angelolealpl14@gmail.com" },
  { label: "Suporte", href: "#" },
];

const HEADER_LINKS = [
  { label: "Documentação", href: "/documentacao" },
  { label: "Roadmap", href: "/roadmap" },
  { label: "Download", href: "/download" },
];

const FEATURES = [
  {
    title: "Backups automáticos",
    text: "Snapshots programados por política, com deduplicação e compressão para reduzir custo e tráfego.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M4 7h16v12H4z" />
        <path d="M8 7V5h8v2" />
        <path d="M8 13h8" />
      </svg>
    ),
  },
  {
    title: "Restaurações rápidas",
    text: "Recupere arquivos, pastas ou snapshots completos em poucos cliques, sem processos manuais.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M3 12a9 9 0 1 0 3-6.7" />
        <path d="M3 4v6h6" />
        <path d="M12 7v5l4 2" />
      </svg>
    ),
  },
  {
    title: "Políticas centralizadas",
    text: "Controle retenção, fontes protegidas e dispositivos em uma única interface para toda a equipe.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    ),
  },
];
