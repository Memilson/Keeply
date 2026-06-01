"use client";

import { useEffect, useMemo, useState } from "react";
import { api, type Device } from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

export default function MachinesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const data = await api<Device[]>("/api/devices");
        setDevices(data ?? []);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar máquinas.");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(
    () =>
      devices.filter((d) => {
        if (!query) return true;
        const q = query.toLowerCase();
        return d.name?.toLowerCase().includes(q) || d.hostname?.toLowerCase().includes(q) || d.osName?.toLowerCase().includes(q);
      }),
    [devices, query]
  );

  function isHealthy(d: Device) {
    return d.lastSeenAt && Date.now() - new Date(d.lastSeenAt).getTime() < 24 * 3600 * 1000;
  }

  return (
    <>
      <Topbar title="Máquinas" subtitle="Todos os endpoints protegidos pelo agente Keeply" />
      <div className="space-y-5 p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 rounded-lg border bg-white px-3 py-2" style={{ borderColor: "#E4E1F0" }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#6B6993" strokeWidth="2">
              <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
            </svg>
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por nome, hostname ou SO…"
              className="w-64 bg-transparent text-sm focus:outline-none"
              style={{ color: "#18163A" }}
            />
          </div>
          <span className="text-xs font-medium" style={{ color: "#6B6993" }}>
            {loading ? "Carregando…" : `${filtered.length} máquina${filtered.length !== 1 ? "s" : ""}`}
          </span>
        </div>

        {error && (
          <div className="rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        <div className="kp-card overflow-hidden">
          {loading ? (
            <p className="px-6 py-10 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-14">
              <div className="grid h-12 w-12 place-items-center rounded-2xl" style={{ background: "#EDE9FF" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" />
                </svg>
              </div>
              <p className="text-sm text-center" style={{ color: "#6B6993" }}>
                {query ? "Nenhuma máquina encontrada para essa busca." : "Nenhuma máquina cadastrada. Instale o agente Keeply para começar."}
              </p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr style={{ borderBottom: "1px solid #F0EEF8" }}>
                  {["Máquina", "Sistema operacional", "Versão agente", "Status", "Último contato"].map((h) => (
                    <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((d, idx) => {
                  const healthy = isHealthy(d);
                  return (
                    <tr
                      key={d.id}
                      className="transition-colors hover:bg-gray-50/60"
                      style={{ borderTop: idx > 0 ? "1px solid #F5F3FC" : undefined }}
                    >
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-3">
                          <span
                            className="grid h-9 w-9 shrink-0 place-items-center rounded-xl text-xs font-bold text-white"
                            style={{ background: "#7B61FF" }}
                          >
                            {(d.name || d.hostname || "K").slice(0, 2).toUpperCase()}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium" style={{ color: "#18163A" }}>{d.name || "—"}</p>
                            <p className="truncate text-xs" style={{ color: "#6B6993" }}>{d.hostname}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>{d.osName ?? "—"}</td>
                      <td className="px-5 py-3.5 font-mono text-xs" style={{ color: "#6B6993" }}>{d.agentVersion ?? "—"}</td>
                      <td className="px-5 py-3.5">
                        <span
                          className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
                          style={{
                            background: healthy ? "#ECFDF5" : "#FEF3C7",
                            color: healthy ? "#059669" : "#D97706",
                          }}
                        >
                          <span
                            className="h-1.5 w-1.5 rounded-full inline-block"
                            style={{ background: healthy ? "#10B981" : "#F59E0B" }}
                          />
                          {healthy ? "Online" : "Offline"}
                        </span>
                      </td>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>{formatRelative(d.lastSeenAt)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
