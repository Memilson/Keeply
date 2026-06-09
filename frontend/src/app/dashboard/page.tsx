"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  api, API_BASE,
  type Device, type Snapshot, type SnapshotStatus, type PagedResponse,
} from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

const DAY_MS = 24 * 60 * 60 * 1000;

/* ─────────────────────────── TOKENS ────────────────────────────── */
const BG       = "#08071A";
const SURFACE  = "#0C0B1C";
const BORDER   = "rgba(255,255,255,0.07)";
const PURPLE   = "#7B61FF";
const PURPLE_L = "#A78BFA";
const GREEN    = "#10B981";
const AMBER    = "#F59E0B";
const RED      = "#EF4444";

/* ─────────────────────────── SKELETON ──────────────────────────── */
function Bone({
  className = "",
  style,
}: {
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <div
      className={`animate-pulse rounded-lg ${className}`}
      style={{ background: "rgba(255,255,255,0.055)", ...style }}
    />
  );
}

/* ─────────────────────────── PAGE ──────────────────────────────── */
export default function DashboardOverview() {
  const [devices, setDevices]     = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState<string | null>(null);
  const [asOf, setAsOf]           = useState<number | null>(null);

  useEffect(() => {
    let dead = false;
    (async () => {
      try {
        const [dl, sp] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<PagedResponse<Snapshot>>("/api/snapshots"),
        ]);
        if (dead) return;
        setDevices(dl ?? []);
        setSnapshots(sp?.items ?? []);
        setAsOf(Date.now());
      } catch (e) {
        if (!dead) { setAsOf(Date.now()); setError(e instanceof Error ? e.message : "Erro."); }
      } finally {
        if (!dead) setLoading(false);
      }
    })();
    return () => { dead = true; };
  }, []);

  const d = useMemo(() => {
    const now  = asOf ?? 0;
    const done = snapshots.filter((s) => s.status === "COMPLETED");
    const run  = snapshots.filter(isRun);
    const fail = snapshots.filter((s) => s.status === "FAILED");
    const b24  = snapshots.filter(
      (s) => (s.status === "COMPLETED" || isRun(s)) && now - +new Date(s.startedAt) <= DAY_MS
    ).length;
    const onl  = devices.filter((d) => d.lastSeenAt && now - +new Date(d.lastSeenAt) <= DAY_MS).length;
    const cf   = snapshots.filter((s) => s.status === "COMPLETED" || s.status === "FAILED");
    const rate = cf.length ? Math.round((done.length / cf.length) * 100) : 100;

    const recent = [...snapshots]
      .sort((a, b) => +new Date(b.startedAt) - +new Date(a.startedAt))
      .slice(0, 6);

    const activity = Array.from({ length: 7 }, (_, i) => {
      const dt  = new Date(now - (6 - i) * DAY_MS);
      const key = dt.toISOString().slice(0, 10);
      return {
        key, label: dt.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" }),
        count: snapshots.filter((s) => new Date(s.startedAt).toISOString().slice(0, 10) === key).length,
      };
    });

    const byDev = new Map<string, number>();
    for (const s of done) byDev.set(s.deviceId, (byDev.get(s.deviceId) ?? 0) + (s.totalCompressedSize ?? 0));
    const top = [...byDev.entries()]
      .sort((a, b) => b[1] - a[1]).slice(0, 5)
      .map(([id, size]) => ({ id, size, name: dname(devices.find((x) => x.id === id)) }));

    return {
      onl, b24, run, fail, rate,
      off: Math.max(devices.length - onl, 0),
      total: done.reduce((a, s) => a + (s.totalCompressedSize ?? 0), 0),
      recent, activity, top,
    };
  }, [asOf, devices, snapshots]);

  return (
    <>
      <Topbar title="Dashboard" />

      <main
        className="flex-1 p-5 space-y-4"
        style={{ background: BG, minHeight: "calc(100vh - 58px)" }}
      >
        {/* Error */}
        {error && (
          <div
            className="flex items-start gap-3 rounded-2xl border px-5 py-3.5 text-sm"
            style={{ background: "rgba(239,68,68,0.07)", borderColor: "rgba(239,68,68,0.2)" }}
            role="alert"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke={RED} strokeWidth="2" strokeLinecap="round" className="h-4 w-4 mt-0.5 shrink-0">
              <circle cx="12" cy="12" r="9" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <p className="text-[#EF4444]">{error} <span className="text-slate-600">(backend em {API_BASE} está online?)</span></p>
          </div>
        )}

        {/* ── KPI ROW ──────────────────────────────────────────── */}
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <KpiCard loading={loading} label="Dispositivos ativos" value={String(d.onl)}
            sub={`${d.off} offline`} color={PURPLE}
            icon={<ServerIcon />}
          />
          <KpiCard loading={loading} label="Backups 24h" value={String(d.b24)}
            sub="nas últimas 24 horas" color={GREEN}
            icon={<ArchiveIcon />}
          />
          <KpiCard loading={loading} label="Em execução" value={String(d.run.length)}
            sub="jobs ativos agora" color={AMBER}
            icon={<ClockIcon />}
          />
          <KpiCard loading={loading} label="Falhas" value={String(d.fail.length)}
            sub={`${d.rate}% taxa de sucesso`} color={RED}
            icon={<ShieldXIcon />}
          />
        </div>

        {/* ── CHARTS ───────────────────────────────────────────── */}
        <div className="grid gap-3 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <DonutSection loading={loading} rate={d.rate}
            devices={devices.length} b24={d.b24} run={d.run.length} fail={d.fail.length}
          />
          <ActivitySection loading={loading} values={d.activity} />
        </div>

        {/* ── BOTTOM ───────────────────────────────────────────── */}
        <div className="grid gap-3 lg:grid-cols-[1fr_288px]">
          <SnapshotsSection loading={loading} snapshots={d.recent} devices={devices} />
          <TopSection loading={loading} items={d.top} />
        </div>
      </main>
    </>
  );
}

/* ══════════════════════════════════════════════════════════════════
   KPI CARD
═══════════════════════════════════════════════════════════════════ */
function KpiCard({
  label, value, sub, color, icon, loading,
}: {
  label: string; value: string; sub: string;
  color: string; icon: React.ReactNode; loading: boolean;
}) {
  return (
    <div
      className="group relative overflow-hidden rounded-2xl border p-5 transition-all duration-200 cursor-default"
      style={{ background: SURFACE, borderColor: BORDER }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = `${color}30`;
        el.style.boxShadow   = `0 0 28px ${color}12, inset 0 1px 0 ${color}15`;
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = BORDER;
        el.style.boxShadow   = "none";
      }}
    >
      {/* Top-right corner glow */}
      <div
        className="absolute -right-4 -top-4 h-16 w-16 rounded-full pointer-events-none"
        style={{ background: `${color}0D`, filter: "blur(12px)" }}
      />

      <div className="relative flex items-start justify-between">
        <p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-600">{label}</p>
        <div
          className="grid h-8 w-8 shrink-0 place-items-center rounded-xl transition-transform duration-200 group-hover:scale-110"
          style={{ background: `${color}14`, color }}
        >
          {icon}
        </div>
      </div>

      <div className="relative mt-4">
        {loading ? (
          <>
            <Bone className="h-9 w-20 rounded-xl" />
            <Bone className="mt-2 h-3 w-28" />
          </>
        ) : (
          <>
            <p className="text-[2rem] font-black leading-none tracking-tight text-white tabular-nums">
              {value}
            </p>
            <p className="mt-1.5 text-[11px] text-slate-600">{sub}</p>
          </>
        )}
      </div>

      {/* Bottom accent line */}
      {!loading && (
        <div
          className="absolute bottom-0 left-0 h-[2px] w-full transition-all duration-300"
          style={{ background: `linear-gradient(90deg, ${color}50, ${color}15, transparent)` }}
        />
      )}
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════
   DONUT SECTION
═══════════════════════════════════════════════════════════════════ */
function DonutSection({
  loading, rate, devices, b24, run, fail,
}: {
  loading: boolean; rate: number;
  devices: number; b24: number; run: number; fail: number;
}) {
  const segs = [
    { label: "Máquinas", value: devices, color: PURPLE },
    { label: "Backups 24h", value: b24, color: GREEN },
    { label: "Executando", value: run, color: AMBER },
    { label: "Falhas", value: fail, color: RED },
  ];
  const hasData = segs.some((s) => s.value > 0);

  const SIZE  = 160;
  const SW    = 14;
  const R     = (SIZE - SW) / 2;
  const CIRC  = 2 * Math.PI * R;
  const total = segs.reduce((a, s) => a + s.value, 0) || 1;

  const arcs = segs.reduce<
    { label: string; color: string; da: string; offset: number }[]
  >((acc, seg) => {
    const used = acc.reduce((a, x) => a + Number(x.da.split(" ")[0]), 0);
    const len  = (seg.value / total) * CIRC;
    acc.push({ label: seg.label, color: seg.color, da: `${len} ${CIRC - len}`, offset: -used });
    return acc;
  }, []);

  return (
    <SectionCard title="Saúde do ambiente" subtitle="Distribuição por categoria">
      {loading ? (
        <div className="flex items-center gap-6">
          <div className="relative shrink-0">
            <Bone className="rounded-full" style={{ width: SIZE, height: SIZE }} />
            <div className="absolute inset-[14px] rounded-full" style={{ background: SURFACE }} />
          </div>
          <div className="flex-1 space-y-3">
            {[80, 65, 50, 70].map((w, i) => (
              <div key={i} className="flex items-center gap-2">
                <Bone className="h-2.5 w-2.5 rounded-full shrink-0" />
                <Bone className="h-3" style={{ width: `${w}%` }} />
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-6">
          <div className="relative shrink-0">
            <svg
              width={SIZE} height={SIZE}
              viewBox={`0 0 ${SIZE} ${SIZE}`}
              role="img"
              aria-label={`Saúde do sistema: ${rate}%`}
            >
              <circle
                cx={SIZE / 2} cy={SIZE / 2} r={R}
                fill="none"
                stroke="rgba(255,255,255,0.04)"
                strokeWidth={SW}
              />
              {hasData && arcs.map((arc) => (
                <circle
                  key={arc.label}
                  cx={SIZE / 2} cy={SIZE / 2} r={R}
                  fill="none"
                  stroke={arc.color}
                  strokeWidth={SW}
                  strokeLinecap="round"
                  strokeDasharray={arc.da}
                  strokeDashoffset={arc.offset}
                  transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
                  style={{ filter: `drop-shadow(0 0 5px ${arc.color}70)` }}
                />
              ))}
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span
                className="text-[1.8rem] font-black tabular-nums text-white leading-none"
                style={{ textShadow: `0 0 20px ${PURPLE}60` }}
              >
                {rate}%
              </span>
              <span className="mt-0.5 text-[10px] font-semibold uppercase tracking-widest text-slate-600">
                saúde
              </span>
            </div>
          </div>

          <div className="flex-1 space-y-3">
            {segs.map((seg) => (
              <div key={seg.label} className="group/row flex items-center gap-2.5">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ background: seg.color, boxShadow: `0 0 5px ${seg.color}70` }}
                />
                <span className="flex-1 text-[12px] text-slate-500 truncate">{seg.label}</span>
                <span className="text-[13px] font-bold tabular-nums text-white">{seg.value}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </SectionCard>
  );
}

/* ══════════════════════════════════════════════════════════════════
   ACTIVITY SECTION
═══════════════════════════════════════════════════════════════════ */
function ActivitySection({
  loading,
  values,
}: {
  loading: boolean;
  values: { key: string; count: number; label: string }[];
}) {
  const max    = Math.max(...values.map((v) => v.count), 1);
  const guides = [max, Math.ceil(max / 2), 0];
  const H      = 128;

  return (
    <SectionCard title="Atividade de backups" subtitle="Snapshots nos últimos 7 dias">
      <div className="flex gap-2" style={{ height: H + 24 }}>
        {/* Y-axis */}
        <div className="flex flex-col justify-between items-end pb-6 shrink-0 w-5">
          {guides.map((v, i) => (
            <span key={`${v}${i}`} className="text-[9px] tabular-nums leading-none" style={{ color: "#374151" }}>
              {v}
            </span>
          ))}
        </div>

        {/* Chart area */}
        <div className="relative flex flex-1 items-end gap-[5px] pb-6">
          {/* Horizontal grid lines */}
          <div className="absolute inset-0 bottom-6 flex flex-col justify-between pointer-events-none">
            {guides.map((_, i) => (
              <div key={i} style={{ borderTop: "1px solid rgba(255,255,255,0.035)" }} />
            ))}
          </div>

          {loading
            ? Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="relative flex flex-1 flex-col items-center gap-1.5">
                  <div className="flex flex-1 w-full items-end">
                    <Bone
                      className="w-full rounded-t-lg"
                      style={{ height: `${25 + ((i * 17) % 55)}%` }}
                    />
                  </div>
                  <Bone className="h-2.5 w-7" />
                </div>
              ))
            : values.map((item) => {
                const pct   = Math.max((item.count / max) * 100, item.count > 0 ? 6 : 1.5);
                const empty = item.count === 0;
                return (
                  <div
                    key={item.key}
                    className="relative flex flex-1 flex-col items-center gap-1.5"
                    title={`${item.count} snapshot${item.count !== 1 ? "s" : ""} em ${item.label}`}
                  >
                    <div className="flex flex-1 w-full items-end">
                      <div
                        className="relative w-full rounded-t-lg overflow-hidden transition-all duration-500"
                        style={{ height: `${pct}%`, minHeight: 3 }}
                      >
                        <div
                          className="absolute inset-0"
                          style={{
                            background: empty
                              ? "rgba(123,97,255,0.08)"
                              : `linear-gradient(180deg, ${PURPLE_L} 0%, ${PURPLE} 100%)`,
                            boxShadow: empty ? "none" : `0 -3px 12px ${PURPLE}50`,
                          }}
                        />
                      </div>
                    </div>
                    <span className="text-[9px] whitespace-nowrap" style={{ color: "#374151" }}>
                      {item.label}
                    </span>
                  </div>
                );
              })}
        </div>
      </div>
    </SectionCard>
  );
}

/* ══════════════════════════════════════════════════════════════════
   SNAPSHOTS SECTION
═══════════════════════════════════════════════════════════════════ */
function SnapshotsSection({
  loading, snapshots, devices,
}: {
  loading: boolean; snapshots: Snapshot[]; devices: Device[];
}) {
  return (
    <SectionCard
      title="Snapshots recentes"
      subtitle="Últimas execuções do ambiente"
      action={
        <Link
          href="/dashboard/machines"
          className="text-[11px] font-semibold transition-colors duration-200 focus:outline-none"
          style={{ color: PURPLE_L }}
          onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = "#fff"; }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = PURPLE_L; }}
        >
          Ver em máquinas →
        </Link>
      }
      noPadding
    >
      {loading ? (
        <div className="px-5 py-4">
          {/* skeleton header */}
          <div className="flex gap-4 pb-3 mb-1" style={{ borderBottom: "1px solid rgba(255,255,255,0.06)" }}>
            {[100, 120, 80, 60, 90, 40].map((w, i) => (
              <Bone key={i} className="h-2.5" style={{ width: w }} />
            ))}
          </div>
          {/* skeleton rows */}
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className="flex items-center gap-4 py-3"
              style={{
                borderTop: "1px solid rgba(255,255,255,0.04)",
                opacity: 1 - i * 0.16,
              }}
            >
              <Bone className="h-8 w-8 rounded-xl shrink-0" />
              <Bone className="h-3 flex-1 max-w-[100px]" />
              <Bone className="h-3 flex-1 max-w-[160px]" />
              <Bone className="h-5 w-20 rounded-full" />
              <Bone className="h-3 w-14" />
              <Bone className="h-3 w-24" />
              <Bone className="h-3 w-8 ml-auto" />
            </div>
          ))}
        </div>
      ) : snapshots.length === 0 ? (
        <EmptyState
          icon={<ArchiveIcon />}
          title="Nenhum snapshot"
          sub="Instale o agente Keeply para começar."
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[600px]">
            <thead>
              <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.06)" }}>
                {["Máquina", "Caminho", "Status", "Tamanho", "Iniciado", ""].map((h) => (
                  <th
                    key={h}
                    className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-[0.1em]"
                    style={{ color: "#374151", background: "rgba(255,255,255,0.02)" }}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {snapshots.map((snap) => {
                const dev = devices.find((d) => d.id === snap.deviceId);
                const name = dname(dev);
                return (
                  <tr
                    key={snap.id}
                    style={{ borderTop: "1px solid rgba(255,255,255,0.035)" }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "rgba(123,97,255,0.04)";
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "transparent";
                    }}
                  >
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2.5">
                        <span
                          className="grid h-8 w-8 shrink-0 place-items-center rounded-xl text-[10px] font-black text-white"
                          style={{
                            background: `linear-gradient(135deg, ${PURPLE}, #5B3FE0)`,
                            boxShadow: `0 2px 8px ${PURPLE}35`,
                          }}
                        >
                          {name.slice(0, 2).toUpperCase()}
                        </span>
                        <span className="text-[12px] font-semibold text-white">{name}</span>
                      </div>
                    </td>
                    <td className="px-5 py-3">
                      <span
                        className="block max-w-[180px] truncate font-mono text-[11px]"
                        style={{ color: "#4B5563" }}
                        title={snap.sourcePath}
                      >
                        {snap.sourcePath}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <Pill status={snap.status} />
                    </td>
                    <td className="px-5 py-3 text-[11px] tabular-nums" style={{ color: "#4B5563" }}>
                      {formatBytes(snap.totalCompressedSize ?? 0)}
                    </td>
                    <td className="px-5 py-3 text-[11px]" style={{ color: "#4B5563" }}>
                      {formatDateTime(snap.startedAt)}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link
                        href="/dashboard/machines"
                        className="text-[11px] font-bold transition-colors duration-150"
                        style={{ color: PURPLE_L }}
                        onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = "#fff"; }}
                        onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = PURPLE_L; }}
                        aria-label={`Abrir snapshot de ${name}`}
                      >
                        →
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </SectionCard>
  );
}

/* ══════════════════════════════════════════════════════════════════
   TOP DEVICES
═══════════════════════════════════════════════════════════════════ */
const RANK_COLORS = [
  { bg: "rgba(250,189,50,0.15)", text: "#FBBF24" },
  { bg: "rgba(148,163,184,0.12)", text: "#94A3B8" },
  { bg: "rgba(180,110,70,0.12)", text: "#B46E46" },
];

function TopSection({
  loading,
  items,
}: {
  loading: boolean;
  items: { id: string; name: string; size: number }[];
}) {
  const max = items[0]?.size || 1;

  return (
    <SectionCard title="Top por volume" subtitle="Ranking de armazenamento">
      {loading ? (
        <div className="space-y-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} style={{ opacity: 1 - i * 0.2 }}>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2.5 flex-1">
                  <Bone className="h-5 w-5 rounded-md shrink-0" />
                  <Bone className="h-3 flex-1 max-w-[100px]" />
                </div>
                <Bone className="h-3 w-14 ml-3 shrink-0" />
              </div>
              <Bone className="h-1.5 w-full rounded-full" />
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<ServerIcon />}
          title="Sem dados"
          sub="Aguardando snapshots completos."
        />
      ) : (
        <div className="space-y-4">
          {items.map((item, i) => {
            const pct = Math.max((item.size / max) * 100, 5);
            const rank = RANK_COLORS[i] ?? { bg: "rgba(255,255,255,0.05)", text: "#4B5563" };
            return (
              <div key={item.id}>
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <span
                      className="grid h-5 w-5 shrink-0 place-items-center rounded-md text-[10px] font-black leading-none"
                      style={{ background: rank.bg, color: rank.text }}
                    >
                      {i + 1}
                    </span>
                    <span className="truncate text-[12px] font-medium text-white">{item.name}</span>
                  </div>
                  <span className="ml-3 shrink-0 text-[11px] tabular-nums" style={{ color: "#4B5563" }}>
                    {formatBytes(item.size)}
                  </span>
                </div>
                <div
                  className="h-1.5 overflow-hidden rounded-full"
                  style={{ background: "rgba(255,255,255,0.05)" }}
                  role="progressbar"
                  aria-valuenow={Math.round(pct)}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div
                    className="h-full rounded-full transition-all duration-700"
                    style={{
                      width: `${pct}%`,
                      background:
                        i === 0
                          ? `linear-gradient(90deg, ${PURPLE}, ${PURPLE_L})`
                          : "rgba(123,97,255,0.4)",
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </SectionCard>
  );
}

/* ══════════════════════════════════════════════════════════════════
   SHARED PRIMITIVES
═══════════════════════════════════════════════════════════════════ */
function SectionCard({
  title, subtitle, action, children, noPadding = false,
}: {
  title: string; subtitle: string;
  action?: React.ReactNode;
  children: React.ReactNode;
  noPadding?: boolean;
}) {
  return (
    <div
      className="flex flex-col rounded-2xl border overflow-hidden"
      style={{ background: SURFACE, borderColor: BORDER }}
    >
      <div
        className="flex items-center justify-between px-5 py-4 shrink-0"
        style={{ borderBottom: "1px solid rgba(255,255,255,0.06)" }}
      >
        <div>
          <h2 className="text-[13px] font-semibold text-white">{title}</h2>
          <p className="mt-0.5 text-[11px]" style={{ color: "#374151" }}>{subtitle}</p>
        </div>
        {action}
      </div>
      <div className={noPadding ? "flex-1 min-h-0" : "flex-1 min-h-0 p-5"}>{children}</div>
    </div>
  );
}

function EmptyState({ icon, title, sub }: { icon: React.ReactNode; title: string; sub: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-10 px-5">
      <div
        className="grid h-12 w-12 place-items-center rounded-2xl"
        style={{ background: "rgba(123,97,255,0.1)", color: PURPLE_L }}
      >
        {icon}
      </div>
      <div className="text-center">
        <p className="text-sm font-semibold text-white">{title}</p>
        <p className="mt-0.5 text-[12px]" style={{ color: "#374151" }}>{sub}</p>
      </div>
    </div>
  );
}

/* ── Status Pill ─────────────────────────────────────────────────── */
function Pill({ status }: { status: SnapshotStatus }) {
  const running = status === "RUNNING" || status === "IN_PROGRESS" || status === "PROCESSING";
  const map: Record<SnapshotStatus, { label: string; bg: string; text: string }> = {
    COMPLETED:   { label: "Concluído",   bg: `${GREEN}14`,  text: GREEN  },
    RUNNING:     { label: "Executando",  bg: `${PURPLE}14`, text: PURPLE_L },
    IN_PROGRESS: { label: "Executando",  bg: `${PURPLE}14`, text: PURPLE_L },
    PROCESSING:  { label: "Processando", bg: `${PURPLE}14`, text: PURPLE_L },
    FAILED:      { label: "Falhou",      bg: `${RED}14`,    text: RED    },
  };
  const { label, bg, text } = map[status] ?? map.FAILED;

  return (
    <span
      className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide"
      style={{ background: bg, color: text }}
    >
      {running ? (
        <span className="relative flex h-1.5 w-1.5 shrink-0">
          <span
            className="absolute h-full w-full animate-ping rounded-full opacity-60"
            style={{ background: text }}
          />
          <span className="relative h-1.5 w-1.5 rounded-full" style={{ background: text }} />
        </span>
      ) : (
        <span
          className="h-1.5 w-1.5 rounded-full shrink-0"
          style={{ background: text }}
        />
      )}
      {label}
    </span>
  );
}

/* ── Helpers ─────────────────────────────────────────────────────── */
function isRun(s: Snapshot) {
  return s.status === "RUNNING" || s.status === "IN_PROGRESS" || s.status === "PROCESSING";
}
function dname(d?: Device) { return d?.name || d?.hostname || "Máquina"; }

/* ── Icons ───────────────────────────────────────────────────────── */
function ServerIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-[17px] w-[17px]">
      <rect x="3" y="4" width="18" height="7" rx="1.5" />
      <rect x="3" y="13" width="18" height="7" rx="1.5" />
      <circle cx="7" cy="7.5" r="0.7" fill="currentColor" />
      <circle cx="7" cy="16.5" r="0.7" fill="currentColor" />
    </svg>
  );
}
function ArchiveIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-[17px] w-[17px]">
      <rect x="3" y="4" width="18" height="4" rx="1" />
      <path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" />
      <path d="M10 12h4" />
    </svg>
  );
}
function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-[17px] w-[17px]">
      <circle cx="12" cy="12" r="9" />
      <polyline points="12 7 12 12 15 15" />
    </svg>
  );
}
function ShieldXIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-[17px] w-[17px]">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <line x1="10" y1="10" x2="14" y2="14" />
      <line x1="14" y1="10" x2="10" y2="14" />
    </svg>
  );
}
