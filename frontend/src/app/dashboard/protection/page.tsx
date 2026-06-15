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
  validationEnabled: boolean;
  encryptionEnabled: boolean;
  scheduleCron: string | null;
  retentionMode: RetentionMode;
  retentionDays: number | null;
};

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };

const DEFAULT_DAILY_CRON = "0 2 * * *";

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
      validationEnabled: device.plan?.validationEnabled ?? false,
      encryptionEnabled: device.plan?.encryptionEnabled ?? false,
      scheduleCron: toDailyCron(device.plan?.scheduleCron),
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
    return `Todos os dias às ${cronToTime(cron)}`;
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
          validationEnabled: draft.validationEnabled,
          encryptionEnabled: draft.encryptionEnabled,
          scheduleCron: toDailyCron(draft.scheduleCron),
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
        <div className="p-6 text-sm text-slate-500">Carregando…</div>
      </>
    );
  }

  const inputCls = "rounded-lg border bg-white px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-[#7B61FF]/30 transition-shadow";
  const inputStyle = { borderColor: "#E5E7EB" };

  return (
    <>
      <Topbar title="Proteção" subtitle="Plano de proteção do agente" />
      <div className="p-6">
        {error && (
          <div
            className="mb-4 rounded-xl border px-4 py-3 text-sm text-[#DC2626]"
            style={{ background: "#FEF2F2", borderColor: "#FECACA" }}
          >
            {error}
          </div>
        )}

        {!selected ? (
          <div className="px-6 py-10 text-sm text-slate-500">Nenhum dispositivo registrado.</div>
        ) : (
          <div
            className="rounded-xl border bg-white overflow-hidden max-w-2xl mx-auto"
            style={{ borderColor: "#E5E7EB" }}
          >
            {/* Header */}
            <div
              className="flex items-center justify-between px-6 py-5"
              style={{ borderBottom: "1px solid #E5E7EB", background: "#F9FAFB" }}
            >
              <div className="flex items-center gap-3">
                <span
                  className="grid h-9 w-9 place-items-center rounded-xl"
                  style={{ background: "rgba(123,97,255,0.10)" }}
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                    <path d="m9 12 2 2 4-4" />
                  </svg>
                </span>
                <h2 className="text-base font-bold text-gray-900">Plano de Backup</h2>
              </div>
              <div className="flex items-center gap-3">
                {devices.length > 1 && (
                  <select
                    value={selected.id}
                    onChange={(e) => setSelectedId(e.target.value)}
                    className={inputCls}
                    style={inputStyle}
                  >
                    {devices.map((d) => (
                      <option key={d.id} value={d.id}>{d.name || d.hostname}</option>
                    ))}
                  </select>
                )}
                <Toggle value={Boolean(selected.plan)} onChange={() => {}} disabled />
              </div>
            </div>

            {/* Sources */}
            <SectionRow title="O que fazer backup" right={`${draft?.sources.length ?? 0} pasta(s)`}>
              <button
                onClick={() => {
                  const p = newSource.trim();
                  if (!draft || !p || draft.sources.includes(p)) return;
                  patch({ sources: [...draft.sources, p], planType: "CUSTOM" });
                  setNewSource("");
                }}
                className="rounded-lg border px-3 py-1.5 text-xs font-semibold text-[#A78BFA] transition-colors duration-200 hover:bg-[#7B61FF]/10 cursor-pointer"
                style={{ borderColor: "rgba(123,97,255,0.3)" }}
              >
                + Adicionar
              </button>
            </SectionRow>

            <div className="px-6 pb-4">
              <div className="mb-2 flex gap-2">
                <input
                  value={newSource}
                  onChange={(e) => setNewSource(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      const p = newSource.trim();
                      if (draft && p && !draft.sources.includes(p)) {
                        patch({ sources: [...draft.sources, p], planType: "CUSTOM" });
                        setNewSource("");
                      }
                    }
                  }}
                  placeholder="/home/user/Storage"
                  className={`flex-1 ${inputCls}`}
                  style={inputStyle}
                />
              </div>
              <div className="space-y-2">
                {(draft?.sources ?? []).map((src) => (
                  <div
                    key={src}
                    className="flex items-center justify-between rounded-lg px-3 py-2.5"
                    style={{ background: "#F9FAFB", border: "1px solid #E5E7EB" }}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <svg viewBox="0 0 24 24" fill="none" stroke="#D97706" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
                        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                      </svg>
                      <span className="text-sm font-mono text-gray-700 truncate">{src}</span>
                    </div>
                    <button
                      onClick={() => patch({ sources: (draft?.sources ?? []).filter((s) => s !== src), planType: "CUSTOM" })}
                      className="text-gray-400 hover:text-[#DC2626] transition-colors duration-200 cursor-pointer text-lg leading-none ml-2"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {/* CDP */}
            <LineRow title="Proteção contínua (CDP)" description="Backup incremental em tempo real">
              <Toggle value={!!draft?.cdpEnabled} onChange={(v) => patch({ cdpEnabled: v })} />
            </LineRow>

            {/* Validation */}
            <LineRow title="Validação pós-backup" description="Verifica integridade após cada snapshot">
              <Toggle value={!!draft?.validationEnabled} onChange={(v) => patch({ validationEnabled: v })} />
            </LineRow>

            {/* Schedule */}
            <LineRow title="Agendamento" description={scheduleLabel(draft?.scheduleCron)}>
              <input
                type="time"
                required
                step={60}
                value={cronToTime(draft?.scheduleCron)}
                onChange={(e) => {
                  const nextCron = timeToDailyCron(e.target.value);
                  if (nextCron) patch({ scheduleCron: nextCron });
                }}
                className={inputCls}
                style={inputStyle}
                aria-label="Hora diária do agendamento"
              />
            </LineRow>

            {/* Retention */}
            <LineRow title="Retenção" description={retentionLabel(draft?.retentionMode ?? "KEEP_ALL", draft?.retentionDays ?? null)}>
              <div className="flex items-center gap-2">
                <select
                  value={draft?.retentionMode ?? "KEEP_ALL"}
                  onChange={(e) => {
                    const mode = e.target.value as RetentionMode;
                    patch({ retentionMode: mode, retentionDays: mode === "KEEP_DAYS" ? draft?.retentionDays ?? 30 : null });
                  }}
                  className={inputCls}
                  style={inputStyle}
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
                    className={`w-20 ${inputCls}`}
                    style={inputStyle}
                  />
                )}
              </div>
            </LineRow>

            {/* Encryption */}
            <div
              className="flex items-center justify-between px-6 py-5"
              style={{ borderTop: "1px solid #F3F4F6" }}
            >
              <div>
                <p className="text-sm font-medium text-gray-900">Criptografia</p>
                <p className="text-xs text-gray-400 mt-0.5">AES-256 · SHA-256</p>
              </div>
              <Toggle value={!!draft?.encryptionEnabled} onChange={(v) => patch({ encryptionEnabled: v })} />
            </div>

            {/* Device info */}
            <div
              className="px-6 py-5"
              style={{ borderTop: "1px solid #F3F4F6" }}
            >
              <p className="mb-3 text-xs font-bold uppercase tracking-widest text-gray-400">
                Informações do dispositivo
              </p>
              <div className="space-y-2 text-sm">
                <div className="flex gap-3">
                  <span className="text-gray-400 shrink-0" style={{ minWidth: 120 }}>ID do dispositivo</span>
                  <span className="text-gray-700 font-mono text-xs break-all">{selected.id}</span>
                </div>
                <div className="flex gap-3">
                  <span className="text-gray-400 shrink-0" style={{ minWidth: 120 }}>Servidor</span>
                  <span className="text-gray-700 font-mono text-xs">http://localhost:8080</span>
                </div>
              </div>
            </div>

            {/* Footer */}
            <div
              className="flex justify-end gap-2 px-6 py-4"
              style={{ borderTop: "1px solid #F3F4F6", background: "#F9FAFB" }}
            >
              <button
                onClick={() => {
                  if (!selected) return;
                  setDrafts((prev) => { const next = { ...prev }; delete next[selected.id]; return next; });
                }}
                className="rounded-lg border px-4 py-2 text-sm text-gray-500 hover:text-gray-700 transition-colors duration-200 cursor-pointer"
                style={{ borderColor: "#E5E7EB" }}
              >
                Cancelar
              </button>
              <button
                onClick={save}
                disabled={!isDirty || saving}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                style={{ background: "#7B61FF" }}
              >
                {saving ? "Salvando..." : "Salvar alterações"}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function cronToTime(cron: string | null | undefined) {
  const normalized = toDailyCron(cron);
  const parts = normalized.split(/\s+/);
  const minute = Number(parts[0]);
  const hour = Number(parts[1]);
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function timeToDailyCron(time: string) {
  const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time.trim());
  if (!match) return null;
  return `${Number(match[2])} ${Number(match[1])} * * *`;
}

function toDailyCron(cron: string | null | undefined) {
  if (!cron || !cron.trim()) return DEFAULT_DAILY_CRON;
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 5) return DEFAULT_DAILY_CRON;
  const minute = Number(parts[0]);
  const hour = Number(parts[1]);
  if (!Number.isInteger(minute) || minute < 0 || minute > 59) return DEFAULT_DAILY_CRON;
  if (!Number.isInteger(hour) || hour < 0 || hour > 23) return DEFAULT_DAILY_CRON;
  return `${minute} ${hour} * * *`;
}

function SectionRow({ title, right, children }: { title: string; right?: string; children?: React.ReactNode }) {
  return (
    <div
      className="flex items-center justify-between px-6 py-4"
      style={{ borderTop: "1px solid #F3F4F6" }}
    >
      <p className="text-sm font-medium text-gray-900">{title}</p>
      <div className="flex items-center gap-3">
        {right && <span className="text-xs text-slate-500">{right}</span>}
        {children}
      </div>
    </div>
  );
}

function LineRow({ title, description, children }: { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <div
      className="flex items-center justify-between px-6 py-4"
      style={{ borderTop: "1px solid #F3F4F6" }}
    >
      <div>
        <p className="text-sm font-medium text-gray-900">{title}</p>
        {description && <p className="text-xs text-gray-400 mt-0.5">{description}</p>}
      </div>
      <div className="flex items-center gap-3 ml-4 shrink-0">{children}</div>
    </div>
  );
}

function Toggle({ value, onChange, disabled = false }: { value: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      onClick={() => !disabled && onChange(!value)}
      disabled={disabled}
      className="relative inline-flex h-7 w-12 items-center rounded-full transition-colors duration-200 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
      style={{ background: value ? "#7B61FF" : "#D1D5DB" }}
      aria-pressed={value}
    >
      <span
        className="inline-block h-5 w-5 rounded-full bg-white transition-transform duration-200 shadow"
        style={{ transform: value ? "translateX(26px)" : "translateX(2px)" }}
      />
    </button>
  );
}
