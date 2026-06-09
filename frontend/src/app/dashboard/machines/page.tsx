"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  MachineStatusPill,
  SnapshotKindPill,
  SnapshotStatusPill,
} from "@/components/backup-ui";
import { Topbar } from "@/components/Topbar";
import { api, type Device, type DevicePlan, type PagedResponse, type Snapshot } from "@/lib/api";
import {
  deviceName,
  getMachineStatus,
  inferSnapshotType,
  planName,
  retentionLabel,
  scheduleLabel,
} from "@/lib/backup-view";
import { formatBytes, formatDateTime, formatRelative } from "@/lib/format";

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };
type MachinePanelTab = "summary" | "plan" | "snapshots";

type MachineViewModel = {
  device: DeviceWithPlan;
  snapshots: Snapshot[];
  latestSnapshot: Snapshot | null;
  status: ReturnType<typeof getMachineStatus>;
  usageBytes: number;
};

export default function MachinesPage() {
  const [devices, setDevices] = useState<DeviceWithPlan[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [activeTab, setActiveTab] = useState<MachinePanelTab>("summary");
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
        return "";
      });

      await Promise.all(
        baseDevices.map(async (device) => {
          try {
            const plan = await api<DevicePlan>(`/api/devices/${device.id}/plan`);
            setDevices((current) =>
              current.map((item) => (item.id === device.id ? { ...item, plan, planLoading: false } : item)),
            );
          } catch {
            setDevices((current) =>
              current.map((item) => (item.id === device.id ? { ...item, planLoading: false } : item)),
            );
          }
        }),
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha ao carregar máquinas.");
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
    const grouped = new Map<string, Snapshot[]>();
    for (const snapshot of snapshots) {
      const list = grouped.get(snapshot.deviceId) ?? [];
      list.push(snapshot);
      grouped.set(snapshot.deviceId, list);
    }
    for (const [, list] of grouped) {
      list.sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }
    return grouped;
  }, [snapshots]);

  const machines = useMemo<MachineViewModel[]>(() => {
    return devices.map((device) => {
      const deviceSnapshots = snapshotsByDevice.get(device.id) ?? [];
      return {
        device,
        snapshots: deviceSnapshots,
        latestSnapshot: deviceSnapshots[0] ?? null,
        status: getMachineStatus(device.plan, device.planLoading, device.lastSeenAt, deviceSnapshots),
        usageBytes: deviceSnapshots.reduce((total, snapshot) => total + (snapshot.totalCompressedSize ?? 0), 0),
      };
    });
  }, [devices, snapshotsByDevice]);

  const selectedMachine = machines.find((item) => item.device.id === selectedDeviceId) ?? null;

  return (
    <>
      <Topbar title="Máquinas" />
      <div className="min-h-0 flex-1 overflow-hidden">
        {error ? (
          <div
            className="mx-4 mt-4 rounded-lg border px-4 py-3 text-sm text-[#F87171] md:mx-6"
            style={{ background: "rgba(127,29,29,0.18)", borderColor: "rgba(248,113,113,0.24)" }}
          >
            {error}
          </div>
        ) : null}

        {loading ? (
          <div className="p-6 text-sm text-slate-500">Carregando máquinas...</div>
        ) : machines.length === 0 ? (
          <div className="p-6 text-sm text-slate-500">Nenhuma máquina encontrada.</div>
        ) : (
          <div
            className={
              selectedMachine
                ? "grid h-full min-h-0 lg:grid-cols-[minmax(380px,0.95fr)_64px_minmax(420px,1.05fr)]"
                : "grid h-full min-h-0 lg:grid-cols-[minmax(0,1fr)]"
            }
          >
            <DeviceList
              machines={machines}
              selectedId={selectedMachine?.device.id ?? ""}
              onSelect={(deviceId) => {
                setSelectedDeviceId(deviceId);
                setActiveTab("summary");
              }}
            />
            {selectedMachine ? (
              <>
                <ActionRail
                  key={`rail-${selectedMachine.device.id}`}
                  activeTab={activeTab}
                  onTabChange={setActiveTab}
                />
                <DetailPanel
                  key={`panel-${selectedMachine.device.id}`}
                  machine={selectedMachine}
                  activeTab={activeTab}
                  onTabChange={setActiveTab}
                  onClose={() => setSelectedDeviceId("")}
                />
              </>
            ) : null}
          </div>
        )}
      </div>
    </>
  );
}

function DeviceList({
  machines,
  selectedId,
  onSelect,
}: {
  machines: MachineViewModel[];
  selectedId: string;
  onSelect: (deviceId: string) => void;
}) {
  return (
    <section className="min-h-0 overflow-hidden border-r" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
      <div className="border-b px-6 py-4" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
        <h2 className="text-lg font-semibold text-slate-50">Todos os dispositivos</h2>
        <p className="mt-1 text-xs text-slate-500">{machines.length} dispositivo{machines.length === 1 ? "" : "s"}</p>
      </div>

      <div className="grid grid-cols-[56px_minmax(0,1.2fr)_120px_140px] border-b px-6 py-3 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
        <span>Tipo</span>
        <span>Nome</span>
        <span>Origem</span>
        <span>Último backup</span>
      </div>

      <div className="h-[calc(100%-112px)] overflow-y-auto">
        {machines.map((machine) => {
          const selected = machine.device.id === selectedId;
          return (
            <button
              key={machine.device.id}
              type="button"
              onClick={() => onSelect(machine.device.id)}
              className="grid w-full grid-cols-[56px_minmax(0,1.2fr)_120px_140px] items-center border-b px-6 py-3 text-left transition-colors duration-150"
              style={{
                borderColor: "rgba(148,163,184,0.08)",
                background: selected ? "rgba(123,97,255,0.12)" : "transparent",
                boxShadow: selected ? "inset 3px 0 0 #7B61FF" : "none",
              }}
            >
              <span className="text-xl text-[#7B61FF]">
                <i className="bi bi-pc-display-horizontal" aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold text-slate-100">{deviceName(machine.device)}</span>
                <span className="mt-0.5 block truncate text-xs text-slate-500">{machine.device.osName || "Sistema"}</span>
              </span>
              <span className="truncate text-sm text-slate-400">{machine.device.plan?.sources?.[0] ?? "Sistema"}</span>
              <span className="truncate text-sm text-slate-500">
                {machine.latestSnapshot ? formatRelative(machine.latestSnapshot.startedAt) : "Nunca"}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function ActionRail({
  activeTab,
  onTabChange,
}: {
  activeTab: MachinePanelTab;
  onTabChange: (tab: MachinePanelTab) => void;
}) {
  const items: Array<{ id: MachinePanelTab; label: string; icon: string }> = [
    { id: "summary", label: "Resumo", icon: "bi-speedometer2" },
    { id: "plan", label: "Plano", icon: "bi-shield-check" },
    { id: "snapshots", label: "Snapshots", icon: "bi-archive" },
  ];

  return (
    <aside className="machine-action-rail-enter flex min-h-0 flex-row border-b lg:flex-col lg:border-b-0 lg:border-r" style={{ borderColor: "rgba(148,163,184,0.12)", background: "rgba(2,6,23,0.38)" }}>
      {items.map((item) => {
        const active = activeTab === item.id;
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => onTabChange(item.id)}
            title={item.label}
            aria-label={item.label}
            className="grid h-16 w-16 place-items-center border-r text-xl transition-colors duration-150 lg:border-b lg:border-r-0"
            style={{
              borderColor: "rgba(148,163,184,0.12)",
              color: active ? "#A78BFA" : "#64748B",
              background: active ? "rgba(123,97,255,0.12)" : "transparent",
              boxShadow: active ? "inset 3px 0 0 #7B61FF" : "none",
            }}
          >
            <i className={`bi ${item.icon}`} aria-hidden="true" />
          </button>
        );
      })}

    </aside>
  );
}

function DetailPanel({
  machine,
  activeTab,
  onTabChange,
  onClose,
}: {
  machine: MachineViewModel | null;
  activeTab: MachinePanelTab;
  onTabChange: (tab: MachinePanelTab) => void;
  onClose: () => void;
}) {
  if (!machine) {
    return <section className="p-6 text-sm text-slate-500">Selecione uma máquina.</section>;
  }

  return (
    <section className="machine-detail-panel-enter min-h-0 overflow-y-auto">
      <div className="border-b px-8 py-5" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
        <div className="mb-3 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="grid h-8 w-8 place-items-center rounded-lg border text-sm text-slate-400 transition-colors duration-150 hover:bg-white/5 hover:text-slate-100"
            style={{ borderColor: "rgba(148,163,184,0.18)" }}
            aria-label="Fechar detalhes do dispositivo"
            title="Fechar"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-semibold text-slate-50">{deviceName(machine.device)}</h2>
              <MachineStatusPill status={machine.status} />
            </div>
            <p className="mt-1 text-sm text-slate-400">{machine.device.osName || "Sistema não informado"}</p>
            <p className="mt-1 truncate text-xs text-slate-500">{machine.device.id}</p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onTabChange("snapshots")}
              className="rounded-lg border px-3 py-2 text-xs font-semibold text-slate-200 transition-colors duration-150 hover:bg-white/5"
              style={{ borderColor: "rgba(148,163,184,0.18)" }}
            >
              Snapshots
            </button>
            <Link
              href={`/dashboard/protection?device=${encodeURIComponent(machine.device.id)}`}
              className="rounded-lg px-3 py-2 text-xs font-semibold text-slate-950 transition-colors duration-150 hover:opacity-90"
              style={{ background: "#4ADE80" }}
            >
              Plano
            </Link>
          </div>
        </div>
      </div>

      <div className="px-8 py-6">
        {activeTab === "summary" ? <MachineSummary machine={machine} /> : null}
        {activeTab === "plan" ? <MachinePlan machine={machine} /> : null}
        {activeTab === "snapshots" ? <MachineSnapshots machine={machine} /> : null}
      </div>
    </section>
  );
}

function MachineSummary({ machine }: { machine: MachineViewModel }) {
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-slate-50">Resumo</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <InfoRow label="Último backup" value={machine.latestSnapshot ? formatDateTime(machine.latestSnapshot.startedAt) : "Sem histórico"} />
        <InfoRow label="Último contato" value={machine.device.lastSeenAt ? formatDateTime(machine.device.lastSeenAt) : "Nunca visto"} />
        <InfoRow label="Armazenado" value={formatBytes(machine.usageBytes)} />
        <InfoRow label="Snapshots" value={String(machine.snapshots.length)} />
      </div>
    </div>
  );
}

function MachinePlan({ machine }: { machine: MachineViewModel }) {
  const plan = machine.device.plan;
  const rows = [
    { label: "Backup", value: plan ? planName(plan, machine.device.planLoading) : "Sem plano", action: null },
    { label: "O que fazer backup", value: plan?.sources?.length ? plan.sources.join(", ") : "Nenhum caminho configurado", action: null },
    { label: "Agendamento", value: scheduleLabel(plan?.scheduleCron), action: null },
    { label: "Quantos manter", value: retentionLabel(plan), action: null },
    { label: "CDP", value: plan?.cdpEnabled ? "Ativado" : "Desativado", action: null },
    { label: "Validação", value: plan?.validationEnabled ? "Ativada" : "Desativada", action: null },
    { label: "Criptografia", value: plan?.encryptionEnabled ? "Ativada" : "Desativada", action: null },
  ];

  return (
    <div className="overflow-hidden rounded-lg border" style={{ borderColor: "rgba(148,163,184,0.14)" }}>
      <div className="flex items-center justify-between border-b px-5 py-4" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
        <h3 className="text-base font-semibold text-slate-50">Plano de proteção</h3>
        <Link
          href={`/dashboard/protection?device=${encodeURIComponent(machine.device.id)}`}
          className="rounded-lg border px-3 py-2 text-xs font-semibold text-[#A78BFA] transition-colors duration-150 hover:bg-white/5"
          style={{ borderColor: "rgba(167,139,250,0.28)" }}
        >
          Editar
        </Link>
      </div>
      {rows.map((row) => (
        <div key={row.label} className="grid gap-3 border-b px-5 py-3 last:border-b-0 md:grid-cols-[220px_minmax(0,1fr)]" style={{ borderColor: "rgba(148,163,184,0.08)" }}>
          <span className="text-sm font-medium text-slate-400">{row.label}</span>
          <span className="break-words text-sm font-semibold text-slate-100">{row.value}</span>
        </div>
      ))}
    </div>
  );
}

function MachineSnapshots({ machine }: { machine: MachineViewModel }) {
  return (
    <div className="overflow-hidden rounded-lg border" style={{ borderColor: "rgba(148,163,184,0.14)" }}>
      <div className="flex items-center justify-between border-b px-5 py-4" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
        <h3 className="text-base font-semibold text-slate-50">Snapshots</h3>
      </div>

      {machine.snapshots.length === 0 ? (
        <p className="px-5 py-6 text-sm text-slate-500">Esta máquina ainda não possui snapshots.</p>
      ) : (
        machine.snapshots.map((snapshot) => (
          <div key={snapshot.id} className="grid gap-3 border-b px-5 py-3 last:border-b-0 md:grid-cols-[minmax(0,1fr)_120px_110px_90px]" style={{ borderColor: "rgba(148,163,184,0.08)" }}>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-slate-100">{formatDateTime(snapshot.startedAt)}</p>
              <p className="mt-0.5 truncate text-xs text-slate-500">{snapshot.sourcePath}</p>
            </div>
            <div className="flex items-center">
              <SnapshotKindPill type={inferSnapshotType(machine.snapshots, snapshot)} />
            </div>
            <div className="flex items-center">
              <SnapshotStatusPill status={snapshot.status} />
            </div>
            <Link
              href={`/dashboard/backups/${snapshot.id}`}
              className="self-center rounded-lg border px-3 py-2 text-center text-xs font-semibold text-slate-200 transition-colors duration-150 hover:bg-white/5"
              style={{ borderColor: "rgba(148,163,184,0.18)" }}
            >
              Explorar
            </Link>
          </div>
        ))
      )}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border px-4 py-3" style={{ borderColor: "rgba(148,163,184,0.12)", background: "rgba(2,6,23,0.28)" }}>
      <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-semibold text-slate-100">{value}</p>
    </div>
  );
}
