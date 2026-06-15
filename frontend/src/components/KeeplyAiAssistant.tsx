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
        className="inline-flex h-9 items-center gap-2 rounded-lg border px-3 text-xs font-semibold text-gray-700 transition-colors"
        style={{
          background: open ? "rgba(123,97,255,0.10)" : "#F3F4F6",
          borderColor: open ? "rgba(123,97,255,0.4)" : "#E5E7EB",
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
            background: "#FFFFFF",
            borderColor: "#E5E7EB",
            boxShadow: "0 8px 32px rgba(0,0,0,0.12), 0 2px 8px rgba(0,0,0,0.06)",
          }}
          aria-label="Painel Keeply I.A"
        >
          <div className="flex items-center justify-between border-b px-4 py-3" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
            <div className="min-w-0">
              <h2 className="text-sm font-semibold text-gray-900">Keeply I.A</h2>
              <p className="mt-0.5 truncate text-[11px] text-gray-400">Nemotron 3 Super via OpenRouter</p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="grid h-8 w-8 place-items-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700"
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
                    background: message.role === "user" ? "rgba(123,97,255,0.10)" : "#F3F4F6",
                    color: message.role === "user" ? "#4C1D95" : "#111827",
                    border: message.role === "user" ? "1px solid rgba(123,97,255,0.2)" : "1px solid #E5E7EB",
                  }}
                >
                  {message.content}
                </div>
              </div>
            ))}

            {loading ? (
              <div className="mr-auto max-w-[90%] rounded-lg border px-3 py-2 text-sm text-gray-400" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
                Pensando...
              </div>
            ) : null}
          </div>

          <div className="border-t p-3" style={{ borderColor: "#E5E7EB" }}>
            {messages.length === 1 ? (
              <div className="mb-3 flex flex-wrap gap-2">
                {SUGGESTIONS.map((item) => (
                  <button
                    key={item}
                    type="button"
                    onClick={() => void sendMessage(item)}
                    className="rounded-lg border px-2.5 py-1.5 text-left text-[11px] text-gray-500 transition-colors hover:border-[#7B61FF]/50 hover:text-gray-900"
                    style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}
                  >
                    {item}
                  </button>
                ))}
              </div>
            ) : null}

            {error ? (
              <p className="mb-2 rounded-lg border px-3 py-2 text-xs text-[#DC2626]" style={{ borderColor: "#FECACA", background: "#FEF2F2" }}>
                {error}
              </p>
            ) : null}

            <form onSubmit={submit} className="flex items-center gap-2">
              <input
                ref={inputRef}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-lg border bg-white px-3 text-sm text-gray-700 outline-none placeholder:text-gray-400 focus:border-[#7B61FF]"
                style={{ borderColor: "#E5E7EB" }}
                placeholder="Pergunte sobre seus backups..."
                maxLength={4000}
              />
              <button
                type="submit"
                disabled={loading || !draft.trim()}
                className="grid h-10 w-10 place-items-center rounded-lg text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-45"
                style={{ background: "#7B61FF" }}
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
