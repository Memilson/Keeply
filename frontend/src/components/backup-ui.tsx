"use client";

import Link from "next/link";
import Image from "next/image";
import type { ReactNode } from "react";
import type { Device, DevicePlan, Snapshot, SnapshotStatus } from "@/lib/api";
import type { MachineOperationalStatus, SnapshotKind } from "@/lib/backup-view";
import { deviceName, isOnline, nextRunLabel, planName, retentionLabel, scheduleLabel, summarizeSources } from "@/lib/backup-view";
import { formatBytes, formatDateTime, formatRelative } from "@/lib/format";

const machineStatusMeta: Record<
  MachineOperationalStatus,
  { label: string; color: string; background: string; border: string }
> = {
  online: {
    label: "Online",
    color: "#15803D",
    background: "#DCFCE7",
    border: "#86EFAC",
  },
  offline: {
    label: "Offline",
    color: "#374151",
    background: "#F3F4F6",
    border: "#D1D5DB",
  },
  error: {
    label: "Com erro",
    color: "#B91C1C",
    background: "#FEE2E2",
    border: "#FCA5A5",
  },
  "no-plan": {
    label: "Sem plano",
    color: "#92400E",
    background: "#FEF3C7",
    border: "#FCD34D",
  },
};

const snapshotStatusMeta: Record<
  SnapshotStatus,
  { label: string; color: string; background: string; border: string }
> = {
  COMPLETED: {
    label: "Concluído",
    color: "#15803D",
    background: "#DCFCE7",
    border: "#86EFAC",
  },
  RUNNING: {
    label: "Em execução",
    color: "#5B21B6",
    background: "#EDE9FF",
    border: "#C4B5FD",
  },
  IN_PROGRESS: {
    label: "Em execução",
    color: "#5B21B6",
    background: "#EDE9FF",
    border: "#C4B5FD",
  },
  PROCESSING: {
    label: "Processando",
    color: "#6D28D9",
    background: "#F5F3FF",
    border: "#DDD6FE",
  },
  FAILED: {
    label: "Falhou",
    color: "#B91C1C",
    background: "#FEE2E2",
    border: "#FCA5A5",
  },
};

const snapshotKindMeta: Record<
  SnapshotKind,
  { label: string; color: string; background: string; border: string }
> = {
  COMPLETO: {
    label: "Completo",
    color: "#1D4ED8",
    background: "#DBEAFE",
    border: "#93C5FD",
  },
  INCREMENTAL: {
    label: "Incremental",
    color: "#92400E",
    background: "#FEF3C7",
    border: "#FCD34D",
  },
};

export function Surface({
  children,
  className = "",
  padded = true,
}: {
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <section
      className={`rounded-[24px] border ${padded ? "p-5 md:p-6" : ""} ${className}`}
      style={{
        background: "#FFFFFF",
        borderColor: "#E5E7EB",
      }}
    >
      {children}
    </section>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        {eyebrow ? (
          <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-400">{eyebrow}</p>
        ) : null}
        <h2 className="mt-1 text-xl font-semibold tracking-tight text-gray-900">{title}</h2>
        {description ? <p className="mt-2 max-w-2xl text-sm text-gray-500">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export function MachineStatusPill({ status }: { status: MachineOperationalStatus }) {
  const meta = machineStatusMeta[status];
  return (
    <span
      className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-[11px] font-semibold"
      style={{ color: meta.color, background: meta.background, borderColor: meta.border }}
    >
      <span className="h-2 w-2 rounded-full" style={{ background: meta.color }} />
      {meta.label}
    </span>
  );
}

export function SnapshotStatusPill({ status }: { status: SnapshotStatus }) {
  const meta = snapshotStatusMeta[status];
  return <TagPill label={meta.label} color={meta.color} background={meta.background} border={meta.border} />;
}

export function SnapshotKindPill({ type }: { type: SnapshotKind }) {
  const meta = snapshotKindMeta[type];
  return <TagPill label={meta.label} color={meta.color} background={meta.background} border={meta.border} />;
}

function TagPill({
  label,
  color,
  background,
  border,
}: {
  label: string;
  color: string;
  background: string;
  border: string;
}) {
  return (
    <span
      className="inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-semibold"
      style={{ color, background, borderColor: border }}
    >
      {label}
    </span>
  );
}

export function MachineGlyph({ osName, selected = false }: { osName?: string; selected?: boolean }) {
  const normalized = (osName ?? "").toLowerCase();
  const showLinux = normalized.includes("linux");
  const showWindows = normalized.includes("windows");

  return (
    <div
      className="relative flex h-14 w-14 items-center justify-center rounded-2xl border"
      style={{
        background: selected ? "#EDE9FF" : "#F9FAFB",
        borderColor: selected ? "rgba(123,97,255,0.3)" : "#E5E7EB",
      }}
    >
      {showLinux ? (
        <Image src="/Linux.svg" alt="Linux" width={24} height={24} />
      ) : showWindows ? (
        <Image src="/Windows 11.svg" alt="Windows" width={24} height={24} />
      ) : (
        <svg viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-6 w-6">
          <rect x="3" y="4" width="18" height="12" rx="2" />
          <path d="M8 20h8" />
          <path d="M12 16v4" />
        </svg>
      )}
    </div>
  );
}

export function MetadataStat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-2xl border px-4 py-3" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
      <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">{label}</p>
      <p className="mt-2 text-sm font-semibold text-gray-900">{value}</p>
      {hint ? <p className="mt-1 text-xs text-gray-400">{hint}</p> : null}
    </div>
  );
}

export function MachineCard({
  device,
  plan,
  planLoading,
  status,
  selected,
  snapshotCount,
  latestSnapshot,
  onSelect,
}: {
  device: Device;
  plan: DevicePlan | null;
  planLoading: boolean;
  status: MachineOperationalStatus;
  selected: boolean;
  snapshotCount: number;
  latestSnapshot: Snapshot | null;
  onSelect: () => void;
}) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect();
        }
      }}
      className="w-full rounded-[24px] border p-5 text-left transition-colors duration-200 cursor-pointer focus:outline-none focus:ring-2 focus:ring-[#7B61FF]/30"
      style={{
        background: selected ? "#F5F3FF" : "#FFFFFF",
        borderColor: selected ? "rgba(123,97,255,0.3)" : "#E5E7EB",
      }}
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <MachineGlyph osName={device.osName} selected={selected} />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-lg font-semibold tracking-tight text-gray-900">{deviceName(device)}</h3>
              <MachineStatusPill status={status} />
            </div>
            <p className="mt-1 text-sm text-gray-500">{device.osName || "Sistema operacional não informado"}</p>
            <p className="mt-1 text-xs text-gray-400">{device.id}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Link
            href="/dashboard/machines"
            onClick={(event) => event.stopPropagation()}
            className="inline-flex items-center justify-center rounded-full border px-3 py-2 text-xs font-semibold text-gray-600 transition-colors duration-200 hover:bg-gray-100"
            style={{ borderColor: "#E5E7EB" }}
          >
            Ver backups
          </Link>
          <Link
            href={`/dashboard/protection?device=${encodeURIComponent(device.id)}`}
            onClick={(event) => event.stopPropagation()}
            className="inline-flex items-center justify-center rounded-full px-3 py-2 text-xs font-semibold text-white transition-colors duration-200 hover:opacity-90"
            style={{ background: "#059669" }}
          >
            Plano
          </Link>
        </div>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetadataStat
          label="Plano"
          value={planName(plan, planLoading)}
          hint={plan?.sources?.length ? summarizeSources(plan.sources) : "Sem proteção configurada"}
        />
        <MetadataStat
          label="Último backup"
          value={latestSnapshot ? formatDateTime(latestSnapshot.startedAt) : "Sem histórico"}
          hint={latestSnapshot ? `${snapshotCount} snapshot${snapshotCount === 1 ? "" : "s"}` : "Aguardando primeiro snapshot"}
        />
        <MetadataStat
          label="Último contato"
          value={device.lastSeenAt ? formatRelative(device.lastSeenAt) : "Nunca visto"}
          hint={device.lastSeenAt ? formatDateTime(device.lastSeenAt) : "Agente ainda sem heartbeat"}
        />
        <MetadataStat
          label="Próximo backup"
          value={plan?.scheduleCron ? nextRunLabel(plan.scheduleCron) : "Sem agenda"}
          hint={plan?.scheduleCron ? scheduleLabel(plan.scheduleCron) : "Defina um plano para agendar"}
        />
      </div>
    </div>
  );
}

export function PlanSummary({ plan, planLoading }: { plan: DevicePlan | null; planLoading: boolean }) {
  if (planLoading) {
    return <p className="text-sm text-slate-500">Carregando plano...</p>;
  }
  if (!plan) {
    return (
      <div className="rounded-2xl border px-4 py-4 text-sm text-gray-500" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
        Nenhum plano configurado para esta máquina.
      </div>
    );
  }

  const items = [
    { label: "Tipo", value: planName(plan) },
    { label: "Caminhos protegidos", value: plan.sources.length ? summarizeSources(plan.sources) : "Nenhum caminho" },
    { label: "Frequência", value: scheduleLabel(plan.scheduleCron) },
    { label: "Retenção", value: retentionLabel(plan) },
    { label: "CDP", value: plan.cdpEnabled ? "Ativado" : "Desativado" },
    { label: "Validação", value: plan.validationEnabled ? "Ativada" : "Desativada" },
    { label: "Criptografia", value: plan.encryptionEnabled ? "Ativada" : "Desativada" },
  ];

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <MetadataStat key={item.label} label={item.label} value={item.value} />
      ))}
    </div>
  );
}

export function SnapshotTimelineItem({
  snapshot,
  type,
  active = false,
  action,
}: {
  snapshot: Snapshot;
  type: SnapshotKind;
  active?: boolean;
  action?: ReactNode;
}) {
  return (
    <div className="relative rounded-2xl border px-4 py-4" style={{ borderColor: active ? "rgba(123,97,255,0.3)" : "#E5E7EB", background: active ? "#F5F3FF" : "#F9FAFB" }}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <SnapshotKindPill type={type} />
            <SnapshotStatusPill status={snapshot.status} />
          </div>
          <p className="mt-3 text-sm font-semibold text-gray-900">{formatDateTime(snapshot.startedAt)}</p>
          <p className="mt-1 text-xs text-gray-400">{snapshot.sourcePath}</p>
        </div>
        {action}
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <MetadataStat label="Tamanho" value={formatBytes(snapshot.totalCompressedSize ?? 0)} />
        <MetadataStat label="Arquivos" value={String(snapshot.totalFiles ?? 0)} />
        <MetadataStat label="Origem" value={snapshot.sourcePath.split("/").filter(Boolean).pop() || snapshot.sourcePath || "/"} hint={snapshot.completedAt ? `Finalizado ${formatDateTime(snapshot.completedAt)}` : "Processamento em andamento"} />
      </div>
    </div>
  );
}

export function DeviceIdentity({ device }: { device: Device }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <MachineGlyph osName={device.osName} />
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-gray-900">{deviceName(device)}</p>
        <p className="truncate text-xs text-gray-400">
          {device.osName || "SO não informado"} · {isOnline(device.lastSeenAt) ? "Online" : "Offline"}
        </p>
      </div>
    </div>
  );
}
