"use client";

import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Topbar } from "@/components/Topbar";
import { api, type Device, type DevicePlan, type PagedResponse, type Snapshot } from "@/lib/api";
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

const statusMeta: Record<MachineStatus, { label: string; bg: string; text: string; dot: string }> = {
  protected: { label: "Protegida", bg: "rgba(16,185,129,0.15)", text: "#10B981", dot: "#10B981" },
  error: { label: "Com erro", bg: "rgba(239,68,68,0.15)", text: "#EF4444", dot: "#EF4444" },
  offline: { label: "Offline", bg: "rgba(148,163,184,0.12)", text: "#94A3B8", dot: "#94A3B8" },
  "no-plan": { label: "Sem plano", bg: "rgba(245,158,11,0.15)", text: "#F59E0B", dot: "#F59E0B" },
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
      const [deviceList, snapshotPage] = await Promise.all([
        api<Device[]>("/api/devices"),
        api<PagedResponse<Snapshot>>("/api/snapshots"),
      ]);

      const baseDevices = (deviceList ?? []).map((device) => ({
        ...device,
        plan: null,
        planLoading: true,
      }));

      setDevices(baseDevices);
      setSnapshots(snapshotPage?.items ?? []);
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
    const timer = window.setTimeout(() => { void loadData(); }, 0);
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
    <>
      <Topbar title="Máquinas" />
      <div className="flex-1 overflow-hidden">
        {error && (
          <div
            className="mx-6 mt-4 rounded-xl border px-4 py-3 text-sm text-[#EF4444]"
            style={{ background: "rgba(239,68,68,0.08)", borderColor: "rgba(239,68,68,0.2)" }}
          >
            <span className="font-semibold">Erro:</span> {error}
          </div>
        )}

        <div className="grid xl:grid-cols-[minmax(0,1fr)_340px] h-full">
          {/* Table */}
          <section className="overflow-x-auto">
            <table className="min-w-[900px] w-full border-collapse text-left">
              <thead>
                <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
                  {["Máquina", "Status", "Sistema", "Plano", "Último contato", "Snapshots", "Uso", "Ações"].map((h) => (
                    <th
                      key={h}
                      className="px-5 py-3 text-[10px] font-bold uppercase tracking-widest text-slate-500 bg-white/5"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={8} className="px-5 py-10 text-center text-sm text-slate-500">
                      Carregando máquinas...
                    </td>
                  </tr>
                ) : rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-5 py-10 text-center text-sm text-slate-500">
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
                        className="cursor-pointer transition-colors duration-200"
                        style={{
                          borderTop: "1px solid rgba(255,255,255,0.04)",
                          background: selected ? "rgba(123,97,255,0.08)" : "transparent",
                          boxShadow: selected ? "inset 3px 0 0 #7B61FF" : "none",
                        }}
                      >
                        <td className="px-5 py-3.5">
                          <MachineCell device={row.device} status={row.status} selected={selected} />
                        </td>
                        <td className="px-5 py-3.5">
                          <StatusPill status={row.status} />
                        </td>
                        <td className="px-5 py-3.5">
                          <SystemCell osName={row.device.osName} />
                        </td>
                        <td className="px-5 py-3.5 text-xs text-slate-400">{planName(row.device)}</td>
                        <td className="px-5 py-3.5 text-xs text-slate-400">
                          {row.device.lastSeenAt ? formatDateTime(row.device.lastSeenAt) : "Nunca visto"}
                        </td>
                        <td className="px-5 py-3.5 text-xs text-slate-400 tabular-nums">{row.snapshots.length}</td>
                        <td className="px-5 py-3.5 text-xs text-slate-400 tabular-nums">{formatBytes(row.usageBytes)}</td>
                        <td className="px-5 py-3.5">
                          <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                            <Link
                              href={`/dashboard/backups?device=${encodeURIComponent(row.device.id)}`}
                              className="grid h-8 w-8 place-items-center rounded-lg border transition-colors duration-200 cursor-pointer"
                              style={{ borderColor: "rgba(255,255,255,0.1)", color: "#A78BFA", background: "rgba(123,97,255,0.08)" }}
                              title="Ver snapshots"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4">
                                <circle cx="12" cy="12" r="9" /><polyline points="12 7 12 12 15 15" />
                              </svg>
                            </Link>
                            <Link
                              href={`/dashboard/protection?device=${encodeURIComponent(row.device.id)}`}
                              className="grid h-8 w-8 place-items-center rounded-lg border transition-colors duration-200 cursor-pointer"
                              style={{ borderColor: "rgba(255,255,255,0.1)", color: "#A78BFA", background: "rgba(123,97,255,0.08)" }}
                              title="Configurar plano"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4">
                                <circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
                              </svg>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </section>

          {/* Detail Panel */}
          <MachineDetails row={selectedRow} />
        </div>
      </div>
    </>
  );
}

function MachineDetails({ row }: { row: MachineRow | null }) {
  const [activeTab, setActiveTab] = useState<DetailTab>("details");

  if (!row) {
    return (
      <aside
        className="px-5 py-8 text-sm text-slate-500"
        style={{ borderLeft: "1px solid rgba(255,255,255,0.08)" }}
      >
        Selecione uma máquina para ver os detalhes.
      </aside>
    );
  }

  const { device, snapshots, latestSnapshot, usageBytes, status } = row;
  const online = isOnline(device.lastSeenAt);

  return (
    <aside
      className="overflow-y-auto xl:sticky xl:top-0 xl:self-start"
      style={{
        borderLeft: "1px solid rgba(255,255,255,0.08)",
        padding: "20px 0 28px",
        maxHeight: "calc(100vh - 60px)",
      }}
    >
      <div className="px-5 pb-4" style={{ borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">
              Máquina selecionada
            </p>
            <h2 className="mt-1 truncate text-lg font-black text-white">{deviceName(device)}</h2>
            <p className="mt-0.5 truncate text-sm text-slate-500">
              {device.osName || "Sistema operacional não informado"}
            </p>
          </div>
          <span
            className="flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold"
            style={{
              borderColor: online ? "rgba(16,185,129,0.3)" : "rgba(148,163,184,0.2)",
              background: online ? "rgba(16,185,129,0.1)" : "rgba(148,163,184,0.08)",
              color: online ? "#10B981" : "#94A3B8",
            }}
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ background: online ? "#10B981" : "#94A3B8" }}
            />
            {online ? "Online" : "Offline"}
          </span>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-2">
          <Link
            href={`/dashboard/backups?device=${encodeURIComponent(device.id)}`}
            className="flex items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold text-slate-300 hover:text-white hover:bg-white/5 transition-colors duration-200 cursor-pointer"
            style={{ borderColor: "rgba(255,255,255,0.1)" }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-3.5 w-3.5">
              <circle cx="12" cy="12" r="9" /><polyline points="12 7 12 12 15 15" />
            </svg>
            Snapshots
          </Link>
          <Link
            href={`/dashboard/protection?device=${encodeURIComponent(device.id)}`}
            className="flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold text-white transition-colors duration-200 cursor-pointer"
            style={{ background: "#7B61FF" }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-3.5 w-3.5">
              <circle cx="12" cy="12" r="3" />
            </svg>
            Plano
          </Link>
        </div>
      </div>

      <div className="px-5 pt-4">
        <div
          className="rounded-xl border overflow-hidden"
          style={{ borderColor: "rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.02)" }}
        >
          <AccordionItem
            active={activeTab === "details"}
            label="Detalhes"
            onClick={() => setActiveTab("details")}
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
                <circle cx="12" cy="12" r="9" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            }
          >
            <div className="space-y-3">
              <InfoRow label="Status" value={statusMeta[status].label} />
              <InfoRow label="ID do dispositivo" value={device.id} />
              <InfoRow label="Instalação" value={device.deviceInstallationId ?? "Não informado"} />
              <InfoRow label="Agente" value={device.agentVersion ?? "Não informado"} />
              <InfoRow label="Último contato" value={device.lastSeenAt ? formatDateTime(device.lastSeenAt) : "Nunca visto"} />
            </div>
          </AccordionItem>

          <AccordionItem
            active={activeTab === "storage"}
            label="Armazenamento"
            onClick={() => setActiveTab("storage")}
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
                <rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" />
              </svg>
            }
          >
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

          <AccordionItem
            active={activeTab === "paths"}
            label="Caminhos protegidos"
            onClick={() => setActiveTab("paths")}
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
              </svg>
            }
          >
            {device.planLoading ? (
              <p className="text-sm text-slate-500">Carregando plano...</p>
            ) : !device.plan || device.plan.sources.length === 0 ? (
              <p className="text-sm text-slate-500">Nenhum caminho configurado.</p>
            ) : (
              <div className="space-y-2">
                {device.plan.sources.map((source) => (
                  <div
                    key={source}
                    className="break-all rounded-lg px-3 py-2 text-xs text-slate-400"
                    style={{ background: "rgba(255,255,255,0.04)" }}
                  >
                    {source}
                  </div>
                ))}
              </div>
            )}
          </AccordionItem>

          <AccordionItem
            active={activeTab === "plan"}
            label="Plano configurado"
            onClick={() => setActiveTab("plan")}
            icon={
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
                <rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
              </svg>
            }
          >
            {device.planLoading ? (
              <p className="text-sm text-slate-500">Carregando plano...</p>
            ) : !device.plan ? (
              <p className="text-sm text-slate-500">Nenhum plano configurado.</p>
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
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <section style={{ borderTop: "1px solid rgba(255,255,255,0.06)" }}>
      <button
        type="button"
        onClick={onClick}
        className="flex min-h-11 w-full min-w-0 items-center justify-between gap-3 px-3 py-2.5 text-left text-sm font-bold transition-colors duration-200 cursor-pointer"
        style={{
          background: active ? "rgba(123,97,255,0.12)" : "transparent",
          color: active ? "#A78BFA" : "#64748B",
        }}
        aria-expanded={active}
      >
        <span className="flex min-w-0 items-center gap-2">
          {icon}
          <span className="truncate">{label}</span>
        </span>
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-3.5 w-3.5 shrink-0 transition-transform duration-200"
          style={{ transform: active ? "rotate(90deg)" : "rotate(0deg)" }}
        >
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>
      <div
        className="grid transition-[grid-template-rows,opacity] duration-300 ease-out"
        style={{ gridTemplateRows: active ? "1fr" : "0fr", opacity: active ? 1 : 0 }}
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
  const meta = statusMeta[status];
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase whitespace-nowrap"
      style={{ background: meta.bg, color: meta.text }}
    >
      <span className="h-1.5 w-1.5 rounded-full inline-block shrink-0" style={{ background: meta.dot }} />
      {meta.label}
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
  const dotColor = statusMeta[status].dot;

  return (
    <div className="flex min-w-0 items-center gap-3">
      <span
        className="relative grid h-10 w-10 shrink-0 place-items-center rounded-xl border"
        style={{
          background: "rgba(123,97,255,0.08)",
          borderColor: selected ? "rgba(123,97,255,0.4)" : "rgba(255,255,255,0.08)",
          color: "#6B7280",
        }}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
          <rect x="2" y="3" width="20" height="14" rx="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" />
        </svg>
        <span
          className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2"
          style={{ background: dotColor, borderColor: "#0D0C1A" }}
          aria-hidden="true"
        />
      </span>
      <div className="min-w-0">
        <p className="truncate text-sm font-bold text-white leading-tight">{deviceName(device)}</p>
        <p className="mt-0.5 truncate text-[11px] text-slate-600 leading-tight">{device.id}</p>
      </div>
    </div>
  );
}

function SystemCell({ osName }: { osName?: string }) {
  if (!osName) return <span className="text-xs text-slate-500">Não informado</span>;

  const normalizedOs = osName.toLowerCase();

  if (normalizedOs.includes("linux")) {
    return (
      <div className="flex items-center gap-2">
        <Image src="/Linux.svg" alt="Linux" width={16} height={16} />
        <span className="text-xs text-slate-400">Linux</span>
      </div>
    );
  }

  if (normalizedOs.includes("windows")) {
    return (
      <div className="flex items-center gap-2">
        <Image src="/Windows 11.svg" alt="Windows" width={16} height={16} />
        <span className="text-xs text-slate-400">{osName}</span>
      </div>
    );
  }

  return <span className="text-xs text-slate-400">{osName}</span>;
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div
      className="rounded-xl border px-3 py-3"
      style={{ borderColor: "rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.03)" }}
    >
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-bold text-white">{value}</p>
    </div>
  );
}

function InfoRow({ label, value, className = "" }: { label: string; value: string; className?: string }) {
  return (
    <div className={`min-w-0 ${className}`}>
      <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-600">{label}</p>
      <p className="mt-0.5 break-words text-xs font-semibold text-slate-300">{value}</p>
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
