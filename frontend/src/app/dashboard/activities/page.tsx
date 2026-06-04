"use client";

import { useEffect, useMemo, useState } from "react";
import { api, type Device, type PagedResponse, type Snapshot, type SnapshotStatus } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

type ActivityFilter = "ALL" | "BACKUP" | "RUNNING" | "ERRORS";

export default function ActivitiesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<ActivityFilter>("ALL");

  useEffect(() => {
    (async () => {
      try {
        const [d, s] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<PagedResponse<Snapshot>>("/api/snapshots"),
        ]);
        setDevices(d ?? []);
        setSnapshots(s?.items ?? []);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar atividades.");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(() => {
    const sorted = [...snapshots].sort(
      (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
    );
    if (filter === "ALL" || filter === "BACKUP") return sorted;
    if (filter === "RUNNING") {
      return sorted.filter((s) => s.status === "RUNNING" || s.status === "IN_PROGRESS" || s.status === "PROCESSING");
    }
    return sorted.filter((s) => s.status === "FAILED");
  }, [snapshots, filter]);

  const deviceById = useMemo(() => {
    const map = new Map<string, Device>();
    for (const d of devices) map.set(d.id, d);
    return map;
  }, [devices]);

  return (
    <>
      <Topbar title="Atividades" />
      <div className="p-6">
        <div
          className="rounded-xl border bg-[#100F1E] overflow-hidden"
          style={{ borderColor: "rgba(255,255,255,0.08)" }}
        >
          {/* Filter pills */}
          <div
            className="flex flex-wrap items-center gap-2 px-5 py-4"
            style={{ borderBottom: "1px solid rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.02)" }}
          >
            {(["ALL", "BACKUP", "RUNNING", "ERRORS"] as ActivityFilter[]).map((f) => {
              const labels: Record<ActivityFilter, string> = {
                ALL: "Todos",
                BACKUP: "Backup",
                RUNNING: "Em andamento",
                ERRORS: "Erros",
              };
              return (
                <FilterPill
                  key={f}
                  label={labels[f]}
                  active={filter === f}
                  onClick={() => setFilter(f)}
                />
              );
            })}
          </div>

          {loading ? (
            <p className="px-5 py-8 text-sm text-slate-500">Carregando…</p>
          ) : error ? (
            <p className="px-5 py-8 text-sm text-[#EF4444]">{error}</p>
          ) : filtered.length === 0 ? (
            <p className="px-5 py-8 text-sm text-slate-500">
              Nenhuma atividade para o filtro selecionado.
            </p>
          ) : (
            <div className="max-h-[70vh] overflow-auto">
              <ul>
                {filtered.map((s, idx) => {
                  const status = statusView(s.status);
                  const device = deviceById.get(s.deviceId);
                  const devName = device?.name || device?.hostname || "Dispositivo";
                  return (
                    <li
                      key={s.id}
                      className="px-5 py-4"
                      style={{
                        borderTop: idx > 0 ? "1px solid rgba(255,255,255,0.04)" : undefined,
                      }}
                    >
                      <div className="flex items-start gap-3">
                        <span
                          className="mt-1.5 inline-block h-2.5 w-2.5 shrink-0 rounded-full"
                          style={{ background: status.dot }}
                        />
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-sm font-semibold" style={{ color: status.textColor }}>
                              {status.label}
                            </span>
                            <span
                              className="rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide"
                              style={{ background: "rgba(123,97,255,0.15)", color: "#A78BFA" }}
                            >
                              Backup
                            </span>
                          </div>
                          <p className="mt-1 text-sm text-slate-400">
                            <span className="font-medium text-white">{devName}</span>
                            {" "}&rarr;{" "}
                            <span className="font-mono text-xs text-slate-500">{s.sourcePath}</span>
                          </p>
                          <p className="mt-1 text-xs text-slate-600">
                            {formatDateTime(s.startedAt)}
                            {" · "}
                            <span className="font-mono">{s.id}</span>
                          </p>
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function FilterPill({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="rounded-lg border px-4 py-1.5 text-xs font-bold transition-colors duration-200 cursor-pointer"
      style={
        active
          ? { background: "rgba(123,97,255,0.15)", borderColor: "rgba(123,97,255,0.35)", color: "#A78BFA" }
          : { background: "transparent", borderColor: "rgba(255,255,255,0.1)", color: "#64748B" }
      }
    >
      {label}
    </button>
  );
}

function statusView(status: SnapshotStatus): { label: string; dot: string; textColor: string } {
  if (status === "COMPLETED") return { label: "Completado", dot: "#10B981", textColor: "#10B981" };
  if (status === "FAILED") return { label: "Erro", dot: "#EF4444", textColor: "#EF4444" };
  return { label: "Em andamento", dot: "#F59E0B", textColor: "#F59E0B" };
}
