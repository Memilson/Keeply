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
  isMobileDevice,
  nextRunLabel,
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
  const [deletingDeviceId, setDeletingDeviceId] = useState<string | null>(null);
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

  const visibleMachines = useMemo(
    () => machines.filter((machine) => machine.latestSnapshot || isMobileDevice(machine.device)),
    [machines],
  );

  const selectedMachine = visibleMachines.find((item) => item.device.id === selectedDeviceId) ?? null;

  const handleDeleteDevice = useCallback(
    async (deviceId: string) => {
      const machine = machines.find((item) => item.device.id === deviceId);
      if (!machine) return;

      const confirmed = window.confirm(
        `Excluir o dispositivo "${deviceName(machine.device)}"? Essa ação remove o dispositivo e o histórico de snapshots dele.`,
      );
      if (!confirmed) return;

      setDeletingDeviceId(deviceId);
      setError(null);
      try {
        await api(`/api/devices/${deviceId}`, { method: "DELETE" });
        setSelectedDeviceId((current) => (current === deviceId ? "" : current));
        await loadData();
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Falha ao excluir dispositivo.");
      } finally {
        setDeletingDeviceId(null);
      }
    },
    [loadData, machines],
  );

  return (
    <>
      <Topbar title="Máquinas" />
      <div className="min-h-0 flex-1 overflow-hidden">
        {error ? (
          <div
            className="mx-4 mt-4 rounded-lg border px-4 py-3 text-sm text-[#DC2626] md:mx-6"
            style={{ background: "#FEF2F2", borderColor: "#FECACA" }}
          >
            {error}
          </div>
        ) : null}

        {loading ? (
          <div className="p-6 text-sm text-slate-500">Carregando máquinas...</div>
        ) : visibleMachines.length === 0 ? (
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
              machines={visibleMachines}
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
                  deleting={deletingDeviceId === selectedMachine.device.id}
                  onDelete={() => void handleDeleteDevice(selectedMachine.device.id)}
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
  const [searchQuery, setSearchQuery] = useState("");

  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (q.length < 3) return machines;
    return machines.filter((m) => {
      const name = (m.device.name ?? "").toLowerCase();
      const host = (m.device.hostname ?? "").toLowerCase();
      return name.includes(q) || host.includes(q);
    });
  }, [machines, searchQuery]);

  return (
    <section className="min-h-0 overflow-hidden border-r" style={{ borderColor: "#E5E7EB" }}>
      <div className="border-b px-6 py-4" style={{ borderColor: "#E5E7EB" }}>
        <h2 className="text-lg font-semibold text-gray-900">Todos os dispositivos</h2>
        <p className="mt-1 text-xs text-gray-500">{machines.length} dispositivo{machines.length === 1 ? "" : "s"}</p>
      </div>

      <div className="border-b px-6 py-2.5" style={{ borderColor: "#E5E7EB" }}>
        <div className="relative">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-gray-400">
            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Pesquisar dispositivo..."
            className="w-full rounded-lg border bg-white py-1.5 pl-8 pr-3 text-sm text-gray-700 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#7B61FF]/30"
            style={{ borderColor: "#E5E7EB" }}
          />
        </div>
      </div>

      <div className="grid grid-cols-[40px_minmax(0,1.2fr)_100px_120px_110px] border-b px-6 py-3 text-[11px] font-semibold uppercase tracking-[0.14em] text-gray-500" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
        <span>Tipo</span>
        <span>Nome</span>
        <span>Origem</span>
        <span>Último backup</span>
        <span>Próximo backup</span>
      </div>

      <div className="h-[calc(100%-152px)] overflow-y-auto">
        {filtered.map((machine) => {
          const selected = machine.device.id === selectedId;
          const mobile = isMobileDevice(machine.device);
          return (
            <button
              key={machine.device.id}
              type="button"
              onClick={() => onSelect(machine.device.id)}
              className="grid w-full grid-cols-[40px_minmax(0,1.2fr)_100px_120px_110px] items-center border-b px-6 py-3 text-left transition-colors duration-150"
              style={{
                borderColor: "#F3F4F6",
                background: selected ? "rgba(123,97,255,0.06)" : "transparent",
                boxShadow: selected ? "inset 3px 0 0 #7B61FF" : "none",
              }}
            >
              <span className="text-xl text-[#7B61FF]">
                <i className={`bi ${mobile ? "bi-phone" : "bi-pc-display-horizontal"}`} aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold text-gray-900">{deviceName(machine.device)}</span>
                <span className="mt-0.5 block truncate text-xs text-gray-500">{machine.device.osName || "Sistema"}</span>
              </span>
              <span className="truncate text-sm text-gray-500">{machine.device.plan?.sources?.[0] ?? "—"}</span>
              <span className="truncate text-sm text-gray-500">
                {machine.latestSnapshot ? formatRelative(machine.latestSnapshot.startedAt) : "Nunca"}
              </span>
              <span className="truncate text-sm text-gray-500">
                {machine.device.plan?.scheduleCron ? nextRunLabel(machine.device.plan.scheduleCron) : "—"}
              </span>
            </button>
          );
        })}
        {filtered.length === 0 && (
          <p className="px-6 py-6 text-sm text-gray-400">Nenhum resultado para &ldquo;{searchQuery}&rdquo;.</p>
        )}
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
    <aside className="machine-action-rail-enter flex min-h-0 flex-row border-b lg:flex-col lg:border-b-0 lg:border-r" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
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
              borderColor: "#E5E7EB",
              color: active ? "#7B61FF" : "#6B7280",
              background: active ? "rgba(123,97,255,0.08)" : "transparent",
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
  deleting,
  onDelete,
  onTabChange,
  onClose,
}: {
  machine: MachineViewModel | null;
  activeTab: MachinePanelTab;
  deleting: boolean;
  onDelete: () => void;
  onTabChange: (tab: MachinePanelTab) => void;
  onClose: () => void;
}) {
  if (!machine) {
    return <section className="p-6 text-sm text-slate-500">Selecione uma máquina.</section>;
  }

  return (
    <section className="machine-detail-panel-enter min-h-0 overflow-y-auto">
      <div className="border-b px-8 py-5" style={{ borderColor: "#E5E7EB" }}>
        <div className="mb-3 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="grid h-8 w-8 place-items-center rounded-lg border text-sm text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-700"
            style={{ borderColor: "#E5E7EB" }}
            aria-label="Fechar detalhes do dispositivo"
            title="Fechar"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-semibold text-gray-900">{deviceName(machine.device)}</h2>
              <MachineStatusPill status={machine.status} />
            </div>
            <p className="mt-1 text-sm text-gray-500">{machine.device.osName || "Sistema não informado"}</p>
            <p className="mt-1 truncate text-xs text-gray-400">{machine.device.id}</p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onDelete}
              disabled={deleting}
              className="rounded-lg border px-3 py-2 text-xs font-semibold text-[#B91C1C] transition-colors duration-150 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
              style={{ borderColor: "#FECACA" }}
            >
              {deleting ? "Excluindo..." : "Excluir dispositivo"}
            </button>
            <button
              type="button"
              onClick={() => onTabChange("snapshots")}
              className="rounded-lg border px-3 py-2 text-xs font-semibold text-gray-600 transition-colors duration-150 hover:bg-gray-100"
              style={{ borderColor: "#E5E7EB" }}
            >
              Snapshots
            </button>
            <Link
              href={`/dashboard/protection?device=${encodeURIComponent(machine.device.id)}`}
              className="rounded-lg px-3 py-2 text-xs font-semibold text-white transition-colors duration-150 hover:opacity-90"
              style={{ background: "#059669" }}
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
      <h3 className="text-base font-semibold text-gray-900">Resumo</h3>
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
    <div className="overflow-hidden rounded-lg border" style={{ borderColor: "#E5E7EB" }}>
      <div className="flex items-center justify-between border-b px-5 py-4" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
        <h3 className="text-base font-semibold text-gray-900">Plano de proteção</h3>
        <Link
          href={`/dashboard/protection?device=${encodeURIComponent(machine.device.id)}`}
          className="rounded-lg border px-3 py-2 text-xs font-semibold text-[#7B61FF] transition-colors duration-150 hover:bg-purple-50"
          style={{ borderColor: "rgba(123,97,255,0.3)" }}
        >
          Editar
        </Link>
      </div>
      {rows.map((row) => (
        <div key={row.label} className="grid gap-3 border-b px-5 py-3 last:border-b-0 md:grid-cols-[220px_minmax(0,1fr)]" style={{ borderColor: "#F3F4F6" }}>
          <span className="text-sm font-medium text-gray-500">{row.label}</span>
          <span className="break-words text-sm font-semibold text-gray-900">{row.value}</span>
        </div>
      ))}
    </div>
  );
}

function MachineSnapshots({ machine }: { machine: MachineViewModel }) {
  return (
    <div className="overflow-hidden rounded-lg border" style={{ borderColor: "#E5E7EB" }}>
      <div className="flex items-center justify-between border-b px-5 py-4" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
        <h3 className="text-base font-semibold text-gray-900">Snapshots</h3>
      </div>

      {machine.snapshots.length === 0 ? (
        <p className="px-5 py-6 text-sm text-gray-500">Esta máquina ainda não possui snapshots.</p>
      ) : (
        machine.snapshots.map((snapshot) => (
          <div key={snapshot.id} className="grid gap-3 border-b px-5 py-3 last:border-b-0 md:grid-cols-[minmax(0,1fr)_120px_110px_90px]" style={{ borderColor: "#F3F4F6" }}>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-gray-900">{formatDateTime(snapshot.startedAt)}</p>
              <p className="mt-0.5 truncate text-xs text-gray-400">{snapshot.sourcePath}</p>
            </div>
            <div className="flex items-center">
              <SnapshotKindPill type={inferSnapshotType(machine.snapshots, snapshot)} />
            </div>
            <div className="flex items-center">
              <SnapshotStatusPill status={snapshot.status} />
            </div>
            <Link
              href={`/dashboard/backups/${snapshot.id}`}
              className="self-center rounded-lg border px-3 py-2 text-center text-xs font-semibold text-gray-600 transition-colors duration-150 hover:bg-gray-100"
              style={{ borderColor: "#E5E7EB" }}
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
    <div className="rounded-lg border px-4 py-3" style={{ borderColor: "#E5E7EB", background: "#F9FAFB" }}>
      <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-gray-500">{label}</p>
      <p className="mt-1 text-sm font-semibold text-gray-900">{value}</p>
    </div>
  );
}
