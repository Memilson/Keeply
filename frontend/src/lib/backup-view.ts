import type { Device, DevicePlan, Snapshot, SnapshotStatus } from "@/lib/api";

export type SnapshotKind = "COMPLETO" | "INCREMENTAL";
export type MachineOperationalStatus = "online" | "offline" | "error" | "no-plan";

export function deviceName(device: Pick<Device, "name" | "hostname">): string {
  return device.name || device.hostname || "Máquina";
}

export function isMobileDevice(device: Pick<Device, "name" | "hostname" | "osName">): boolean {
  const probe = `${device.name ?? ""} ${device.hostname ?? ""} ${device.osName ?? ""}`.toLowerCase();
  return ["android", "ios", "iphone", "ipad", "celular", "mobile", "phone"].some((term) =>
    probe.includes(term),
  );
}

export function isOnline(lastSeenAt?: string): boolean {
  if (!lastSeenAt) return false;
  const seen = new Date(lastSeenAt).getTime();
  if (Number.isNaN(seen)) return false;
  return Date.now() - seen <= 15 * 60 * 1000;
}

export function planName(plan: DevicePlan | null | undefined, loading = false): string {
  if (loading) return "Carregando...";
  if (!plan) return "Sem plano";
  return plan.planType === "CUSTOM" ? "Personalizado" : "Padrão";
}

export function scheduleLabel(cron: string | null | undefined): string {
  if (!cron || !cron.trim()) return "Sem agenda";
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 5) return `Cron ${cron}`;
  const minute = parts[0].padStart(2, "0");
  const hour = parts[1].padStart(2, "0");
  if (parts[2] === "*" && parts[3] === "*" && parts[4] === "*") {
    return `Todos os dias às ${hour}:${minute}`;
  }
  return `Cron ${cron}`;
}

export function nextRunLabel(cron: string | null | undefined): string {
  if (!cron || !cron.trim()) return "Sem agenda";
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 5) return scheduleLabel(cron);
  if (!(parts[2] === "*" && parts[3] === "*" && parts[4] === "*")) {
    return scheduleLabel(cron);
  }

  const minute = Number(parts[0]);
  const hour = Number(parts[1]);
  if (Number.isNaN(minute) || Number.isNaN(hour)) return scheduleLabel(cron);

  const next = new Date();
  next.setSeconds(0, 0);
  next.setHours(hour, minute, 0, 0);
  if (next.getTime() <= Date.now()) {
    next.setDate(next.getDate() + 1);
  }

  return next.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function retentionLabel(plan: DevicePlan | null | undefined): string {
  if (!plan) return "Sem retenção configurada";
  if (plan.retentionMode === "KEEP_DAYS" && plan.retentionDays && plan.retentionDays > 0) {
    return `Manter por ${plan.retentionDays} dia${plan.retentionDays === 1 ? "" : "s"}`;
  }
  return "Manter todos os snapshots";
}

export function snapshotStatusLabel(status: SnapshotStatus): string {
  const labels: Record<SnapshotStatus, string> = {
    RUNNING: "Executando",
    IN_PROGRESS: "Em progresso",
    PROCESSING: "Processando",
    COMPLETED: "Concluído",
    FAILED: "Falhou",
  };
  return labels[status] ?? status;
}

export function inferSnapshotType(snapshots: Snapshot[], current: Snapshot): SnapshotKind {
  const currentStartedAt = new Date(current.startedAt).getTime();
  const hasOlderFromSameSource = snapshots.some((snapshot) => {
    if (snapshot.id === current.id) return false;
    if (snapshot.deviceId !== current.deviceId) return false;
    if (snapshot.sourcePath !== current.sourcePath) return false;
    return new Date(snapshot.startedAt).getTime() < currentStartedAt;
  });
  return hasOlderFromSameSource ? "INCREMENTAL" : "COMPLETO";
}

export function getMachineStatus(
  plan: DevicePlan | null | undefined,
  planLoading: boolean,
  lastSeenAt: string | undefined,
  snapshots: Snapshot[],
): MachineOperationalStatus {
  if (!planLoading && (!plan || plan.sources.length === 0)) return "no-plan";
  const latestSnapshot = [...snapshots].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
  )[0];
  if (latestSnapshot?.status === "FAILED") return "error";
  return isOnline(lastSeenAt) ? "online" : "offline";
}

export function summarizeSources(sources: string[]): string {
  if (sources.length === 0) return "Nenhum caminho protegido";
  if (sources.length === 1) return sources[0];
  return `${sources[0]} +${sources.length - 1}`;
}
