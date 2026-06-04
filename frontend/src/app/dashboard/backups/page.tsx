"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Fragment, useEffect, useMemo, useState } from "react";
import { api, type Device, type PagedResponse, type Snapshot } from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

export default function BackupsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [devices, setDevices] = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<{ id: string; message: string } | null>(null);
  const [deletingSnapshotId, setDeletingSnapshotId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [openFilter, setOpenFilter] = useState<"device" | "status" | null>(null);
  const deviceFilter = searchParams.get("device") ?? "all";

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
        setError(e instanceof Error ? e.message : "Falha ao carregar backups.");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(() => {
    return snapshots
      .filter((s) => (deviceFilter === "all" ? true : s.deviceId === deviceFilter))
      .filter((s) => {
        if (statusFilter === "all") return true;
        if (statusFilter === "RUNNING") return s.status === "RUNNING" || s.status === "IN_PROGRESS" || s.status === "PROCESSING";
        return s.status === statusFilter;
      })
      .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
  }, [snapshots, deviceFilter, statusFilter]);

  const deviceName = (id: string) => {
    const d = devices.find((x) => x.id === id);
    return d?.name || d?.hostname || "—";
  };

  const deleteSnapshot = async (snapshot: Snapshot) => {
    if (!window.confirm("Apagar este snapshot? Esta ação remove o backup do histórico.")) return;
    setDeleteError(null);
    setDeletingSnapshotId(snapshot.id);
    try {
      await api<void>(`/api/snapshots/${snapshot.id}`, { method: "DELETE" });
      setSnapshots((current) => current.filter((item) => item.id !== snapshot.id));
    } catch (e) {
      setDeleteError({
        id: snapshot.id,
        message: e instanceof Error ? e.message : "Falha ao apagar snapshot.",
      });
    } finally {
      setDeletingSnapshotId(null);
    }
  };

  const selectCls =
    "w-full rounded-lg border bg-[#0D0C1A] px-3 py-2 text-sm text-slate-300 focus:outline-none focus:ring-2 focus:ring-[#7B61FF] transition-shadow";

  return (
    <>
      <Topbar title="Backups" />
      <div className="p-6">
        {error && (
          <div
            className="mb-4 rounded-xl border px-4 py-3 text-sm text-[#EF4444]"
            style={{ background: "rgba(239,68,68,0.08)", borderColor: "rgba(239,68,68,0.2)" }}
          >
            {error}
          </div>
        )}

        <div className="rounded-xl border bg-[#100F1E] overflow-hidden" style={{ borderColor: "rgba(255,255,255,0.08)" }}>
          {loading ? (
            <p className="px-6 py-10 text-sm text-slate-500">Carregando…</p>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-14">
              <div
                className="grid h-12 w-12 place-items-center rounded-2xl"
                style={{ background: "rgba(123,97,255,0.15)" }}
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" />
                </svg>
              </div>
              <p className="text-sm text-center text-slate-500">Nenhum backup encontrado com esses filtros.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[900px] w-full border-collapse text-sm">
                <thead>
                  <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">
                      <div className="relative">
                        <div className="flex items-center gap-2">
                          <span>Máquina</span>
                          <FilterButton
                            active={deviceFilter !== "all"}
                            onClick={() => setOpenFilter((c) => (c === "device" ? null : "device"))}
                          />
                        </div>
                        {openFilter === "device" && (
                          <div
                            className="absolute left-0 top-full z-10 mt-2 w-[210px] rounded-xl border p-2 shadow-xl"
                            style={{ background: "#100F1E", borderColor: "rgba(255,255,255,0.12)" }}
                          >
                            <select
                              value={deviceFilter}
                              onChange={(e) => {
                                const value = e.target.value;
                                router.replace(value === "all" ? "/dashboard/backups" : `/dashboard/backups?device=${encodeURIComponent(value)}`);
                                setOpenFilter(null);
                              }}
                              className={selectCls}
                              style={{ borderColor: "rgba(255,255,255,0.1)" }}
                            >
                              <option value="all">Todas as máquinas</option>
                              {devices.map((d) => (
                                <option key={d.id} value={d.id}>{d.name || d.hostname}</option>
                              ))}
                            </select>
                          </div>
                        )}
                      </div>
                    </th>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">
                      Origem
                    </th>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">
                      <div className="relative">
                        <div className="flex items-center gap-2">
                          <span>Status</span>
                          <FilterButton
                            active={statusFilter !== "all"}
                            onClick={() => setOpenFilter((c) => (c === "status" ? null : "status"))}
                          />
                        </div>
                        {openFilter === "status" && (
                          <div
                            className="absolute left-0 top-full z-10 mt-2 w-[190px] rounded-xl border p-2 shadow-xl"
                            style={{ background: "#100F1E", borderColor: "rgba(255,255,255,0.12)" }}
                          >
                            <select
                              value={statusFilter}
                              onChange={(e) => { setStatusFilter(e.target.value); setOpenFilter(null); }}
                              className={selectCls}
                              style={{ borderColor: "rgba(255,255,255,0.1)" }}
                            >
                              <option value="all">Todos os status</option>
                              <option value="COMPLETED">Concluído</option>
                              <option value="RUNNING">Em execução</option>
                              <option value="FAILED">Falhou</option>
                            </select>
                          </div>
                        )}
                      </div>
                    </th>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">Arquivos</th>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">Tamanho</th>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">Início</th>
                    <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5">
                      {loading ? "Carregando…" : `${filtered.length} backup${filtered.length !== 1 ? "s" : ""}`}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((s, idx) => (
                    <Fragment key={s.id}>
                      <tr
                        className="transition-colors duration-200"
                        style={{
                          borderTop: "1px solid rgba(255,255,255,0.04)",
                          background: idx === 0 ? "rgba(123,97,255,0.06)" : "transparent",
                        }}
                        onMouseEnter={(e) => { if (idx !== 0) (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.02)"; }}
                        onMouseLeave={(e) => { if (idx !== 0) (e.currentTarget as HTMLElement).style.background = "transparent"; }}
                      >
                        <td className="px-5 py-3.5 text-sm font-medium text-white">{deviceName(s.deviceId)}</td>
                        <td className="px-5 py-3.5 text-xs text-slate-400">
                          <span className="block max-w-[240px] truncate" title={s.sourcePath}>{s.sourcePath}</span>
                        </td>
                        <td className="px-5 py-3.5">
                          <StatusPill status={s.status} />
                        </td>
                        <td className="px-5 py-3.5 text-xs text-slate-400 tabular-nums">{s.totalFiles ?? 0}</td>
                        <td className="px-5 py-3.5 text-xs text-slate-400 tabular-nums">{formatBytes(s.totalCompressedSize ?? 0)}</td>
                        <td className="px-5 py-3.5 text-xs text-slate-400">{formatDateTime(s.startedAt)}</td>
                        <td className="px-5 py-3.5 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Link
                              href={`/dashboard/backups/${s.id}`}
                              className="rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors duration-200 hover:opacity-80 cursor-pointer"
                              style={{ border: "1px solid rgba(123,97,255,0.3)", color: "#A78BFA", background: "rgba(123,97,255,0.08)" }}
                            >
                              Abrir
                            </Link>
                            <button
                              type="button"
                              onClick={() => deleteSnapshot(s)}
                              disabled={deletingSnapshotId === s.id}
                              aria-label="Apagar snapshot"
                              title="Apagar snapshot"
                              className="grid h-7 w-7 place-items-center rounded-lg text-base font-semibold transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                              style={{ border: "1px solid rgba(239,68,68,0.3)", color: "#EF4444", background: "rgba(239,68,68,0.08)" }}
                            >
                              ×
                            </button>
                          </div>
                        </td>
                      </tr>
                      {deleteError?.id === s.id && (
                        <tr>
                          <td colSpan={7} className="px-5 pb-3 text-xs text-[#EF4444]">
                            {deleteError.message}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function FilterButton({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="grid h-5 w-5 place-items-center rounded transition-colors duration-200 cursor-pointer"
      style={{
        borderColor: active ? "rgba(123,97,255,0.4)" : "rgba(255,255,255,0.12)",
        border: "1px solid",
        background: active ? "rgba(123,97,255,0.15)" : "transparent",
        color: active ? "#A78BFA" : "#64748B",
      }}
      aria-label="Filtrar"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="h-2.5 w-2.5">
        <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
      </svg>
    </button>
  );
}

function StatusPill({ status }: { status: Snapshot["status"] }) {
  const running = { label: "Em execução", bg: "rgba(123,97,255,0.15)", text: "#A78BFA" };
  const map: Record<Snapshot["status"], { label: string; bg: string; text: string }> = {
    COMPLETED: { label: "Concluído", bg: "rgba(16,185,129,0.15)", text: "#10B981" },
    RUNNING: running,
    IN_PROGRESS: running,
    PROCESSING: { label: "Processando", bg: "rgba(123,97,255,0.15)", text: "#A78BFA" },
    FAILED: { label: "Falhou", bg: "rgba(239,68,68,0.15)", text: "#EF4444" },
  };
  const m = map[status] ?? map.FAILED;
  return (
    <span
      className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase"
      style={{ background: m.bg, color: m.text }}
    >
      {m.label}
    </span>
  );
}
