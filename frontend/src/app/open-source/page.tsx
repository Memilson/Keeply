import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";

const HEADER_LINKS = [
  { label: "Documentação", href: "/documentacao" },
  { label: "Roadmap", href: "/roadmap" },
  { label: "Download", href: "/download" },
];

const VALUES = [
  {
    title: "Transparência",
    text: "A estrutura do projeto fica visível: agente em Java, backend em Spring Boot, banco PostgreSQL e objetos no MinIO, sem esconder como o fluxo funciona.",
  },
  {
    title: "Autonomia",
    text: "A base pode ser rodada localmente, ajustada para o ambiente e evoluída sem depender de uma plataforma fechada para começar a usar ou testar.",
  },
  {
    title: "Base técnica real",
    text: "Não é só conceito visual: já existe fluxo de autenticação, backup, armazenamento, auditoria de snapshot e restauração dentro da arquitetura atual.",
  },
  {
    title: "Evolução contínua",
    text: "O projeto ainda está evoluindo, então o open source aqui também serve para deixar claro o que já foi entregue e o que ainda está em aberto no roadmap.",
  },
];

export default function OpenSourcePage() {
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
                  link.href === "/open-source" ? "text-[#5d48ff]" : "text-[#17183d]"
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

      <section className="px-8 py-20">
        <div className="mx-auto max-w-[1540px] lg:px-12">
          <div className="max-w-[760px]">
            <p className="text-sm font-bold text-[#604bff]">Open Source</p>
            <h1 className="mt-3 text-[44px] font-black leading-tight text-[#090b24]">
              Uma base aberta para quem quer controle técnico e evolução clara.
            </h1>
            <a
              href="https://github.com/Memilson/Keeply.git"
              target="_blank"
              rel="noreferrer"
              className="mt-6 inline-flex items-center rounded-[10px] border border-[#d9d1f4] bg-white px-4 py-2.5 text-[14px] font-semibold text-[#5d48ff] transition hover:border-[#5d48ff]"
            >
              Ver repositório no GitHub
            </a>
          </div>

          <div className="mt-14 grid gap-5 md:grid-cols-2">
            {VALUES.map((item) => (
              <article key={item.title} className="rounded-[14px] border border-[#e5def7] bg-white p-7 shadow-[0_10px_30px_rgba(86,62,180,0.05)]">
                <h2 className="text-[24px] font-black text-[#090b24]">{item.title}</h2>
                <p className="mt-3 text-[16px] leading-8 text-[#62678f]">{item.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
