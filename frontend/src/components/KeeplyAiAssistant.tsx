"use client";

import { FormEvent, useMemo, useRef, useState } from "react";
import { api, type AiChatMessage, type AiChatResponse } from "@/lib/api";

type UiMessage = AiChatMessage & { id: string };

const SUGGESTIONS = [
  "Como verifico se meus backups estão saudáveis?",
  "O que fazer quando uma máquina fica offline?",
  "Como funciona a restauração de um snapshot?",
];

export function KeeplyAiAssistant() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<UiMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      content: "Sou o Keeply I.A. Posso ajudar com backups, máquinas, snapshots, restauração e diagnóstico.",
    },
  ]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const history = useMemo<AiChatMessage[]>(
    () =>
      messages
        .filter((message) => message.id !== "welcome")
        .slice(-8)
        .map(({ role, content }) => ({ role, content })),
    [messages],
  );

  async function sendMessage(text: string) {
    const question = text.trim();
    if (!question || loading) return;

    const nextUserMessage: UiMessage = {
      id: `user-${Date.now()}`,
      role: "user",
      content: question,
    };

    setMessages((current) => [...current, nextUserMessage]);
    setDraft("");
    setLoading(true);
    setError(null);

    try {
      const response = await api<AiChatResponse>("/api/ai/chat", {
        method: "POST",
        body: JSON.stringify({ message: question, history }),
      });

      setMessages((current) => [
        ...current,
        {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          content: response.answer,
        },
      ]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha ao consultar o Keeply I.A.");
    } finally {
      setLoading(false);
      window.setTimeout(() => inputRef.current?.focus(), 0);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void sendMessage(draft);
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="inline-flex h-9 items-center gap-2 rounded-lg border px-3 text-xs font-semibold text-slate-100 transition-colors"
        style={{
          background: open ? "rgba(123,97,255,0.22)" : "rgba(255,255,255,0.045)",
          borderColor: open ? "rgba(167,139,250,0.5)" : "rgba(255,255,255,0.08)",
        }}
        aria-expanded={open}
        aria-label="Abrir Keeply I.A"
      >
        <i className="bi bi-stars text-[#A78BFA]" aria-hidden="true" />
        <span>Keeply I.A</span>
      </button>

      {open ? (
        <section
          className="absolute right-0 top-12 z-50 flex h-[min(620px,calc(100vh-88px))] w-[min(420px,calc(100vw-32px))] flex-col overflow-hidden rounded-lg border shadow-2xl"
          style={{
            background: "#09081A",
            borderColor: "rgba(167,139,250,0.22)",
            boxShadow: "0 24px 80px rgba(0,0,0,0.42)",
          }}
          aria-label="Painel Keeply I.A"
        >
          <div className="flex items-center justify-between border-b px-4 py-3" style={{ borderColor: "rgba(255,255,255,0.07)" }}>
            <div className="min-w-0">
              <h2 className="text-sm font-semibold text-slate-100">Keeply I.A</h2>
              <p className="mt-0.5 truncate text-[11px] text-slate-500">Nemotron 3 Super via OpenRouter</p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="grid h-8 w-8 place-items-center rounded-lg text-slate-500 transition-colors hover:bg-white/5 hover:text-slate-200"
              aria-label="Fechar Keeply I.A"
            >
              <i className="bi bi-x-lg text-sm" aria-hidden="true" />
            </button>
          </div>

          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-4">
            {messages.map((message) => (
              <div
                key={message.id}
                className={message.role === "user" ? "ml-auto max-w-[86%]" : "mr-auto max-w-[90%]"}
              >
                <div
                  className="whitespace-pre-wrap rounded-lg px-3 py-2 text-sm leading-6"
                  style={{
                    background: message.role === "user" ? "rgba(123,97,255,0.22)" : "rgba(255,255,255,0.055)",
                    color: message.role === "user" ? "#F8FAFC" : "#CBD5E1",
                    border: "1px solid rgba(255,255,255,0.07)",
                  }}
                >
                  {message.content}
                </div>
              </div>
            ))}

            {loading ? (
              <div className="mr-auto max-w-[90%] rounded-lg border px-3 py-2 text-sm text-slate-500" style={{ borderColor: "rgba(255,255,255,0.07)", background: "rgba(255,255,255,0.04)" }}>
                Pensando...
              </div>
            ) : null}
          </div>

          <div className="border-t p-3" style={{ borderColor: "rgba(255,255,255,0.07)" }}>
            {messages.length === 1 ? (
              <div className="mb-3 flex flex-wrap gap-2">
                {SUGGESTIONS.map((item) => (
                  <button
                    key={item}
                    type="button"
                    onClick={() => void sendMessage(item)}
                    className="rounded-lg border px-2.5 py-1.5 text-left text-[11px] text-slate-400 transition-colors hover:border-[#7B61FF]/50 hover:text-slate-100"
                    style={{ borderColor: "rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.035)" }}
                  >
                    {item}
                  </button>
                ))}
              </div>
            ) : null}

            {error ? (
              <p className="mb-2 rounded-lg border px-3 py-2 text-xs text-[#FCA5A5]" style={{ borderColor: "rgba(248,113,113,0.24)", background: "rgba(127,29,29,0.18)" }}>
                {error}
              </p>
            ) : null}

            <form onSubmit={submit} className="flex items-center gap-2">
              <input
                ref={inputRef}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-lg border bg-transparent px-3 text-sm text-slate-100 outline-none placeholder:text-slate-600 focus:border-[#7B61FF]"
                style={{ borderColor: "rgba(255,255,255,0.1)" }}
                placeholder="Pergunte sobre seus backups..."
                maxLength={4000}
              />
              <button
                type="submit"
                disabled={loading || !draft.trim()}
                className="grid h-10 w-10 place-items-center rounded-lg border text-slate-100 transition-opacity disabled:cursor-not-allowed disabled:opacity-45"
                style={{ background: "#7B61FF", borderColor: "rgba(255,255,255,0.1)" }}
                aria-label="Enviar mensagem"
              >
                <i className="bi bi-send-fill text-sm" aria-hidden="true" />
              </button>
            </form>
          </div>
        </section>
      ) : null}
    </div>
  );
}
