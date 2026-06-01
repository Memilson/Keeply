"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, type Device, type Snapshot } from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

export default function MachinesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [openActionId, setOpenActionId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [d, s] = await Promise.all([api<Device[]>("/api/devices"), api<Snapshot[]>("/api/snapshots")]);
        const list = d ?? [];
        setDevices(list);
        setSnapshots(s ?? []);
        if (list.length > 0) setSelectedDeviceId(list[0].id);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar máquinas.");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const snapshotsByDevice = useMemo(() => {
    const map = new Map<string, Snapshot[]>();
    for (const s of snapshots) {
      const arr = map.get(s.deviceId) ?? [];
      arr.push(s);
      map.set(s.deviceId, arr);
    }
    for (const [, arr] of map) {
      arr.sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }
    return map;
  }, [snapshots]);

  const selectedSnapshots = selectedDeviceId ? snapshotsByDevice.get(selectedDeviceId) ?? [] : [];

  return (
    <>
      <Topbar title="Máquinas" subtitle="Snapshots por máquina" />
      <div className="p-7">
        {error && (
          <div className="mb-5 rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        <div className="grid gap-5 lg:grid-cols-[280px_minmax(0,1fr)]">
          <aside className="kp-card overflow-hidden">
            <div className="px-4 py-3 text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993", borderBottom: "1px solid #F0EEF8", background: "#FAFAFE" }}>
              Armazenamentos
            </div>
            {loading ? (
              <p className="px-4 py-6 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
            ) : devices.length === 0 ? (
              <p className="px-4 py-6 text-sm" style={{ color: "#6B6993" }}>Nenhuma máquina cadastrada.</p>
            ) : (
              <div className="p-2">
                {devices.map((d) => {
                  const active = selectedDeviceId === d.id;
                  const list = snapshotsByDevice.get(d.id) ?? [];
                  const totalBytes = list.reduce((acc, s) => acc + (s.totalCompressedSize ?? 0), 0);
                  return (
                    <button
                      key={d.id}
                      onClick={() => setSelectedDeviceId(d.id)}
                      className="mb-1.5 w-full rounded-lg px-3 py-2.5 text-left"
                      style={active ? { background: "#EDE9FF" } : { background: "transparent" }}
                    >
                      <p className="truncate text-sm font-semibold" style={{ color: active ? "#6046F0" : "#18163A" }}>{d.name || d.hostname}</p>
                      <p className="mt-0.5 text-xs" style={{ color: "#6B6993" }}>
                        {list.length} snapshot{list.length !== 1 ? "s" : ""} · {formatBytes(totalBytes)}
                      </p>
                    </button>
                  );
                })}
              </div>
            )}
          </aside>

          <section className="kp-card overflow-visible">
            <div className="flex items-center justify-between px-5 py-3" style={{ borderBottom: "1px solid #F0EEF8", background: "#FAFAFE" }}>
              <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>
                {selectedDeviceId ? "Snapshots da máquina" : "Snapshots"}
              </p>
              <span className="text-xs font-medium" style={{ color: "#6B6993" }}>
                {selectedSnapshots.length} item{selectedSnapshots.length !== 1 ? "ns" : ""}
              </span>
            </div>

            {!selectedDeviceId ? (
              <p className="px-5 py-10 text-sm" style={{ color: "#6B6993" }}>Selecione uma máquina na barra lateral.</p>
            ) : selectedSnapshots.length === 0 ? (
              <p className="px-5 py-10 text-sm" style={{ color: "#6B6993" }}>Sem snapshots para essa máquina.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr style={{ borderBottom: "1px solid #F0EEF8" }}>
                    {["Origem", "Status", "Arquivos", "Tamanho", "Início", ""].map((h) => (
                      <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {selectedSnapshots.map((s, idx) => (
                    <tr key={s.id} style={{ borderTop: idx > 0 ? "1px solid #F5F3FC" : undefined }}>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>
                        <span className="block max-w-[280px] truncate" title={s.sourcePath}>{s.sourcePath}</span>
                      </td>
                      <td className="px-5 py-3.5"><StatusPill status={s.status} /></td>
                      <td className="px-5 py-3.5 tabular-nums" style={{ color: "#6B6993" }}>{s.totalFiles ?? 0}</td>
                      <td className="px-5 py-3.5 tabular-nums" style={{ color: "#6B6993" }}>{formatBytes(s.totalCompressedSize ?? 0)}</td>
                      <td className="px-5 py-3.5" style={{ color: "#6B6993" }}>{formatDateTime(s.startedAt)}</td>
                      <td className="relative px-5 py-3.5 text-right">
                        <button
                          type="button"
                          onClick={() => setOpenActionId((current) => (current === s.id ? null : s.id))}
                          className="inline-grid h-8 w-8 place-items-center rounded-lg transition-colors hover:bg-[#F5F3FB]"
                          style={{ border: "1px solid #E4E1F0", color: "#6B6993", background: "#FFFFFF" }}
                          aria-label="Abrir ações"
                        >
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                            <circle cx="5" cy="12" r="2" />
                            <circle cx="12" cy="12" r="2" />
                            <circle cx="19" cy="12" r="2" />
                          </svg>
                        </button>

                        {openActionId === s.id && (
                          <div
                            className="absolute right-5 top-12 z-10 w-44 overflow-hidden rounded-xl bg-white py-1 text-left shadow-lg"
                            style={{ border: "1px solid #E4E1F0" }}
                          >
                            <Link
                              href={`/dashboard/backups/${s.id}`}
                              onClick={() => setOpenActionId(null)}
                              className="block px-4 py-2.5 text-sm transition-colors hover:bg-[#F5F3FB]"
                              style={{ color: "#18163A" }}
                            >
                              Abrir snapshots
                            </Link>
                            <Link
                              href="/dashboard/protection"
                              onClick={() => setOpenActionId(null)}
                              className="block px-4 py-2.5 text-sm transition-colors hover:bg-[#F5F3FB]"
                              style={{ color: "#18163A" }}
                            >
                              Configurar plano
                            </Link>
                            <button
                              type="button"
                              onClick={() => setOpenActionId(null)}
                              className="block w-full px-4 py-2.5 text-left text-sm transition-colors hover:bg-red-50"
                              style={{ color: "#DC2626" }}
                              title="Exclusão de máquina ainda não está disponível na API."
                            >
                              Excluir
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      </div>
    </>
  );
}

function StatusPill({ status }: { status: Snapshot["status"] }) {
  const running = { label: "Em execução", dot: "#7B61FF", bg: "#EDE9FF", text: "#6046F0" };
  const map: Record<Snapshot["status"], { label: string; dot: string; bg: string; text: string }> = {
    COMPLETED: { label: "Concluído", dot: "#10B981", bg: "#ECFDF5", text: "#059669" },
    RUNNING: running,
    IN_PROGRESS: running,
    PROCESSING: running,
    FAILED: { label: "Falhou", dot: "#EF4444", bg: "#FEF2F2", text: "#DC2626" },
  };
  const m = map[status] ?? map.FAILED;
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium" style={{ background: m.bg, color: m.text }}>
      <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: m.dot }} />
      {m.label}
    </span>
  );
}
