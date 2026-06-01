"use client";

import { useEffect, useMemo, useState } from "react";
import { api, type Device, type Snapshot, type SnapshotStatus } from "@/lib/api";
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
          api<Snapshot[]>("/api/snapshots"),
        ]);
        setDevices(d ?? []);
        setSnapshots(s ?? []);
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
      <Topbar title="Atividades" subtitle="Eventos de backup de todos os dispositivos" />
      <div className="space-y-5 p-7">
        <div className="kp-card overflow-hidden">
          <div className="flex flex-wrap items-center gap-2 px-5 py-4" style={{ borderBottom: "1px solid #F0EEF8", background: "#FAFAFE" }}>
            <FilterPill label="Todos" active={filter === "ALL"} onClick={() => setFilter("ALL")} />
            <FilterPill label="Backup" active={filter === "BACKUP"} onClick={() => setFilter("BACKUP")} />
            <FilterPill label="Em andamento" active={filter === "RUNNING"} onClick={() => setFilter("RUNNING")} />
            <FilterPill label="Erros" active={filter === "ERRORS"} onClick={() => setFilter("ERRORS")} />
          </div>

          {loading ? (
            <p className="px-5 py-8 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
          ) : error ? (
            <p className="px-5 py-8 text-sm" style={{ color: "#DC2626" }}>{error}</p>
          ) : filtered.length === 0 ? (
            <p className="px-5 py-8 text-sm" style={{ color: "#6B6993" }}>Nenhuma atividade para o filtro selecionado.</p>
          ) : (
            <div className="max-h-[70vh] overflow-auto">
              <ul>
                {filtered.map((s, idx) => {
                  const status = statusView(s.status);
                  const device = deviceById.get(s.deviceId);
                  const deviceName = device?.name || device?.hostname || "Dispositivo";
                  return (
                    <li key={s.id} className="px-5 py-4" style={{ borderTop: idx > 0 ? "1px solid #F5F3FC" : undefined }}>
                      <div className="flex items-start gap-3">
                        <span
                          className="mt-1 inline-block h-3 w-3 rounded-full"
                          style={{ background: status.dot }}
                        />
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-sm font-semibold" style={{ color: "#3B82F6" }}>{status.label}</span>
                            <span
                              className="rounded-full px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide"
                              style={{ background: "#EDE9FF", color: "#6046F0" }}
                            >
                              Backup
                            </span>
                          </div>
                          <p className="mt-1 text-sm" style={{ color: "#334155" }}>
                            device={deviceName} source={s.sourcePath}
                          </p>
                          <p className="mt-1 text-xs" style={{ color: "#6B6993" }}>
                            {formatDateTime(s.startedAt)} · snapshot_id={s.id}
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
      className="rounded-xl border px-4 py-1.5 text-sm font-semibold transition-colors"
      style={active ? { background: "#EDE9FF", borderColor: "#C7BFFB", color: "#4F46E5" } : { background: "#FFFFFF", borderColor: "#E2E8F0", color: "#475569" }}
    >
      {label}
    </button>
  );
}

function statusView(status: SnapshotStatus): { label: string; dot: string } {
  if (status === "COMPLETED") return { label: "completado", dot: "#84CC16" };
  if (status === "FAILED") return { label: "erro", dot: "#EF4444" };
  return { label: "em andamento", dot: "#84CC16" };
}
