"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
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
  const [deviceFilter, setDeviceFilter] = useState<string>(() => searchParams.get("device") ?? "all");
  const [statusFilter, setStatusFilter] = useState<string>("all");

  useEffect(() => {
    setDeviceFilter(searchParams.get("device") ?? "all");
  }, [searchParams]);

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

  const selectCls = "rounded-lg border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 transition-shadow";

  return (
    <>
      <Topbar title="Backups" subtitle="Histórico de snapshots de todas as máquinas" />
      <div className="space-y-5 p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2.5">
            <select
              value={deviceFilter}
              onChange={(e) => {
                const value = e.target.value;
                setDeviceFilter(value);
                router.replace(value === "all" ? "/dashboard/backups" : `/dashboard/backups?device=${encodeURIComponent(value)}`);
              }}
              className={selectCls}
              style={{ borderColor: "#E4E1F0", color: "#18163A" }}
            >
              <option value="all">Todas as máquinas</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.name || d.hostname}</option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className={selectCls}
              style={{ borderColor: "#E4E1F0", color: "#18163A" }}
            >
              <option value="all">Todos os status</option>
              <option value="COMPLETED">Concluído</option>
              <option value="RUNNING">Em execução</option>
              <option value="FAILED">Falhou</option>
            </select>
          </div>
          <span className="text-xs font-medium" style={{ color: "#6B6993" }}>
            {loading ? "Carregando…" : `${filtered.length} backup${filtered.length !== 1 ? "s" : ""}`}
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
                  <rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" />
                </svg>
              </div>
              <p className="text-sm text-center" style={{ color: "#6B6993" }}>Nenhum backup encontrado com esses filtros.</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr style={{ borderBottom: "1px solid #F0EEF8" }}>
                  {["Máquina", "Origem", "Status", "Arquivos", "Tamanho", "Início", ""].map((h) => (
                    <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993", background: "#FAFAFE" }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((s, idx) => (
                  <tr
                    key={s.id}
                    className="transition-colors hover:bg-gray-50/60"
                    style={{ borderTop: idx > 0 ? "1px solid #F5F3FC" : undefined }}
                  >
                    <td className="px-5 py-3.5 font-medium" style={{ color: "#18163A" }}>{deviceName(s.deviceId)}</td>
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
                      <Link
                        href={`/dashboard/backups/${s.id}`}
                        className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
                        style={{ border: "1px solid #E4E1F0", color: "#7B61FF", background: "#FAFAFE" }}
                      >
                        Abrir
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
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
