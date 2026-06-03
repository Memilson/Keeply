"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, API_BASE, type Device, type Snapshot, type SnapshotStatus, type PagedResponse } from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

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
        const [deviceList, snapshotPage] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<PagedResponse<Snapshot>>("/api/snapshots"),
        ]);
        if (cancelled) return;
        setDevices(deviceList ?? []);
        setSnapshots(snapshotPage?.items ?? []);
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

  const donutSegments = useMemo(() => {
    const segments = [
      { label: "Máquinas protegidas", value: devices.length, color: "#7B61FF" },
      { label: "Backups 24h", value: dashboard.backups24h, color: "#22C55E" },
      { label: "Em execução", value: dashboard.running.length, color: "#F59E0B" },
      { label: "Falhas críticas", value: dashboard.failed.length, color: "#EF4444" },
    ];
    return segments.some((segment) => segment.value > 0) ? segments : [{ label: "Sem atividade", value: 1, color: "#E4E1F0" }];
  }, [dashboard.backups24h, dashboard.failed.length, dashboard.running.length, devices.length]);

  return (
    <>
      <Topbar title="Dashboard" />
      <main className="dashboard-page">
        {error && (
          <div className="kp-alert-error">
            {error} <span>(backend em {API_BASE} está online?)</span>
          </div>
        )}

        <section className="dashboard-grid-main dashboard-grid-overview">
          <article className="dashboard-card">
            <CardHeader title="Saúde do ambiente" description="Panorama rápido do ambiente" />
            <div className="dashboard-summary">
              <div className="dashboard-summary-chart">
                <MultiDonutChart
                  segments={donutSegments}
                  centerValue={loading ? "..." : `${dashboard.successRate}%`}
                  centerLabel="saúde"
                />
              </div>
            </div>
          </article>

          <article className="dashboard-card activity-card">
            <CardHeader title="Atividade de backups" description="Snapshots registrados nos últimos 7 dias" />
            <BackupActivityChart values={dashboard.activity} loading={loading} />
          </article>
        </section>

        <section className="dashboard-grid-bottom">
          <article className="dashboard-card snapshots-card">
            <div className="card-heading-row">
              <CardHeader title="Snapshots recentes" description="Últimas execuções no seu ambiente" />
              <Link href="/dashboard/backups" className="kp-link">
                Ver todos
              </Link>
            </div>
            <RecentSnapshotsTable snapshots={dashboard.recentSnapshots} devices={devices} loading={loading} />
          </article>

          <article className="dashboard-card">
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

function MultiDonutChart({
  segments,
  centerValue,
  centerLabel,
}: {
  segments: { label: string; value: number; color: string }[];
  centerValue: string;
  centerLabel: string;
}) {
  const size = 220;
  const strokeWidth = 18;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const total = segments.reduce((sum, segment) => sum + segment.value, 0) || 1;
  const arcs = segments.reduce<Array<{ label: string; color: string; dashArray: string; dashOffset: number }>>(
    (acc, segment) => {
      const usedLength = acc.reduce((sum, arc) => sum + Number(arc.dashArray.split(" ")[0]), 0);
      const segmentLength = (segment.value / total) * circumference;
      acc.push({
        label: segment.label,
        color: segment.color,
        dashArray: `${segmentLength} ${circumference - segmentLength}`,
        dashOffset: -usedLength,
      });
      return acc;
    },
    []
  );

  return (
    <div className="multi-donut">
      <div className="multi-donut-visual">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="#EEEAFB"
            strokeWidth={strokeWidth}
          />
          {arcs.map((arc) => {
            return (
              <circle
                key={arc.label}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={arc.color}
                strokeWidth={strokeWidth}
                strokeLinecap="round"
                strokeDasharray={arc.dashArray}
                strokeDashoffset={arc.dashOffset}
                transform={`rotate(-90 ${size / 2} ${size / 2})`}
              />
            );
          })}
        </svg>
        <div className="multi-donut-center">
          <strong>{centerValue}</strong>
          <span>{centerLabel}</span>
        </div>
      </div>
      <div className="multi-donut-legend">
        {segments.map((segment) => (
          <div key={segment.label} className="multi-donut-legend-item">
            <span className="multi-donut-dot" style={{ background: segment.color }} />
            <span>{segment.label}</span>
            <strong>{segment.value}</strong>
          </div>
        ))}
      </div>
    </div>
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
