"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, API_BASE, type Device, type Snapshot, type SnapshotStatus } from "@/lib/api";
import { formatBytes, formatDateTime, formatRelative } from "@/lib/format";
import { Topbar } from "@/components/Topbar";
import { DonutGauge } from "@/components/Sparkline";

const DAY_MS = 24 * 60 * 60 * 1000;

export default function DashboardOverview() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [asOf, setAsOf] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [deviceList, snapshotList] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<Snapshot[]>("/api/snapshots"),
        ]);
        if (cancelled) return;
        setDevices(deviceList ?? []);
        setSnapshots(snapshotList ?? []);
        setAsOf(Date.now());
        setError(null);
      } catch (e) {
        if (!cancelled) {
          setAsOf(Date.now());
          setError(e instanceof Error ? e.message : "Falha ao carregar dados.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const dashboard = useMemo(() => {
    const now = asOf ?? 0;
    const completed = snapshots.filter((s) => s.status === "COMPLETED");
    const running = snapshots.filter(isRunningStatus);
    const failed = snapshots.filter((s) => s.status === "FAILED");
    const recent24h = snapshots.filter((s) => now - new Date(s.startedAt).getTime() <= DAY_MS);
    const backups24h = recent24h.filter((s) => s.status === "COMPLETED" || isRunningStatus(s)).length;
    const activeDevices = devices.filter((d) => d.lastSeenAt && now - new Date(d.lastSeenAt).getTime() <= DAY_MS).length;
    const completedOrFailed = snapshots.filter((s) => s.status === "COMPLETED" || s.status === "FAILED");
    const successRate =
      completedOrFailed.length > 0 ? Math.round((completed.length / completedOrFailed.length) * 100) : 100;
    const totalCompressed = completed.reduce((acc, s) => acc + (s.totalCompressedSize ?? 0), 0);
    const latestSnapshot = [...snapshots].sort(
      (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
    )[0];

    const recentSnapshots = [...snapshots]
      .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())
      .slice(0, 6);

    const activity = Array.from({ length: 7 }, (_, index) => {
      const date = new Date(now - (6 - index) * DAY_MS);
      const key = date.toISOString().slice(0, 10);
      const count = snapshots.filter((s) => new Date(s.startedAt).toISOString().slice(0, 10) === key).length;
      return {
        key,
        count,
        label: date.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" }),
      };
    });

    const byDevice = new Map<string, number>();
    for (const snapshot of completed) {
      byDevice.set(snapshot.deviceId, (byDevice.get(snapshot.deviceId) ?? 0) + (snapshot.totalCompressedSize ?? 0));
    }

    const topDevices = [...byDevice.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([id, size]) => {
        const device = devices.find((d) => d.id === id);
        return { id, name: deviceName(device), size };
      });

    return {
      activeDevices,
      activity,
      backups24h,
      completed,
      failed,
      latestSnapshot,
      offlineDevices: Math.max(devices.length - activeDevices, 0),
      recentSnapshots,
      running,
      successRate,
      topDevices,
      totalCompressed,
    };
  }, [asOf, devices, snapshots]);

  return (
    <>
      <Topbar />
      <main className="dashboard-page">
        {error && (
          <div className="kp-alert-error">
            {error} <span>(backend em {API_BASE} está online?)</span>
          </div>
        )}

        <section className="kpi-grid">
          <KpiCard
            label="Máquinas protegidas"
            value={loading ? "..." : String(devices.length)}
            hint={`${dashboard.activeDevices} ativas, ${dashboard.offlineDevices} offline`}
            tone="purple"
            icon={ICONS.devices}
          />
          <KpiCard
            label="Backups últimas 24h"
            value={loading ? "..." : String(dashboard.backups24h)}
            hint={`${dashboard.running.length} em execução agora`}
            tone="green"
            icon={ICONS.backup}
          />
          <KpiCard
            label="Armazenamento usado"
            value={loading ? "..." : formatBytes(dashboard.totalCompressed)}
            hint="Snapshots concluídos"
            tone="blue"
            icon={ICONS.storage}
          />
          <KpiCard
            label="Saúde do ambiente"
            value={loading ? "..." : `${dashboard.successRate}%`}
            hint="Sucesso entre concluídos e falhos"
            tone="green"
            icon={ICONS.shield}
          />
          <KpiCard
            label="Último snapshot"
            value={loading ? "..." : formatRelative(dashboard.latestSnapshot?.startedAt)}
            hint={dashboard.latestSnapshot ? statusLabel(dashboard.latestSnapshot.status) : "Sem snapshots"}
            tone="purple"
            icon={ICONS.clock}
          />
          <KpiCard
            label="Falhas críticas"
            value={loading ? "..." : String(dashboard.failed.length)}
            hint={dashboard.failed.length > 0 ? "Requer atenção" : "Nenhuma falha registrada"}
            tone={dashboard.failed.length > 0 ? "red" : "green"}
            icon={ICONS.warning}
          />
        </section>

        <section className="dashboard-grid-main">
          <article className="kp-card dashboard-card activity-card">
            <CardHeader title="Atividade de backups" description="Snapshots registrados nos últimos 7 dias" />
            <BackupActivityChart values={dashboard.activity} loading={loading} />
          </article>

          <article className="kp-card dashboard-card">
            <CardHeader title="Status de proteção" description="Saúde geral do ambiente" />
            <div className="protection-status">
              <DonutGauge
                value={loading ? 0 : dashboard.successRate}
                label={loading ? "..." : `${dashboard.successRate}%`}
                sublabel="sucesso"
                size={156}
                thickness={13}
                color="#7B61FF"
                trackColor="#EDE9FF"
              />
              <div className="protection-ok">
                <span className="protection-dot" />
                {dashboard.failed.length === 0 ? "Ambiente protegido" : "Falhas detectadas"}
              </div>
              <div className="status-counters">
                <StatTag label="Concluído" count={dashboard.completed.length} color="#10B981" />
                <StatTag label="Rodando" count={dashboard.running.length} color="#7B61FF" />
                <StatTag label="Falhou" count={dashboard.failed.length} color="#EF4444" />
              </div>
            </div>
          </article>
        </section>

        <section className="dashboard-grid-bottom">
          <article className="kp-card dashboard-card snapshots-card">
            <div className="card-heading-row">
              <CardHeader title="Snapshots recentes" description="Últimas execuções no seu ambiente" />
              <Link href="/dashboard/backups" className="kp-link">
                Ver todos
              </Link>
            </div>
            <RecentSnapshotsTable snapshots={dashboard.recentSnapshots} devices={devices} loading={loading} />
          </article>

          <article className="kp-card dashboard-card">
            <CardHeader title="Top máquinas por volume" description="Ranking por armazenamento usado" />
            <TopDevices devices={dashboard.topDevices} loading={loading} />
          </article>
        </section>
      </main>
    </>
  );
}

function CardHeader({ title, description }: { title: string; description: string }) {
  return (
    <div className="card-header">
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function KpiCard({
  label,
  value,
  hint,
  tone,
  icon,
}: {
  label: string;
  value: string;
  hint: string;
  tone: "purple" | "green" | "blue" | "red";
  icon: React.ReactNode;
}) {
  return (
    <article className="kp-card kpi-card">
      <div className={`kpi-icon kpi-icon-${tone}`}>{icon}</div>
      <div className="min-w-0">
        <p className="kpi-label">{label}</p>
        <p className="kpi-value">{value}</p>
        <p className="kpi-hint">{hint}</p>
      </div>
    </article>
  );
}

function BackupActivityChart({ values, loading }: { values: { key: string; count: number; label: string }[]; loading: boolean }) {
  const max = Math.max(...values.map((v) => v.count), 1);
  const guideValues = Array.from(new Set([max, Math.ceil(max / 2), 0]));

  return (
    <div className="chart-wrap">
      <div className="chart-guides" aria-hidden="true">
        {guideValues.map((value, index) => (
          <span key={`${value}-${index}`}>{value}</span>
        ))}
      </div>
      <div className="chart-bars">
        {values.map((item) => {
          const height = loading ? 22 : Math.max((item.count / max) * 100, item.count > 0 ? 12 : 4);
          return (
            <div key={item.key} className="chart-bar-cell" title={`${item.count} snapshots em ${item.label}`}>
              <div className="chart-bar-track">
                <div className="chart-bar" style={{ height: `${height}%`, opacity: item.count === 0 && !loading ? 0.28 : 1 }} />
              </div>
              <span>{item.label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function RecentSnapshotsTable({
  snapshots,
  devices,
  loading,
}: {
  snapshots: Snapshot[];
  devices: Device[];
  loading: boolean;
}) {
  if (loading) return <p className="empty-state">Carregando snapshots...</p>;
  if (snapshots.length === 0) return <p className="empty-state">Nenhum snapshot ainda. Instale o agente Keeply para começar.</p>;

  return (
    <div className="table-scroll">
      <table className="kp-table">
        <thead>
          <tr>
            {["Máquina", "Caminho", "Status", "Tamanho", "Duração", "Iniciado", "Ações"].map((heading) => (
              <th key={heading}>{heading}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {snapshots.map((snapshot) => {
            const device = devices.find((d) => d.id === snapshot.deviceId);
            return (
              <tr key={snapshot.id}>
                <td>
                  <div className="machine-cell">
                    <span className="machine-avatar">{deviceName(device).slice(0, 2).toUpperCase()}</span>
                    <span>{deviceName(device)}</span>
                  </div>
                </td>
                <td>
                  <span className="path-cell" title={snapshot.sourcePath}>
                    {snapshot.sourcePath}
                  </span>
                </td>
                <td>
                  <StatusPill status={snapshot.status} />
                </td>
                <td>{formatBytes(snapshot.totalCompressedSize ?? 0)}</td>
                <td>{formatDuration(snapshot.startedAt, snapshot.completedAt)}</td>
                <td>{formatDateTime(snapshot.startedAt)}</td>
                <td>
                  <Link href={`/dashboard/backups/${snapshot.id}`} className="table-action">
                    Abrir
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function TopDevices({ devices, loading }: { devices: { id: string; name: string; size: number }[]; loading: boolean }) {
  if (loading) return <p className="empty-state">Carregando ranking...</p>;
  if (devices.length === 0) return <p className="empty-state">Sem dados de volume ainda.</p>;

  const max = devices[0]?.size || 1;
  return (
    <div className="volume-list">
      {devices.map((device, index) => (
        <div key={device.id} className="volume-row">
          <div className="volume-row-top">
            <span className="volume-rank">{index + 1}</span>
            <span className="volume-name">{device.name}</span>
            <span className="volume-size">{formatBytes(device.size)}</span>
          </div>
          <div className="volume-track">
            <div className="volume-bar" style={{ width: `${Math.max((device.size / max) * 100, 6)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function StatTag({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <div className="status-counter">
      <span style={{ background: color }} />
      <strong>{count}</strong>
      <small>{label}</small>
    </div>
  );
}

function StatusPill({ status }: { status: SnapshotStatus }) {
  const map: Record<SnapshotStatus, { label: string; className: string }> = {
    COMPLETED: { label: "Concluído", className: "badge-success" },
    RUNNING: { label: "Em execução", className: "badge-running" },
    IN_PROGRESS: { label: "Em execução", className: "badge-running" },
    PROCESSING: { label: "Processando", className: "badge-running" },
    FAILED: { label: "Falhou", className: "badge-danger" },
  };
  const meta = map[status] ?? map.FAILED;
  return <span className={`kp-badge ${meta.className}`}>{meta.label}</span>;
}

function isRunningStatus(snapshot: Snapshot) {
  return snapshot.status === "RUNNING" || snapshot.status === "IN_PROGRESS" || snapshot.status === "PROCESSING";
}

function statusLabel(status: SnapshotStatus) {
  if (status === "COMPLETED") return "Concluído";
  if (status === "FAILED") return "Falhou";
  return "Em execução";
}

function deviceName(device?: Device) {
  return device?.name || device?.hostname || "Máquina";
}

function formatDuration(startedAt?: string, completedAt?: string) {
  if (!startedAt || !completedAt) return "-";
  const diff = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (!Number.isFinite(diff) || diff < 0) return "-";
  const seconds = Math.round(diff / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  if (minutes < 60) return remainingSeconds ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
}

const ICONS = {
  devices: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="6" rx="1.5" />
      <rect x="3" y="14" width="18" height="6" rx="1.5" />
      <path d="M7 7h.01M7 17h.01" />
    </svg>
  ),
  backup: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  ),
  storage: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
      <path d="M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" />
    </svg>
  ),
  shield: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3 5 6v5c0 4.7 3 8.2 7 9 4-.8 7-4.3 7-9V6l-7-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  ),
  clock: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="8" />
      <path d="M12 8v5l3 2" />
    </svg>
  ),
  warning: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <path d="m12 3 9 16H3L12 3Z" />
      <path d="M12 9v4M12 17h.01" />
    </svg>
  ),
};
