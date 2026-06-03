"use client";

import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Topbar } from "@/components/Topbar";
import { api, type Device, type DevicePlan, type Snapshot } from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };
type MachineStatus = "protected" | "error" | "offline" | "no-plan";
type DetailTab = "details" | "storage" | "paths" | "plan";

type MachineRow = {
  device: DeviceWithPlan;
  snapshots: Snapshot[];
  latestSnapshot: Snapshot | null;
  status: MachineStatus;
  usageBytes: number;
};

const statusCopy: Record<MachineStatus, { label: string; icon: string; className: string }> = {
  protected: {
    label: "Protegida",
    icon: "bi-shield-check",
    className: "border-emerald-200 bg-emerald-50 text-emerald-700",
  },
  error: {
    label: "Com erro",
    icon: "bi-exclamation-triangle",
    className: "border-red-200 bg-red-50 text-red-700",
  },
  offline: {
    label: "Offline",
    icon: "bi-wifi-off",
    className: "border-slate-200 bg-slate-100 text-slate-600",
  },
  "no-plan": {
    label: "Sem plano",
    icon: "bi-slash-circle",
    className: "border-amber-200 bg-amber-50 text-amber-700",
  },
};

export default function MachinesPage() {
  const [devices, setDevices] = useState<DeviceWithPlan[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [deviceList, snapshotList] = await Promise.all([
        api<Device[]>("/api/devices"),
        api<Snapshot[]>("/api/snapshots"),
      ]);

      const baseDevices = (deviceList ?? []).map((device) => ({
        ...device,
        plan: null,
        planLoading: true,
      }));

      setDevices(baseDevices);
      setSnapshots(snapshotList ?? []);
      setSelectedDeviceId((current) => {
        if (current && baseDevices.some((device) => device.id === current)) return current;
        return baseDevices[0]?.id ?? "";
      });

      await Promise.all(
        baseDevices.map(async (device) => {
          try {
            const plan = await api<DevicePlan>(`/api/devices/${device.id}/plan`);
            setDevices((prev) =>
              prev.map((item) => (item.id === device.id ? { ...item, plan, planLoading: false } : item))
            );
          } catch {
            setDevices((prev) =>
              prev.map((item) => (item.id === device.id ? { ...item, planLoading: false } : item))
            );
          }
        })
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao carregar máquinas.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadData();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadData]);

  const snapshotsByDevice = useMemo(() => {
    const map = new Map<string, Snapshot[]>();
    for (const snapshot of snapshots) {
      const list = map.get(snapshot.deviceId) ?? [];
      list.push(snapshot);
      map.set(snapshot.deviceId, list);
    }
    for (const [, list] of map) {
      list.sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }
    return map;
  }, [snapshots]);

  const rows = useMemo<MachineRow[]>(() => {
    return devices.map((device) => {
      const deviceSnapshots = snapshotsByDevice.get(device.id) ?? [];
      return {
        device,
        snapshots: deviceSnapshots,
        latestSnapshot: deviceSnapshots[0] ?? null,
        status: getMachineStatus(device, deviceSnapshots),
        usageBytes: deviceSnapshots.reduce((acc, snapshot) => acc + (snapshot.totalCompressedSize ?? 0), 0),
      };
    });
  }, [devices, snapshotsByDevice]);

  const selectedRow = rows.find((row) => row.device.id === selectedDeviceId) ?? rows[0] ?? null;

  return (
    <div className="dashboard-page" style={{ padding: "28px 0 0" }}>
      <Topbar title="Máquinas" />
      {error && (
        <div className="mx-3 kp-alert-error xl:mx-4">
          <span className="font-semibold">Erro:</span> {error}
        </div>
      )}

      <div className="grid xl:grid-cols-[minmax(0,1fr)_360px]">
        <section
          className="overflow-hidden"
          style={{ background: "transparent", border: "0", borderRadius: "0", boxShadow: "none" }}
        >
          <div className="overflow-x-auto">
            <table className="min-w-[980px] w-full border-collapse text-left">
              <thead>
                <tr className="text-xs uppercase tracking-wide" style={{ color: "var(--kp-muted)", background: "#FAFAFE" }}>
                  <Th>Máquina</Th>
                  <Th>Status</Th>
                  <Th>Sistema</Th>
                  <Th>Plano</Th>
                  <Th>Último contato</Th>
                  <Th>Snapshots</Th>
                  <Th>Uso</Th>
                  <Th>Ações</Th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={8} className="px-5 py-10 text-center text-sm" style={{ color: "var(--kp-muted)" }}>
                      Carregando máquinas...
                    </td>
                  </tr>
                ) : rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-5 py-10 text-center text-sm" style={{ color: "var(--kp-muted)" }}>
                      Nenhuma máquina encontrada.
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => {
                    const selected = row.device.id === selectedRow?.device.id;
                    return (
                      <tr
                        key={row.device.id}
                        onClick={() => setSelectedDeviceId(row.device.id)}
                        className="cursor-pointer border-t transition"
                        style={{
                          borderColor: "var(--kp-border-soft)",
                          background: selected ? "var(--kp-primary-tint)" : "#FFFFFF",
                          boxShadow: selected ? "inset 3px 0 0 #7B61FF" : "none",
                        }}
                      >
                        <Td>
                          <MachineCell device={row.device} status={row.status} selected={selected} />
                        </Td>
                        <Td>
                          <StatusPill status={row.status} />
                        </Td>
                        <Td>
                          <SystemCell osName={row.device.osName} />
                        </Td>
                        <Td>{planName(row.device)}</Td>
                        <Td>{row.device.lastSeenAt ? formatDateTime(row.device.lastSeenAt) : "Nunca visto"}</Td>
                        <Td>{row.snapshots.length}</Td>
                        <Td>{formatBytes(row.usageBytes)}</Td>
                        <Td>
                          <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
                            <Link
                              href={`/dashboard/backups?device=${encodeURIComponent(row.device.id)}`}
                              className="grid h-9 w-9 place-items-center rounded-lg border bg-white"
                              style={{ borderColor: "var(--kp-border)", color: "var(--kp-primary-dark)" }}
                              title="Ver snapshots"
                            >
                              <i className="bi bi-clock-history" aria-hidden="true" />
                            </Link>
                            <Link
                              href={`/dashboard/protection?device=${encodeURIComponent(row.device.id)}`}
                              className="grid h-9 w-9 place-items-center rounded-lg border bg-white"
                              style={{ borderColor: "var(--kp-border)", color: "var(--kp-primary-dark)" }}
                              title="Configurar plano"
                            >
                              <i className="bi bi-gear" aria-hidden="true" />
                            </Link>
                          </div>
                        </Td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>

        <MachineDetails row={selectedRow} />
      </div>
    </div>
  );
}

function MachineDetails({ row }: { row: MachineRow | null }) {
  const [activeTab, setActiveTab] = useState<DetailTab>("details");

  if (!row) {
    return (
      <aside className="px-4 py-8 text-sm xl:px-5" style={{ color: "var(--kp-muted)", borderLeft: "1px solid #E5E7EB" }}>
        Selecione uma máquina para ver os detalhes.
      </aside>
    );
  }

  const { device, snapshots, latestSnapshot, usageBytes, status } = row;
  const online = isOnline(device.lastSeenAt);

  return (
    <aside
      className="overflow-hidden xl:sticky xl:top-0 xl:self-start"
      style={{
        background: "transparent",
        borderTop: "0",
        borderRight: "0",
        borderBottom: "0",
        borderRadius: "0",
        boxShadow: "none",
        borderLeft: "1px solid #E5E7EB",
        padding: "20px 0 28px",
      }}
    >
      <div className="px-5 py-5" style={{ borderBottom: "1px solid var(--kp-border-soft)" }}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-bold uppercase tracking-wide" style={{ color: "var(--kp-muted)" }}>
              Máquina selecionada
            </p>
            <h2 className="mt-1 truncate text-xl font-extrabold" style={{ color: "var(--kp-ink)" }}>
              {deviceName(device)}
            </h2>
            <p className="mt-1 truncate text-sm" style={{ color: "var(--kp-muted)" }}>
              {device.osName || "Sistema operacional não informado"}
            </p>
          </div>
          <span
            className="flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold"
            style={{
              borderColor: online ? "#A7F3D0" : "#E2E8F0",
              background: online ? "#ECFDF5" : "#F8FAFC",
              color: online ? "#047857" : "#64748B",
            }}
          >
            <span className="h-2 w-2 rounded-full" style={{ background: online ? "#10B981" : "#94A3B8" }} />
            {online ? "Online" : "Offline"}
          </span>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-2">
          <Link href={`/dashboard/backups?device=${encodeURIComponent(device.id)}`} className="kp-btn kp-btn-secondary">
            <i className="bi bi-clock-history" aria-hidden="true" />
            Snapshots
          </Link>
          <Link href={`/dashboard/protection?device=${encodeURIComponent(device.id)}`} className="kp-btn kp-btn-primary">
            <i className="bi bi-gear" aria-hidden="true" />
            Plano
          </Link>
        </div>
      </div>

      <div className="px-5 pt-4">
        <div className="divide-y rounded-xl border" style={{ borderColor: "var(--kp-border-soft)", background: "#FFFFFF" }}>
          <AccordionItem active={activeTab === "details"} icon="bi-info-circle" label="Detalhes" onClick={() => setActiveTab("details")}>
            <div className="space-y-3">
              <InfoRow label="Status" value={statusCopy[status].label} />
              <InfoRow label="ID do dispositivo" value={device.id} />
              <InfoRow label="Instalação" value={device.deviceInstallationId ?? "Não informado"} />
              <InfoRow label="Agente" value={device.agentVersion ?? "Não informado"} />
              <InfoRow label="Último contato" value={device.lastSeenAt ? formatDateTime(device.lastSeenAt) : "Nunca visto"} />
            </div>
          </AccordionItem>

          <AccordionItem active={activeTab === "storage"} icon="bi-hdd-stack" label="Armazenamento" onClick={() => setActiveTab("storage")}>
            <div className="grid grid-cols-2 gap-3">
              <MiniStat label="Snapshots" value={`${snapshots.length}`} />
              <MiniStat label="Uso" value={formatBytes(usageBytes)} />
            </div>
            <InfoRow
              className="mt-3"
              label="Último snapshot"
              value={latestSnapshot ? `${snapshotStatusLabel(latestSnapshot.status)} · ${formatDateTime(latestSnapshot.startedAt)}` : "Nenhum snapshot"}
            />
          </AccordionItem>

          <AccordionItem active={activeTab === "paths"} icon="bi-folder-check" label="Caminhos protegidos" onClick={() => setActiveTab("paths")}>
            {device.planLoading ? (
              <p className="text-sm" style={{ color: "var(--kp-muted)" }}>
                Carregando plano...
              </p>
            ) : !device.plan || device.plan.sources.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--kp-muted)" }}>
                Nenhum caminho configurado.
              </p>
            ) : (
              <div className="space-y-2">
                {device.plan.sources.map((source) => (
                  <div key={source} className="break-all rounded-lg px-3 py-2 text-sm" style={{ background: "#F8FAFC", color: "#334155" }}>
                    {source}
                  </div>
                ))}
              </div>
            )}
          </AccordionItem>

          <AccordionItem active={activeTab === "plan"} icon="bi-calendar2-check" label="Plano configurado" onClick={() => setActiveTab("plan")}>
            {device.planLoading ? (
              <p className="text-sm" style={{ color: "var(--kp-muted)" }}>
                Carregando plano...
              </p>
            ) : !device.plan ? (
              <p className="text-sm" style={{ color: "var(--kp-muted)" }}>
                Nenhum plano configurado.
              </p>
            ) : (
              <div className="space-y-3">
                <InfoRow label="Tipo" value={planName(device)} />
                <InfoRow label="Frequência" value={scheduleLabel(device.plan.scheduleCron ?? "")} />
                <InfoRow label="Retenção" value={retentionLabel(device.plan)} />
                <InfoRow label="CDP" value={device.plan.cdpEnabled ? "Ativado" : "Desativado"} />
                <InfoRow label="Criptografia" value={device.plan.encryptionEnabled ? "AES-256 · SHA-256" : "Desativada"} />
              </div>
            )}
          </AccordionItem>
        </div>
      </div>
    </aside>
  );
}

function AccordionItem({
  active,
  icon,
  label,
  onClick,
  children,
}: {
  active: boolean;
  icon: string;
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <section>
      <button
        type="button"
        onClick={onClick}
        className="flex min-h-11 w-full min-w-0 items-center justify-between gap-3 px-3 py-2.5 text-left text-sm font-bold transition-colors duration-200 first:rounded-t-xl last:rounded-b-xl"
        style={{
          background: active ? "var(--kp-primary-tint)" : "#FFFFFF",
          color: active ? "var(--kp-primary-dark)" : "var(--kp-muted)",
        }}
        aria-expanded={active}
      >
        <span className="flex min-w-0 items-center gap-2">
          <i className={`bi ${icon} shrink-0`} aria-hidden="true" />
          <span className="truncate">{label}</span>
        </span>
        <i
          className="bi bi-chevron-right shrink-0 text-xs transition-transform duration-200 ease-out"
          style={{ transform: active ? "rotate(90deg)" : "rotate(0deg)" }}
          aria-hidden="true"
        />
      </button>
      <div
        className="grid transition-[grid-template-rows,opacity] duration-300 ease-out"
        style={{
          gridTemplateRows: active ? "1fr" : "0fr",
          opacity: active ? 1 : 0,
        }}
        aria-hidden={!active}
      >
        <div className="overflow-hidden">
          <div className="px-3 pb-4 pt-3">{children}</div>
        </div>
      </div>
    </section>
  );
}

function StatusPill({ status }: { status: MachineStatus }) {
  const copy = statusCopy[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold ${copy.className}`}>
      <i className={`bi ${copy.icon}`} aria-hidden="true" />
      {copy.label}
    </span>
  );
}

function MachineCell({
  device,
  status,
  selected,
}: {
  device: Device;
  status: MachineStatus;
  selected: boolean;
}) {
  const dotColor = {
    protected: "#22C55E",
    error: "#EF4444",
    offline: "#94A3B8",
    "no-plan": "#F59E0B",
  }[status];

  return (
    <div className="flex min-w-0 items-center gap-3">
      <span
        className="relative grid h-11 w-11 shrink-0 place-items-center rounded-xl border"
        style={{
          background: "#FFFFFF",
          borderColor: selected ? "#D9DDED" : "#E6EAF5",
          color: "#B8BFD3",
          boxShadow: "0 1px 2px rgba(15, 23, 42, 0.04)",
        }}
      >
        <i className="bi bi-pc-display-horizontal text-[22px]" aria-hidden="true" />
        <span
          className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-white"
          style={{ background: dotColor }}
          aria-hidden="true"
        />
      </span>
      <div className="min-w-0">
        <p className="truncate text-[15px]" style={{ color: "#25324B", fontWeight: 800, lineHeight: 1.15 }}>
          {deviceName(device)}
        </p>
        <p className="mt-1 truncate text-[11px]" style={{ color: "#98A2B3", fontWeight: 600, lineHeight: 1.2 }}>
          {device.id}
        </p>
      </div>
    </div>
  );
}

function SystemCell({ osName }: { osName?: string }) {
  if (!osName) return <>Nao informado</>;

  const normalizedOs = osName.toLowerCase();

  if (normalizedOs.includes("linux")) {
    return (
      <div className="flex items-center gap-2">
        <Image src="/Linux.svg" alt="Linux" width={18} height={18} />
        <span>Linux</span>
      </div>
    );
  }

  if (normalizedOs.includes("windows")) {
    return (
      <div className="flex items-center gap-2">
        <Image src="/Windows 11.svg" alt="Windows" width={18} height={18} />
        <span>{osName}</span>
      </div>
    );
  }

  return <>{osName}</>;
}

function Th({ children }: { children: ReactNode }) {
  return <th className="px-5 py-3 font-extrabold">{children}</th>;
}

function Td({ children }: { children: ReactNode }) {
  return (
    <td className="px-5 py-4 text-sm align-middle" style={{ color: "var(--kp-muted)" }}>
      {children}
    </td>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border px-3 py-3" style={{ borderColor: "var(--kp-border-soft)", background: "#FAFAFE" }}>
      <p className="text-xs font-semibold" style={{ color: "var(--kp-muted)" }}>
        {label}
      </p>
      <p className="mt-1 text-sm font-extrabold" style={{ color: "var(--kp-ink)" }}>
        {value}
      </p>
    </div>
  );
}

function InfoRow({ label, value, className = "" }: { label: string; value: string; className?: string }) {
  return (
    <div className={`min-w-0 ${className}`}>
      <p className="text-xs font-semibold" style={{ color: "var(--kp-muted)" }}>
        {label}
      </p>
      <p className="mt-1 break-words text-sm font-semibold" style={{ color: "#334155" }}>
        {value}
      </p>
    </div>
  );
}

function deviceName(device: Device) {
  return device.name || device.hostname || "Máquina";
}

function isOnline(lastSeenAt?: string) {
  if (!lastSeenAt) return false;
  const seen = new Date(lastSeenAt).getTime();
  if (Number.isNaN(seen)) return false;
  return Date.now() - seen <= 15 * 60 * 1000;
}

function getMachineStatus(device: DeviceWithPlan, snapshots: Snapshot[]): MachineStatus {
  if (!isOnline(device.lastSeenAt)) return "offline";
  if (!device.planLoading && (!device.plan || device.plan.sources.length === 0)) return "no-plan";
  const latestSnapshot = snapshots[0];
  if (latestSnapshot?.status === "FAILED") return "error";
  return "protected";
}

function planName(device: DeviceWithPlan) {
  if (device.planLoading) return "Carregando...";
  if (!device.plan) return "Sem plano";
  return device.plan.planType === "CUSTOM" ? "Personalizado" : "Padrão";
}

function scheduleLabel(cron: string) {
  if (!cron || !cron.trim()) return "Não configurado";
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 5) return "Não configurado";
  const minute = parts[0].padStart(2, "0");
  const hour = parts[1].padStart(2, "0");
  if (parts[2] === "*" && parts[3] === "*" && parts[4] === "*") {
    return `Todos os dias às ${hour}:${minute}`;
  }
  return `Cron ${cron}`;
}

function retentionLabel(plan: DevicePlan) {
  if (plan.retentionMode === "KEEP_DAYS" && plan.retentionDays && plan.retentionDays > 0) {
    return `Manter por ${plan.retentionDays} dia${plan.retentionDays === 1 ? "" : "s"}`;
  }
  return "Manter todos os snapshots";
}

function snapshotStatusLabel(status: Snapshot["status"]) {
  const labels: Record<Snapshot["status"], string> = {
    RUNNING: "Executando",
    IN_PROGRESS: "Em progresso",
    PROCESSING: "Processando",
    COMPLETED: "Concluído",
    FAILED: "Falhou",
  };
  return labels[status] ?? status;
}
