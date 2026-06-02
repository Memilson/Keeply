"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { api, type Device, type DevicePlan } from "@/lib/api";
import { Topbar } from "@/components/Topbar";

type PlanType = "DEFAULT" | "CUSTOM";
type RetentionMode = "KEEP_ALL" | "KEEP_DAYS";

type Draft = {
  planType: PlanType;
  sources: string[];
  cdpEnabled: boolean;
  encryptionEnabled: boolean;
  scheduleCron: string | null;
  retentionMode: RetentionMode;
  retentionDays: number | null;
};

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };

export default function ProtectionPage() {
  const searchParams = useSearchParams();
  const deviceParam = searchParams.get("device");
  const [devices, setDevices] = useState<DeviceWithPlan[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [newSource, setNewSource] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const devList = await api<Device[]>("/api/devices");
        const withPlan: DeviceWithPlan[] = (devList ?? []).map((d) => ({ ...d, plan: null, planLoading: true }));
        setDevices(withPlan);
        const requested = withPlan.find((d) => d.id === deviceParam);
        if (requested || withPlan.length > 0) setSelectedId((requested ?? withPlan[0]).id);
        setLoading(false);
        await Promise.all(withPlan.map(async (d) => {
          try {
            const plan = await api<DevicePlan>(`/api/devices/${d.id}/plan`);
            setDevices((prev) => prev.map((x) => (x.id === d.id ? { ...x, plan, planLoading: false } : x)));
          } catch {
            setDevices((prev) => prev.map((x) => (x.id === d.id ? { ...x, planLoading: false } : x)));
          }
        }));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar proteção.");
        setLoading(false);
      }
    })();
  }, [deviceParam]);

  const selected = useMemo(
    () => devices.find((d) => d.id === selectedId) ?? null,
    [devices, selectedId]
  );

  function toDraft(device: DeviceWithPlan): Draft {
    return {
      planType: device.plan?.planType ?? "DEFAULT",
      sources: device.plan?.sources ?? [],
      cdpEnabled: device.plan?.cdpEnabled ?? false,
      encryptionEnabled: device.plan?.encryptionEnabled ?? false,
      scheduleCron: device.plan?.scheduleCron ?? null,
      retentionMode: device.plan?.retentionMode ?? "KEEP_ALL",
      retentionDays: device.plan?.retentionDays ?? null,
    };
  }

  const draft = selected ? drafts[selected.id] ?? toDraft(selected) : null;
  const isDirty = !!(selected && drafts[selected.id]);

  function patch(update: Partial<Draft>) {
    if (!selected) return;
    setDrafts((prev) => ({ ...prev, [selected.id]: { ...(prev[selected.id] ?? toDraft(selected)), ...update } }));
  }

  function scheduleLabel(cron: string | null | undefined) {
    if (!cron || !cron.trim()) return "Não configurado";
    const p = cron.trim().split(/\s+/);
    if (p.length !== 5) return "Não configurado";
    const min = p[0].padStart(2, "0");
    const hour = p[1].padStart(2, "0");
    return `Todos os dias às ${hour}:${min}`;
  }

  function retentionLabel(mode: RetentionMode, days: number | null) {
    if (mode === "KEEP_DAYS" && days && days > 0) {
      return `Manter por ${days} dia${days === 1 ? "" : "s"}`;
    }
    return "Manter todos os snapshots";
  }

  async function save() {
    if (!selected || !draft) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await api<DevicePlan>(`/api/devices/${selected.id}/plan`, {
        method: "PUT",
        body: JSON.stringify({
          planType: draft.planType,
          sources: draft.sources.length ? draft.sources : ["/"],
          cdpEnabled: draft.cdpEnabled,
          encryptionEnabled: draft.encryptionEnabled,
          scheduleCron: draft.scheduleCron || null,
          retentionMode: draft.retentionMode,
          retentionDays: draft.retentionMode === "KEEP_DAYS" ? draft.retentionDays : null,
        }),
      });
      setDevices((prev) => prev.map((d) => (d.id === selected.id ? { ...d, plan: updated } : d)));
      setDrafts((prev) => {
        const next = { ...prev };
        delete next[selected.id];
        return next;
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar proteção.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <>
        <Topbar title="Proteção" subtitle="Plano de proteção do agente" />
        <div className="p-7 text-sm" style={{ color: "#6B6993" }}>Carregando…</div>
      </>
    );
  }

  return (
    <>
      <Topbar title="Proteção" subtitle="Plano de proteção do agente" />
      <div className="p-7">
        {error && (
          <div className="mb-4 rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        {!selected ? (
          <div className="kp-card px-6 py-10 text-sm" style={{ color: "#6B6993" }}>Nenhum dispositivo registrado.</div>
        ) : (
          <div className="kp-card overflow-hidden">
            <div className="flex items-center justify-between px-6 py-5" style={{ borderBottom: "1px solid #ECEAF5" }}>
              <div className="flex items-center gap-3">
                <ShieldIcon />
                <h2 className="text-xl font-semibold" style={{ color: "#111827" }}>Plano de Backup</h2>
              </div>
              <div className="flex items-center gap-3">
                {devices.length > 1 && (
                  <select
                    value={selected.id}
                    onChange={(e) => setSelectedId(e.target.value)}
                    className="rounded-lg border px-2 py-1 text-sm"
                    style={{ borderColor: "#E4E1F0", color: "#374151" }}
                  >
                    {devices.map((d) => (
                      <option key={d.id} value={d.id}>{d.name || d.hostname}</option>
                    ))}
                  </select>
                )}
                <Toggle value={Boolean(selected.plan)} onChange={() => {}} disabled />
              </div>
            </div>

            <SectionRow title="O que fazer backup" right={`${draft?.sources.length ?? 0} pasta(s)`}>
              <button
                onClick={() => {
                  const p = newSource.trim();
                  if (!draft || !p || draft.sources.includes(p)) return;
                  patch({ sources: [...draft.sources, p], planType: "CUSTOM" });
                  setNewSource("");
                }}
                className="rounded-xl border px-4 py-1.5 text-sm"
                style={{ borderColor: "#D1D5DB", color: "#6D47FF" }}
              >
                + Adicionar
              </button>
            </SectionRow>

            <div className="px-6 pb-4">
              <div className="mb-2 flex gap-2">
                <input
                  value={newSource}
                  onChange={(e) => setNewSource(e.target.value)}
                  placeholder="/home/angelo/Storage"
                  className="flex-1 rounded-lg border px-3 py-2 text-sm"
                  style={{ borderColor: "#E4E1F0" }}
                />
              </div>
              <div className="space-y-2">
                {(draft?.sources ?? []).map((src) => (
                  <div key={src} className="flex items-center justify-between rounded-xl px-4 py-3" style={{ background: "#F3F4F6" }}>
                    <div className="flex items-center gap-3">
                      <FolderIcon />
                      <span className="text-sm" style={{ color: "#334155" }}>{src}</span>
                    </div>
                    <button
                      onClick={() => patch({ sources: (draft?.sources ?? []).filter((s) => s !== src), planType: "CUSTOM" })}
                      className="text-lg leading-none"
                      style={{ color: "#DC2626" }}
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <LineRow title="Proteção contínua (CDP)">
              <Toggle value={!!draft?.cdpEnabled} onChange={(v) => patch({ cdpEnabled: v })} />
            </LineRow>

            <LineRow title="Agendamento" value={scheduleLabel(draft?.scheduleCron)}>
              <button
                onClick={() => {
                  const defaultValue = draft?.scheduleCron
                    ? scheduleLabel(draft.scheduleCron).replace("Todos os dias às ", "")
                    : "";
                  const value = window.prompt("Informe horário (HH:MM). Deixe vazio para remover.", defaultValue);
                  if (value === null) return;
                  if (!value.trim()) {
                    patch({ scheduleCron: null });
                    return;
                  }
                  const m = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value);
                  if (!m) return;
                  patch({ scheduleCron: `${Number(m[2])} ${Number(m[1])} * * *` });
                }}
                className="text-lg leading-none"
                style={{ color: "#6D47FF" }}
              >
                ✎
              </button>
            </LineRow>

            <LineRow title="Quanto tempo manter" value={retentionLabel(draft?.retentionMode ?? "KEEP_ALL", draft?.retentionDays ?? null)}>
              <select
                value={draft?.retentionMode ?? "KEEP_ALL"}
                onChange={(e) => {
                  const mode = e.target.value as RetentionMode;
                  patch({
                    retentionMode: mode,
                    retentionDays: mode === "KEEP_DAYS" ? draft?.retentionDays ?? 30 : null,
                  });
                }}
                className="rounded-lg border px-2 py-1 text-sm"
                style={{ borderColor: "#E4E1F0", color: "#374151" }}
              >
                <option value="KEEP_ALL">Manter todos</option>
                <option value="KEEP_DAYS">Manter por dias</option>
              </select>
              {(draft?.retentionMode ?? "KEEP_ALL") === "KEEP_DAYS" && (
                <input
                  type="number"
                  min={1}
                  value={draft?.retentionDays ?? 30}
                  onChange={(e) => patch({ retentionDays: Number(e.target.value) > 0 ? Number(e.target.value) : null })}
                  className="w-24 rounded-lg border px-3 py-2 text-sm"
                  style={{ borderColor: "#E4E1F0" }}
                />
              )}
            </LineRow>

            <div className="flex items-center justify-between px-6 py-5" style={{ borderTop: "1px solid #ECEAF5" }}>
              <div>
                <p className="text-sm font-medium" style={{ color: "#334155" }}>Criptografia</p>
                <p className="text-xs" style={{ color: "#94A3B8" }}>AES-256 · SHA-256</p>
              </div>
              <Toggle value={!!draft?.encryptionEnabled} onChange={(v) => patch({ encryptionEnabled: v })} />
            </div>

            <div style={{ borderTop: "1px solid #ECEAF5" }} className="px-6 py-5">
              <p className="mb-3 text-sm font-medium" style={{ color: "#334155" }}>Informações do dispositivo</p>
              <div className="grid gap-2 text-sm">
                <div className="flex gap-3">
                  <span style={{ color: "#64748B", minWidth: 120 }}>ID do dispositivo</span>
                  <span style={{ color: "#334155" }}>{selected.id}</span>
                </div>
                <div className="flex gap-3">
                  <span style={{ color: "#64748B", minWidth: 120 }}>Servidor</span>
                  <span style={{ color: "#334155" }}>http://localhost:8080</span>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2 px-6 py-4" style={{ borderTop: "1px solid #ECEAF5", background: "#FAFAFE" }}>
              <button
                onClick={() => {
                  if (!selected) return;
                  setDrafts((prev) => {
                    const next = { ...prev };
                    delete next[selected.id];
                    return next;
                  });
                }}
                className="rounded-lg border px-4 py-2 text-sm"
                style={{ borderColor: "#D1D5DB", color: "#475569" }}
              >
                Cancelar
              </button>
              <button
                onClick={save}
                disabled={!isDirty || saving}
                className="rounded-lg px-4 py-2 text-sm text-white disabled:opacity-60"
                style={{ background: "#6D47FF" }}
              >
                {saving ? "Salvando..." : "Salvar"}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function SectionRow({ title, right, children }: { title: string; right?: string; children?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between px-6 py-5" style={{ borderTop: "1px solid #ECEAF5" }}>
      <p className="text-sm font-medium" style={{ color: "#334155" }}>{title}</p>
      <div className="flex items-center gap-3">
        {right && <span className="text-sm" style={{ color: "#64748B" }}>{right}</span>}
        {children}
      </div>
    </div>
  );
}

function LineRow({ title, value, children }: { title: string; value?: string; children?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between px-6 py-5" style={{ borderTop: "1px solid #ECEAF5" }}>
      <p className="text-sm font-medium" style={{ color: "#334155" }}>{title}</p>
      <div className="flex items-center gap-3">
        {value && <span className="text-sm" style={{ color: "#64748B" }}>{value}</span>}
        {children}
      </div>
    </div>
  );
}

function Toggle({ value, onChange, disabled = false }: { value: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      onClick={() => !disabled && onChange(!value)}
      disabled={disabled}
      className="relative inline-flex h-8 w-14 items-center rounded-full"
      style={{ background: value ? "#6D47FF" : "#CBD5E1" }}
    >
      <span
        className="inline-block h-6 w-6 rounded-full bg-white transition-transform"
        style={{ transform: value ? "translateX(30px)" : "translateX(2px)" }}
      />
    </button>
  );
}

function ShieldIcon() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#6D47FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#F59E0B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  );
}
