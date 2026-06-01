"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, type Device, type Snapshot, API_BASE } from "@/lib/api";
import { formatBytes, formatRelative } from "@/lib/format";
import { Topbar } from "@/components/Topbar";
import { Sparkline, DonutGauge } from "@/components/Sparkline";

export default function DashboardOverview() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [d, s] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<Snapshot[]>("/api/snapshots"),
        ]);
        if (cancelled) return;
        setDevices(d ?? []);
        setSnapshots(s ?? []);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Falha ao carregar dados.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const completed = snapshots.filter((s) => s.status === "COMPLETED");
  const running = snapshots.filter((s) => s.status === "RUNNING" || s.status === "IN_PROGRESS" || s.status === "PROCESSING");
  const failed = snapshots.filter((s) => s.status === "FAILED");

  const stats = useMemo(() => {
    const totalOriginal = completed.reduce((acc, s) => acc + (s.totalOriginalSize ?? 0), 0);
    const totalCompressed = completed.reduce((acc, s) => acc + (s.totalCompressedSize ?? 0), 0);
    const compression = totalOriginal > 0 ? totalOriginal / Math.max(totalCompressed, 1) : 0;
    const healthy = devices.filter(
      (d) => d.lastSeenAt && Date.now() - new Date(d.lastSeenAt).getTime() < 24 * 3600 * 1000
    ).length;
    const successRate = snapshots.length > 0 ? Math.round((completed.length / snapshots.length) * 100) : 100;
    return { devices: devices.length, healthy, backups: completed.length, running: running.length, failed: failed.length, totalCompressed, totalOriginal, compression, successRate };
  }, [devices, snapshots, completed, running, failed]);

  const series = useMemo(() => {
    const buckets: number[] = Array(14).fill(0);
    const now = Date.now();
    for (const s of snapshots) {
      const t = new Date(s.startedAt).getTime();
      const daysAgo = Math.floor((now - t) / (24 * 3600 * 1000));
      if (daysAgo >= 0 && daysAgo < 14) buckets[13 - daysAgo]++;
    }
    return buckets;
  }, [snapshots]);

  const recent = useMemo(
    () => [...snapshots].sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()).slice(0, 6),
    [snapshots]
  );

  const topDevices = useMemo(() => {
    const byDevice = new Map<string, number>();
    for (const s of completed) {
      byDevice.set(s.deviceId, (byDevice.get(s.deviceId) ?? 0) + (s.totalCompressedSize ?? 0));
    }
    return [...byDevice.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4).map(([id, size]) => {
      const d = devices.find((x) => x.id === id);
      return { id, name: d?.name || d?.hostname || "Máquina", size };
    });
  }, [completed, devices]);

  return (
    <>
      <Topbar title="Visão geral" subtitle="Resumo de proteção das suas máquinas" />
      <div className="space-y-5 p-7">
        {error && (
          <div className="rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}{" "}
            <span style={{ color: "#EF4444", opacity: 0.7 }}>(backend em {API_BASE} está online?)</span>
          </div>
        )}

        {/* KPI strip */}
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <KpiCard
            label="Máquinas protegidas"
            value={loading ? "…" : `${stats.devices}`}
            hint={`${stats.healthy} ativas nas últimas 24h`}
            iconBg="#EDE9FF"
            iconColor="#7B61FF"
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" /><circle cx="7" cy="7.5" r="0.7" fill="currentColor" /><circle cx="7" cy="16.5" r="0.7" fill="currentColor" />
              </svg>
            }
          />
          <KpiCard
            label="Backups concluídos"
            value={loading ? "…" : `${stats.backups}`}
            hint={`${stats.running} em execução agora`}
            iconBg="#ECFDF5"
            iconColor="#10B981"
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            }
          />
          <KpiCard
            label="Volume armazenado"
            value={loading ? "…" : formatBytes(stats.totalCompressed)}
            hint={`Original ${formatBytes(stats.totalOriginal)}`}
            iconBg="#EFF6FF"
            iconColor="#3B82F6"
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M3 5v6c0 1.7 4 3 9 3s9-1.3 9-3V5" /><path d="M3 11v6c0 1.7 4 3 9 3s9-1.3 9-3v-6" />
              </svg>
            }
          />
          <KpiCard
            label="Taxa de sucesso"
            value={loading ? "…" : `${stats.successRate}%`}
            hint={stats.failed > 0 ? `${stats.failed} falha(s) recente(s)` : "Sem falhas recentes"}
            iconBg={stats.failed > 0 ? "#FEF3C7" : "#ECFDF5"}
            iconColor={stats.failed > 0 ? "#F59E0B" : "#10B981"}
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6l-8-3Z" />
              </svg>
            }
          />
        </section>

        {/* Charts row */}
        <section className="grid gap-5 xl:grid-cols-3">
          {/* Activity chart */}
          <div className="kp-card xl:col-span-2">
            <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: "1px solid #F0EEF8" }}>
              <div>
                <h2 className="text-sm font-semibold" style={{ color: "#18163A" }}>Atividade de backups</h2>
                <p className="text-xs mt-0.5" style={{ color: "#6B6993" }}>Snapshots nos últimos 14 dias</p>
              </div>
              <span className="inline-flex items-center gap-1.5 text-xs" style={{ color: "#6B6993" }}>
                <span className="h-2 w-2 rounded-full inline-block" style={{ background: "#7B61FF" }} />
                Snapshots
              </span>
            </div>
            <div className="p-6">
              <BarChart values={series} />
            </div>
          </div>

          {/* Protection status */}
          <div className="kp-card">
            <div className="px-6 py-4" style={{ borderBottom: "1px solid #F0EEF8" }}>
              <h2 className="text-sm font-semibold" style={{ color: "#18163A" }}>Status de proteção</h2>
              <p className="text-xs mt-0.5" style={{ color: "#6B6993" }}>Saúde geral do ambiente</p>
            </div>
            <div className="flex flex-col items-center gap-5 p-6">
              <DonutGauge
                value={stats.successRate}
                label={`${stats.successRate}%`}
                sublabel="sucesso"
                size={152}
                thickness={12}
                color="#7B61FF"
              />
              <div className="grid w-full grid-cols-3 gap-2">
                <StatTag color="#10B981" label="OK" count={completed.length} />
                <StatTag color="#7B61FF" label="Rodando" count={running.length} />
                <StatTag color="#EF4444" label="Falhou" count={failed.length} />
              </div>
            </div>
          </div>
        </section>

        {/* Bottom row */}
        <section className="grid gap-5 xl:grid-cols-3">
          {/* Recent snapshots */}
          <div className="kp-card xl:col-span-2">
            <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: "1px solid #F0EEF8" }}>
              <div>
                <h2 className="text-sm font-semibold" style={{ color: "#18163A" }}>Snapshots recentes</h2>
                <p className="text-xs mt-0.5" style={{ color: "#6B6993" }}>Últimas execuções no seu ambiente</p>
              </div>
              <Link href="/dashboard/backups" className="text-xs font-medium transition-opacity hover:opacity-75" style={{ color: "#7B61FF" }}>
                Ver todos →
              </Link>
            </div>
            {loading ? (
              <p className="px-6 py-10 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
            ) : recent.length === 0 ? (
              <div className="flex flex-col items-center gap-3 px-6 py-12">
                <div className="grid h-12 w-12 place-items-center rounded-2xl" style={{ background: "#EDE9FF" }}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" />
                  </svg>
                </div>
                <p className="text-sm text-center" style={{ color: "#6B6993" }}>Nenhum snapshot ainda.<br />Instale o agente Keeply para começar.</p>
              </div>
            ) : (
              <ul className="divide-y" style={{ borderColor: "#F0EEF8" }}>
                {recent.map((snap) => {
                  const device = devices.find((d) => d.id === snap.deviceId);
                  return (
                    <li key={snap.id} className="flex items-center gap-4 px-6 py-3.5 hover:bg-gray-50/50 transition-colors">
                      <div className={`grid h-8 w-8 place-items-center rounded-lg ${statusBg(snap.status)}`}>
                        <span className={statusFg(snap.status)}>{statusIcon(snap.status)}</span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium" style={{ color: "#18163A" }}>
                          {device?.name ?? device?.hostname ?? "Máquina"}
                        </p>
                        <p className="truncate text-xs" style={{ color: "#6B6993" }}>{snap.sourcePath}</p>
                      </div>
                      <div className="hidden text-right text-xs md:block" style={{ color: "#6B6993" }}>
                        <p className="font-semibold" style={{ color: "#18163A" }}>
                          {formatBytes(snap.totalCompressedSize ?? 0)}
                        </p>
                        <p>{formatRelative(snap.startedAt)}</p>
                      </div>
                      <Link
                        href={`/dashboard/backups/${snap.id}`}
                        className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
                        style={{ border: "1px solid #E4E1F0", color: "#7B61FF", background: "#FAFAFE" }}
                      >
                        Detalhes
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {/* Top machines */}
          <div className="kp-card">
            <div className="px-6 py-4" style={{ borderBottom: "1px solid #F0EEF8" }}>
              <h2 className="text-sm font-semibold" style={{ color: "#18163A" }}>Top máquinas por volume</h2>
              <p className="text-xs mt-0.5" style={{ color: "#6B6993" }}>Onde está seu armazenamento</p>
            </div>
            {loading ? (
              <p className="px-6 py-10 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
            ) : topDevices.length === 0 ? (
              <p className="px-6 py-12 text-center text-sm" style={{ color: "#6B6993" }}>Sem dados ainda.</p>
            ) : (
              <ul className="space-y-4 p-6">
                {topDevices.map((d) => {
                  const max = topDevices[0].size || 1;
                  const pct = Math.round((d.size / max) * 100);
                  return (
                    <li key={d.id}>
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="truncate text-sm font-medium" style={{ color: "#18163A" }}>{d.name}</span>
                        <span className="text-xs ml-2 shrink-0" style={{ color: "#6B6993" }}>{formatBytes(d.size)}</span>
                      </div>
                      <div className="h-1.5 overflow-hidden rounded-full" style={{ background: "#EDE9FF" }}>
                        <div
                          className="h-full rounded-full transition-all"
                          style={{ width: `${pct}%`, background: "#7B61FF" }}
                        />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
            <div className="px-6 py-4" style={{ borderTop: "1px solid #F0EEF8" }}>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs" style={{ color: "#6B6993" }}>Compressão média</p>
                  <p className="font-semibold text-sm mt-0.5" style={{ color: "#18163A" }}>
                    {loading ? "…" : `${stats.compression.toFixed(1)}x`}
                  </p>
                </div>
                <Sparkline values={series} />
              </div>
            </div>
          </div>
        </section>
      </div>
    </>
  );
}

function KpiCard({ label, value, hint, iconBg, iconColor, icon }: {
  label: string; value: string; hint: string;
  iconBg: string; iconColor: string; icon: React.ReactNode;
}) {
  return (
    <div className="kp-card p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wider" style={{ color: "#6B6993" }}>{label}</p>
          <p className="mt-2.5 text-2xl font-bold tabular-nums" style={{ color: "#18163A" }}>{value}</p>
          <p className="mt-1 text-xs" style={{ color: "#6B6993" }}>{hint}</p>
        </div>
        <div
          className="grid h-10 w-10 shrink-0 place-items-center rounded-xl"
          style={{ background: iconBg, color: iconColor }}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}

function BarChart({ values }: { values: number[] }) {
  const max = Math.max(...values, 1);
  return (
    <div className="flex h-40 items-end gap-1">
      {values.map((v, i) => {
        const h = (v / max) * 100;
        const isToday = i === values.length - 1;
        return (
          <div key={i} className="flex flex-1 flex-col items-center justify-end" title={`${v} snapshots`}>
            <div
              className="w-full rounded-t-sm transition-all"
              style={{
                height: `${Math.max(h, 3)}%`,
                background: isToday ? "#7B61FF" : "#C4B8FF",
                opacity: v === 0 ? 0.3 : 1,
              }}
            />
          </div>
        );
      })}
    </div>
  );
}

function StatTag({ color, label, count }: { color: string; label: string; count: number }) {
  return (
    <div className="rounded-xl p-3 text-center" style={{ background: "#F8F7FD" }}>
      <div className="flex items-center justify-center gap-1.5 mb-1">
        <span className="h-1.5 w-1.5 rounded-full inline-block" style={{ background: color }} />
        <span className="text-[11px]" style={{ color: "#6B6993" }}>{label}</span>
      </div>
      <p className="text-base font-bold" style={{ color: "#18163A" }}>{count}</p>
    </div>
  );
}

function statusBg(s: Snapshot["status"]) {
  return s === "COMPLETED" ? "bg-emerald-50" : s === "RUNNING" ? "bg-[#EDE9FF]" : "bg-red-50";
}
function statusFg(s: Snapshot["status"]) {
  return s === "COMPLETED" ? "text-emerald-600" : s === "RUNNING" ? "text-[#7B61FF]" : "text-red-500";
}
function statusIcon(s: Snapshot["status"]) {
  if (s === "COMPLETED")
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M20 6 9 17l-5-5" />
      </svg>
    );
  if (s === "RUNNING")
    return (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="animate-spin">
        <path d="M21 12a9 9 0 1 1-6.2-8.55" />
      </svg>
    );
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 9v4" /><path d="M12 17h.01" /><circle cx="12" cy="12" r="10" />
    </svg>
  );
}
