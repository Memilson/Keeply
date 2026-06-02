"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { api, type Device, type DevicePlan, type Snapshot } from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };

export default function MachinesPage() {
  const [devices, setDevices] = useState<DeviceWithPlan[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [deviceList, snapshotList] = await Promise.all([
          api<Device[]>("/api/devices"),
          api<Snapshot[]>("/api/snapshots"),
        ]);
        const withPlan = (deviceList ?? []).map((d) => ({ ...d, plan: null, planLoading: true }));
        setDevices(withPlan);
        setSnapshots(snapshotList ?? []);
        if (withPlan.length > 0) setSelectedDeviceId(withPlan[0].id);
        setLoading(false);

        await Promise.all(
          withPlan.map(async (d) => {
            try {
              const plan = await api<DevicePlan>(`/api/devices/${d.id}/plan`);
              setDevices((prev) => prev.map((x) => (x.id === d.id ? { ...x, plan, planLoading: false } : x)));
            } catch {
              setDevices((prev) => prev.map((x) => (x.id === d.id ? { ...x, planLoading: false } : x)));
            }
          })
        );
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar máquinas.");
        setLoading(false);
      }
    })();
  }, []);

  const snapshotsByDevice = useMemo(() => {
    const map = new Map<string, Snapshot[]>();
    for (const s of snapshots) {
      const arr = map.get(s.deviceId) ?? [];
      arr.push(s);
      map.set(s.deviceId, arr);
    }
    for (const [, arr] of map) {
      arr.sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }
    return map;
  }, [snapshots]);

  const selected = devices.find((d) => d.id === selectedDeviceId) ?? null;
  const selectedSnapshots = selectedDeviceId ? snapshotsByDevice.get(selectedDeviceId) ?? [] : [];
  const totalBytes = selectedSnapshots.reduce((acc, s) => acc + (s.totalCompressedSize ?? 0), 0);
  const latestSnapshot = selectedSnapshots[0] ?? null;

  return (
    <>
      <Topbar title="Máquinas" subtitle="Plano configurado por máquina" />
      <div className="p-7">
        {error && (
          <div className="mb-5 rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        <div className="grid gap-5 lg:grid-cols-[280px_minmax(0,1fr)]">
          <aside className="kp-card overflow-hidden">
            <div className="px-4 py-3 text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993", borderBottom: "1px solid #F0EEF8", background: "#FAFAFE" }}>
              Máquinas
            </div>
            {loading ? (
              <p className="px-4 py-6 text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
            ) : devices.length === 0 ? (
              <p className="px-4 py-6 text-sm" style={{ color: "#6B6993" }}>Nenhuma máquina cadastrada.</p>
            ) : (
              <div className="p-2">
                {devices.map((d) => {
                  const active = selectedDeviceId === d.id;
                  const list = snapshotsByDevice.get(d.id) ?? [];
                  return (
                    <button
                      key={d.id}
                      onClick={() => setSelectedDeviceId(d.id)}
                      className="mb-1.5 w-full rounded-lg px-3 py-2.5 text-left"
                      style={active ? { background: "#EDE9FF" } : { background: "transparent" }}
                    >
                      <p className="truncate text-sm font-semibold" style={{ color: active ? "#6046F0" : "#18163A" }}>{deviceName(d)}</p>
                      <p className="mt-0.5 text-xs" style={{ color: "#6B6993" }}>
                        {list.length} snapshot{list.length !== 1 ? "s" : ""}
                      </p>
                    </button>
                  );
                })}
              </div>
            )}
          </aside>

          {!selected ? (
            <section className="kp-card px-6 py-10 text-sm" style={{ color: "#6B6993" }}>
              Selecione uma máquina na barra lateral.
            </section>
          ) : (
            <section className="space-y-5">
              <div className="kp-card overflow-hidden">
                <div className="flex flex-wrap items-start justify-between gap-3 px-6 py-5" style={{ borderBottom: "1px solid #ECEAF5" }}>
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>Máquina</p>
                    <h2 className="mt-1 text-xl font-semibold" style={{ color: "#111827" }}>{deviceName(selected)}</h2>
                    <p className="mt-1 text-sm" style={{ color: "#6B6993" }}>{selected.osName || "Sistema operacional não informado"}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Link
                      href={`/dashboard/backups?device=${encodeURIComponent(selected.id)}`}
                      className="rounded-lg px-3 py-2 text-sm font-medium transition-colors hover:opacity-80"
                      style={{ border: "1px solid #E4E1F0", color: "#7B61FF", background: "#FAFAFE" }}
                    >
                      Ver snapshots
                    </Link>
                    <Link
                      href={`/dashboard/protection?device=${encodeURIComponent(selected.id)}`}
                      className="rounded-lg px-3 py-2 text-sm font-medium transition-colors hover:opacity-80"
                      style={{ border: "1px solid #7B61FF", color: "#FFFFFF", background: "#7B61FF" }}
                    >
                      Configurar plano
                    </Link>
                  </div>
                </div>

                <div className="grid gap-4 px-6 py-5 md:grid-cols-2">
                  <InfoRow label="ID do dispositivo" value={selected.id} />
                  <InfoRow label="Instalação" value={selected.deviceInstallationId ?? "Não informado"} />
                  <InfoRow label="Agente" value={selected.agentVersion ?? "Não informado"} />
                  <InfoRow label="Último contato" value={selected.lastSeenAt ? formatDateTime(selected.lastSeenAt) : "Nunca visto"} />
                  <InfoRow label="Snapshots" value={`${selectedSnapshots.length}`} />
                  <InfoRow label="Armazenamento usado" value={formatBytes(totalBytes)} />
                  <InfoRow label="Último snapshot" value={latestSnapshot ? formatDateTime(latestSnapshot.startedAt) : "Nenhum snapshot"} />
                </div>
              </div>

              <div className="kp-card overflow-hidden">
                <div className="px-6 py-5" style={{ borderBottom: "1px solid #ECEAF5" }}>
                  <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>Plano configurado</p>
                  <h2 className="mt-1 text-xl font-semibold" style={{ color: "#111827" }}>Plano de Backup</h2>
                </div>

                {selected.planLoading ? (
                  <p className="px-6 py-8 text-sm" style={{ color: "#6B6993" }}>Carregando plano…</p>
                ) : !selected.plan ? (
                  <p className="px-6 py-8 text-sm" style={{ color: "#6B6993" }}>Nenhum plano configurado para esta máquina.</p>
                ) : (
                  <>
                    <PlanRow title="O que fazer backup" value={`${selected.plan.sources.length} pasta(s)`}>
                      <div className="mt-3 space-y-2">
                        {selected.plan.sources.map((src) => (
                          <div key={src} className="rounded-xl px-4 py-3 text-sm" style={{ background: "#F3F4F6", color: "#334155" }}>
                            {src}
                          </div>
                        ))}
                      </div>
                    </PlanRow>
                    <PlanRow title="Proteção contínua (CDP)" value={selected.plan.cdpEnabled ? "Ativada" : "Desativada"} />
                    <PlanRow title="Agendamento" value={scheduleLabel(selected.plan.scheduleCron ?? "")} />
                    <PlanRow title="Quanto tempo manter" value={retentionLabel(selected.plan)} />
                    <PlanRow title="Criptografia" value={selected.plan.encryptionEnabled ? "AES-256 · SHA-256" : "Desativada"} />
                  </>
                )}
              </div>
            </section>
          )}
        </div>
      </div>
    </>
  );
}

function deviceName(device: Device) {
  return device.name || device.hostname || "Máquina";
}

function scheduleLabel(cron: string) {
  if (!cron || !cron.trim()) return "Não configurado";
  const p = cron.trim().split(/\s+/);
  if (p.length !== 5) return "Não configurado";
  const min = p[0].padStart(2, "0");
  const hour = p[1].padStart(2, "0");
  return `Todos os dias às ${hour}:${min}`;
}

function retentionLabel(plan: DevicePlan) {
  if (plan.retentionMode === "KEEP_DAYS" && plan.retentionDays && plan.retentionDays > 0) {
    return `Manter por ${plan.retentionDays} dia${plan.retentionDays === 1 ? "" : "s"}`;
  }
  return "Manter todos os snapshots";
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium" style={{ color: "#64748B" }}>{label}</p>
      <p className="mt-1 break-words text-sm" style={{ color: "#334155" }}>{value}</p>
    </div>
  );
}

function PlanRow({ title, value, children }: { title: string; value: string; children?: ReactNode }) {
  return (
    <div className="px-6 py-5" style={{ borderTop: "1px solid #ECEAF5" }}>
      <div className="flex items-start justify-between gap-4">
        <p className="text-sm font-medium" style={{ color: "#334155" }}>{title}</p>
        <p className="text-right text-sm" style={{ color: "#64748B" }}>{value}</p>
      </div>
      {children}
    </div>
  );
}
