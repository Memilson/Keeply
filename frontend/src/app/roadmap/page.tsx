import { PublicNav } from "@/components/PublicNav";

const GITHUB_URL = "https://github.com/Memilson/Keeply";

const ROADMAP = [
  {
    status: "Disponível",
    state: "done" as const,
    title: "Site",
    text: "Landing, autenticação web e estrutura inicial de apresentação do produto já estão no ar.",
    subtasks: ["Landing publicada", "Login web ativo", "Cadastro web ativo", "Navegação principal criada"],
  },
  {
    status: "Disponível",
    state: "done" as const,
    title: "Agente",
    text: "O agente com fluxo de backup e restauração já faz parte da base atual da plataforma.",
    subtasks: ["Autenticação com backend", "Execução de backup", "Fluxo de restauração", "Modo daemon/headless"],
  },
  {
    status: "Em evolução",
    state: "progress" as const,
    title: "CDP",
    text: "Continuous Data Protection ainda é uma frente em aberto e faz parte das próximas evoluções importantes.",
    subtasks: ["Definir modelo de captura contínua", "Reduzir janela entre alterações e envio", "Validar impacto operacional"],
  },
  {
    status: "Em evolução",
    state: "progress" as const,
    title: "Criptografia",
    text: "A camada de encriptação dedicada ainda está no roadmap e não deve ser tratada como entrega concluída agora.",
    subtasks: ["Definir estratégia de encriptação", "Aplicar no pipeline de backup", "Validar restore com dados protegidos"],
  },
  {
    status: "Planejado",
    state: "todo" as const,
    title: "Mobile",
    text: "A presença mobile ainda está prevista como etapa futura e não entra como suporte atual do produto.",
    subtasks: ["Definir escopo mobile", "Avaliar leitura de status e alertas", "Planejar acesso ao painel em tela pequena"],
  },
  {
    status: "Planejado",
    state: "todo" as const,
    title: "Download",
    text: "A distribuição pública e organizada de downloads terá uma página própria, mas ainda está em fase de preparação.",
    subtasks: ["Organizar pacotes", "Definir instruções por ambiente", "Publicar acesso centralizado"],
  },
];

const done = ROADMAP.filter((i) => i.state === "done");
const inProgress = ROADMAP.filter((i) => i.state === "progress");
const todo = ROADMAP.filter((i) => i.state === "todo");

const COLUMNS = [
  { title: "Disponível", items: done, dot: "bg-[#10B981]", badge: "bg-[#10B981]/15 text-[#10B981]", ring: "border-[#10B981]/40" },
  { title: "Em evolução", items: inProgress, dot: "bg-[#7B61FF]", badge: "bg-[#7B61FF]/15 text-[#A78BFA]", ring: "border-[#7B61FF]/40" },
  { title: "Planejado", items: todo, dot: "bg-slate-600", badge: "bg-white/5 text-slate-500", ring: "border-white/10" },
];

export default function RoadmapPage() {
  return (
    <main className="min-h-screen bg-[#0D0C1A]">
      <PublicNav active="/roadmap" />

      {/* page header */}
      <div className="border-b border-white/10 px-6 py-8 lg:px-8">
        <div className="mx-auto max-w-7xl">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-widest text-[#7B61FF]">Roadmap</p>
              <h1 className="mt-1.5 text-xl font-black text-white">O que já existe e o que ainda está em construção.</h1>
              <p className="mt-1.5 text-sm text-slate-500">
                Transparência sobre o estado do projeto. Atualizado conforme o desenvolvimento avança.
              </p>
            </div>
            <a
              href={`${GITHUB_URL}/issues`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex h-9 cursor-pointer shrink-0 items-center gap-2 rounded-lg border border-white/15 bg-white/5 px-4 text-xs font-semibold text-slate-300 transition hover:bg-white/10 hover:text-white"
            >
              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              Sugerir item
            </a>
          </div>

          {/* summary bar */}
          <div className="mt-6 flex flex-wrap gap-3">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-[#10B981]/30 bg-[#10B981]/10 px-3 py-1 text-xs font-semibold text-[#10B981]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#10B981]" aria-hidden />
              {done.length} disponível
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-[#7B61FF]/30 bg-[#7B61FF]/10 px-3 py-1 text-xs font-semibold text-[#A78BFA]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#7B61FF]" aria-hidden />
              {inProgress.length} em evolução
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-500">
              <span className="h-1.5 w-1.5 rounded-full bg-slate-600" aria-hidden />
              {todo.length} planejado
            </span>
          </div>
        </div>
      </div>

      {/* kanban columns */}
      <div className="px-6 py-8 lg:px-8">
        <div className="mx-auto max-w-7xl">
          <div className="grid gap-5 lg:grid-cols-3">
            {COLUMNS.map((col) => (
              <div key={col.title}>
                {/* column header */}
                <div className="mb-4 flex items-center gap-2.5">
                  <span className={`h-2 w-2 rounded-full ${col.dot}`} aria-hidden />
                  <span className="text-xs font-bold uppercase tracking-widest text-slate-400">
                    {col.title}
                  </span>
                  <span className="ml-auto rounded-full bg-white/5 px-2 py-0.5 text-[10px] font-bold text-slate-600">
                    {col.items.length}
                  </span>
                </div>

                {/* cards */}
                <div className="space-y-3">
                  {col.items.map((item) => (
                    <article
                      key={item.title}
                      className={`rounded-xl border bg-[#100F1E] p-5 transition-colors hover:bg-[#13122A] ${col.ring}`}
                    >
                      <div className="mb-3 flex items-center justify-between gap-2">
                        <h2 className="text-sm font-black text-white">{item.title}</h2>
                        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-widest ${col.badge}`}>
                          {item.status}
                        </span>
                      </div>

                      <p className="text-xs leading-[1.7] text-slate-400">{item.text}</p>

                      <ul className="mt-4 space-y-1.5 border-t border-white/5 pt-4">
                        {item.subtasks.map((subtask) => (
                          <li key={subtask} className="flex items-center gap-2 text-[11px] text-slate-500">
                            {item.state === "done" ? (
                              <svg className="h-3 w-3 shrink-0 text-[#10B981]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden>
                                <polyline points="20 6 9 17 4 12" />
                              </svg>
                            ) : (
                              <span className={`h-1 w-1 shrink-0 rounded-full ${col.dot}`} aria-hidden />
                            )}
                            {subtask}
                          </li>
                        ))}
                      </ul>
                    </article>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
