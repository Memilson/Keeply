import Link from "next/link";
import { KeeplyLogo } from "@/components/KeeplyLogo";
import { LiveDemoBanner } from "@/components/LiveDemoBanner";

const HEADER_LINKS = [
  { label: "Documentação", href: "/documentacao" },
  { label: "Roadmap", href: "/roadmap" },
  { label: "Download", href: "/download" },
];

const ROADMAP = [
  {
    status: "Disponível",
    state: "done",
    title: "Site",
    text: "Landing, autenticação web e estrutura inicial de apresentação do produto já estão no ar.",
    subtasks: ["Landing publicada", "Login web ativo", "Cadastro web ativo", "Navegação principal criada"],
  },
  {
    status: "Disponível",
    state: "done",
    title: "Agente",
    text: "O agente com fluxo de backup e restauração já faz parte da base atual da plataforma.",
    subtasks: ["Autenticação com backend", "Execução de backup", "Fluxo de restauração", "Modo daemon/headless"],
  },
  {
    status: "Em evolução",
    state: "progress",
    title: "CDP",
    text: "Continuous Data Protection ainda é uma frente em aberto e faz parte das próximas evoluções importantes.",
    subtasks: ["Definir modelo de captura contínua", "Reduzir janela entre alterações e envio", "Validar impacto operacional"],
  },
  {
    status: "Em evolução",
    state: "progress",
    title: "Criptografia",
    text: "A camada de encriptação dedicada ainda está no roadmap e não deve ser tratada como entrega concluída agora.",
    subtasks: ["Definir estratégia de encriptação", "Aplicar no pipeline de backup", "Validar restore com dados protegidos"],
  },
  {
    status: "Planejado",
    state: "todo",
    title: "Mobile",
    text: "A presença mobile ainda está prevista como etapa futura e não entra como suporte atual do produto.",
    subtasks: ["Definir escopo mobile", "Avaliar leitura de status e alertas", "Planejar acesso ao painel em tela pequena"],
  },
  {
    status: "Em breve",
    state: "todo",
    title: "Download",
    text: "A distribuição pública e organizada de downloads terá uma página própria, mas ainda está em fase de preparação.",
    subtasks: ["Organizar pacotes", "Definir instruções por ambiente", "Publicar acesso centralizado"],
  },
];

export default function RoadmapPage() {
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
                  link.href === "/roadmap" ? "text-[#5d48ff]" : "text-[#17183d]"
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

      <section className="px-8 py-14">
        <div className="mx-auto max-w-[1080px]">
          <div className="mx-auto max-w-[840px] text-center">
            <p className="text-sm font-bold text-[#604bff]">Roadmap</p>
            <h1 className="mt-2 text-[34px] font-black leading-tight text-[#090b24]">
              O que já existe e o que ainda está em construção.
            </h1>
          </div>

          <div className="mx-auto mt-8 max-w-[840px] space-y-3">
            {ROADMAP.map((item) => (
              <article
                key={item.title}
                className="flex gap-3 rounded-[12px] border border-[#e5def7] bg-white p-5 shadow-[0_10px_30px_rgba(86,62,180,0.05)]"
              >
                <div
                  className={`mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                    item.state === "done"
                      ? "border-[#14b86a] bg-[#eafbf3] text-[#14b86a]"
                      : item.state === "progress"
                        ? "border-[#7b61ff] bg-[#f3efff] text-[#7b61ff]"
                        : "border-[#cfc7ea] bg-[#fbfaff] text-[#a29aba]"
                  }`}
                >
                  {item.state === "done" ? (
                    <svg aria-hidden="true" viewBox="0 0 16 16" className="h-3.5 w-3.5 fill-none stroke-current stroke-[2.4]">
                      <path d="M3.5 8.2 6.5 11l6-6" />
                    </svg>
                  ) : item.state === "progress" ? (
                    <span className="h-2.5 w-2.5 rounded-full bg-current" />
                  ) : null}
                </div>

                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-3">
                    <h2 className="text-[19px] font-black text-[#090b24]">{item.title}</h2>
                    <span
                      className={`rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] ${
                        item.state === "done"
                          ? "bg-[#eafbf3] text-[#14945a]"
                          : item.state === "progress"
                            ? "bg-[#f3efff] text-[#6a4fff]"
                            : "bg-[#f4f2fb] text-[#8c84aa]"
                      }`}
                    >
                      {item.status}
                    </span>
                  </div>
                  <p className="mt-2 text-[14px] leading-6 text-[#62678f]">{item.text}</p>
                  <ul className="mt-3 space-y-1.5 text-[14px] leading-6 text-[#4f4c6f]">
                    {item.subtasks.map((subtask) => (
                      <li key={subtask} className="flex items-start gap-2">
                        <span className="mt-[8px] h-1.5 w-1.5 shrink-0 rounded-full bg-[#7b61ff]" />
                        <span>{subtask}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
