import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";
import { LiveDemoBanner } from "@/components/LiveDemoBanner";

const HEADER_LINKS = [
  { label: "Documentação", href: "/documentacao" },
  { label: "Roadmap", href: "/roadmap" },
  { label: "Download", href: "/download" },
];

export default function DownloadPage() {
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
                  link.href === "/download" ? "text-[#5d48ff]" : "text-[#17183d]"
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

      <section className="px-8 py-20">
        <div className="mx-auto max-w-[1540px] lg:px-12">
          <div className="max-w-[760px]">
            <p className="text-sm font-bold text-[#604bff]">Download</p>
            <h1 className="mt-3 text-[44px] font-black leading-tight text-[#090b24]">
              Downloads em breve.
            </h1>
          </div>

          <div className="mt-12 max-w-[760px] rounded-[16px] border border-[#e5def7] bg-white p-8 shadow-[0_10px_30px_rgba(86,62,180,0.05)]">
            <p className="text-[15px] leading-8 text-[#62678f]">
              No momento, o produto considera Linux Ubuntu como base de compatibilidade atual.
              Windows pode entrar depois, mas ainda não deve ser tratado como entrega confirmada.
            </p>
            <p className="mt-6 text-[15px] leading-8 text-[#62678f]">
              Se precisar falar sobre acesso técnico, distribuição ou alinhamento inicial, use{" "}
              <a href="mailto:angelolealpl14@gmail.com" className="font-semibold text-[#5d48ff] hover:text-[#4f39ea]">
                angelolealpl14@gmail.com
              </a>.
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
