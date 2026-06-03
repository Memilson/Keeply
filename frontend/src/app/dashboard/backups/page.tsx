"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Fragment, useEffect, useMemo, useState } from "react";
import { api, type Device, type Snapshot } from "@/lib/api";
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
          api<Snapshot[]>("/api/snapshots"),
        ]);
        setDevices(d ?? []);
        setSnapshots(s ?? []);
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

  const selectCls = "w-full rounded-lg border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 transition-shadow";

  return (
    <>
      <Topbar />
      <div className="dashboard-page" style={{ padding: "28px 0 0" }}>
        {error && (
          <div className="ml-0 mr-7 rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        <div className="overflow-hidden pl-0 pr-7">
          {loading ? (
            <p className="px-6 py-10 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-14">
              <div className="grid h-12 w-12 place-items-center rounded-2xl" style={{ background: "#EDE9FF" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" />
                </svg>
              </div>
              <p className="text-sm text-center" style={{ color: "#6B6993" }}>Nenhum backup encontrado com esses filtros.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
            <table className="min-w-[980px] w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    <div className="relative">
                      <div className="flex items-center gap-2">
                        <span className="block">Máquina</span>
                        <FilterButton
                          active={deviceFilter !== "all"}
                          onClick={() => setOpenFilter((current) => (current === "device" ? null : "device"))}
                        />
                      </div>
                      {openFilter === "device" && (
                        <div className="absolute left-0 top-full z-10 mt-2 w-[210px] rounded-xl border bg-white p-2 shadow-lg" style={{ borderColor: "#E4E1F0" }}>
                          <select
                            value={deviceFilter}
                            onChange={(e) => {
                              const value = e.target.value;
                              router.replace(value === "all" ? "/dashboard/backups" : `/dashboard/backups?device=${encodeURIComponent(value)}`);
                              setOpenFilter(null);
                            }}
                            className={selectCls}
                            style={{ borderColor: "#E4E1F0", color: "#18163A" }}
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
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    Origem
                  </th>
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    <div className="relative">
                      <div className="flex items-center gap-2">
                        <span className="block">Status</span>
                        <FilterButton
                          active={statusFilter !== "all"}
                          onClick={() => setOpenFilter((current) => (current === "status" ? null : "status"))}
                        />
                      </div>
                      {openFilter === "status" && (
                        <div className="absolute left-0 top-full z-10 mt-2 w-[190px] rounded-xl border bg-white p-2 shadow-lg" style={{ borderColor: "#E4E1F0" }}>
                          <select
                            value={statusFilter}
                            onChange={(e) => {
                              setStatusFilter(e.target.value);
                              setOpenFilter(null);
                            }}
                            className={selectCls}
                            style={{ borderColor: "#E4E1F0", color: "#18163A" }}
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
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    Arquivos
                  </th>
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    Tamanho
                  </th>
                  <th className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    Início
                  </th>
                  <th className="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                    {loading ? "Carregando…" : `${filtered.length} backup${filtered.length !== 1 ? "s" : ""}`}
                  </th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((s, idx) => (
                  <Fragment key={s.id}>
                    <tr
                      className="transition-colors hover:bg-[#F7F5FF]"
                      style={{
                        borderTop: idx > 0 ? "1px solid #F0EEF8" : "1px solid #F0EEF8",
                        background: idx === 0 ? "#F3F0FF" : "#FFFFFF",
                      }}
                    >
                      <td className="px-5 py-4 font-medium" style={{ color: "#18163A" }}>{deviceName(s.deviceId)}</td>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>
                        <span className="block max-w-[240px] truncate" title={s.sourcePath}>{s.sourcePath}</span>
                      </td>
                      <td className="px-5 py-3.5">
                        <StatusPill status={s.status} />
                      </td>
                      <td className="px-5 py-3.5 tabular-nums" style={{ color: "#6B6993" }}>{s.totalFiles ?? 0}</td>
                      <td className="px-5 py-3.5 tabular-nums" style={{ color: "#6B6993" }}>{formatBytes(s.totalCompressedSize ?? 0)}</td>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>{formatDateTime(s.startedAt)}</td>
                      <td className="px-5 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            href={`/dashboard/backups/${s.id}`}
                            className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
                            style={{ border: "1px solid #E4E1F0", color: "#7B61FF", background: "#FAFAFE" }}
                          >
                            Abrir
                          </Link>
                          <button
                            type="button"
                            onClick={() => deleteSnapshot(s)}
                            disabled={deletingSnapshotId === s.id}
                            aria-label="Apagar snapshot"
                            title="Apagar snapshot"
                            className="grid h-7 w-7 place-items-center rounded-lg text-base font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                            style={{ border: "1px solid #FECACA", color: "#DC2626", background: "#FEF2F2" }}
                          >
                            ×
                          </button>
                        </div>
                      </td>
                    </tr>
                      {deleteError?.id === s.id && (
                      <tr>
                        <td colSpan={7} className="px-5 pb-3 text-xs" style={{ color: "#DC2626" }}>
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
      className="grid h-6 w-6 place-items-center rounded-md border transition-colors"
      style={{
        borderColor: active ? "#C7BFFB" : "#E4E1F0",
        background: active ? "#EDE9FF" : "#FFFFFF",
        color: active ? "#6046F0" : "#6B6993",
      }}
      aria-label="Filtrar"
    >
      <i className="bi bi-funnel text-[11px]" aria-hidden="true" />
    </button>
  );
}

function StatusPill({ status }: { status: Snapshot["status"] }) {
  const running = { label: "Em execução", dot: "#7B61FF", bg: "#EDE9FF", text: "#6046F0" };
  const map: Record<Snapshot["status"], { label: string; dot: string; bg: string; text: string }> = {
    COMPLETED:   { label: "Concluído", dot: "#10B981", bg: "#ECFDF5", text: "#059669" },
    RUNNING:     running,
    IN_PROGRESS: running,
    PROCESSING:  running,
    FAILED:      { label: "Falhou", dot: "#EF4444", bg: "#FEF2F2", text: "#DC2626" },
  };
  const m = map[status] ?? map.FAILED;
  return (
    <span
      className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium"
      style={{ background: m.bg, color: m.text }}
    >
      <span className="h-1.5 w-1.5 rounded-full inline-block" style={{ background: m.dot }} />
      {m.label}
    </span>
  );
}
