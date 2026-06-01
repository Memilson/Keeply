"use client";

import { useEffect, useState } from "react";
import { api, type Device } from "@/lib/api";
import { Topbar } from "@/components/Topbar";

type PlanType = "DEFAULT" | "CUSTOM";

type DevicePlan = {
  planType: PlanType;
  sources: string[];
  cdpEnabled: boolean;
  encryptionEnabled: boolean;
  scheduleCron: string | null;
  encryptionPasswordSet: boolean;
  updatedAt?: string;
};

type Draft = {
  planType: PlanType;
  sources: string[];
  cdpEnabled: boolean;
  encryptionEnabled: boolean;
  scheduleCron: string;
  encryptionPassword: string;
  encryptionPasswordConfirm: string;
};

type DeviceWithPlan = Device & { plan: DevicePlan | null; planLoading: boolean };

function parseCron(cron: string | null): { days: number[]; time: string } {
  if (!cron) return { days: [], time: "" };
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 5) return { days: [], time: "" };
  const [min, hour, , , dow] = parts;
  const time = `${hour.padStart(2, "0")}:${min.padStart(2, "0")}`;
  const days = dow === "*" ? [0, 1, 2, 3, 4, 5, 6] : dow.split(",").map(Number).filter((n) => !isNaN(n));
  return { time, days };
}

function buildCron(days: number[], time: string): string | null {
  if (!days.length || !time) return null;
  const [h, m] = time.split(":").map(Number);
  const dow = days.length === 7 ? "*" : days.sort((a, b) => a - b).join(",");
  return `${m} ${h} * * ${dow}`;
}

const DAY_LABELS = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

export default function ProtectionPage() {
  const [devices, setDevices] = useState<DeviceWithPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});

  useEffect(() => {
    (async () => {
      try {
        const devList = await api<Device[]>("/api/devices");
        const withPlan: DeviceWithPlan[] = (devList ?? []).map((d) => ({ ...d, plan: null, planLoading: true }));
        setDevices(withPlan);
        setLoading(false);
        await Promise.all(
          withPlan.map(async (d) => {
            try {
              const plan = await api<DevicePlan>(`/api/devices/${d.id}/plan`);
              setDevices((prev) => prev.map((x) => x.id === d.id ? { ...x, plan, planLoading: false } : x));
            } catch {
              setDevices((prev) => prev.map((x) => x.id === d.id ? { ...x, planLoading: false } : x));
            }
          })
        );
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar dispositivos.");
        setLoading(false);
      }
    })();
  }, []);

  function getDraft(device: DeviceWithPlan): Draft {
    if (drafts[device.id]) return drafts[device.id];
    const { days, time } = parseCron(device.plan?.scheduleCron ?? null);
    return {
      planType: device.plan?.planType ?? "DEFAULT",
      sources: device.plan?.sources ?? [],
      cdpEnabled: device.plan?.cdpEnabled ?? false,
      encryptionEnabled: device.plan?.encryptionEnabled ?? false,
      scheduleCron: buildCron(days, time) ?? "",
      encryptionPassword: "",
      encryptionPasswordConfirm: "",
    };
  }

  function patch(deviceId: string, update: Partial<Draft>) {
    setDrafts((prev) => {
      const device = devices.find((d) => d.id === deviceId)!;
      const base = prev[deviceId] ?? getDraft(device);
      return { ...prev, [deviceId]: { ...base, ...update } };
    });
  }

  function discard(deviceId: string) {
    setDrafts((prev) => { const n = { ...prev }; delete n[deviceId]; return n; });
  }

  async function save(device: DeviceWithPlan) {
    const draft = drafts[device.id];
    if (!draft) return;
    setSaving(device.id);
    try {
      if (draft.encryptionEnabled && draft.encryptionPassword) {
        if (draft.encryptionPassword.length < 8) { setError("Senha de criptografia deve ter ao menos 8 caracteres."); setSaving(null); return; }
        if (draft.encryptionPassword !== draft.encryptionPasswordConfirm) { setError("As senhas não coincidem."); setSaving(null); return; }
      }
      const updated = await api<DevicePlan>(`/api/devices/${device.id}/plan`, {
        method: "PUT",
        body: JSON.stringify({
          planType: draft.planType,
          sources: draft.sources.length ? draft.sources : ["/"],
          cdpEnabled: draft.cdpEnabled,
          encryptionEnabled: draft.encryptionEnabled,
          scheduleCron: draft.scheduleCron || null,
          ...(draft.encryptionPassword ? { encryptionPassword: draft.encryptionPassword } : {}),
        }),
      });
      setDevices((prev) => prev.map((x) => x.id === device.id ? { ...x, plan: updated } : x));
      discard(device.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setSaving(null);
    }
  }

  if (loading) return (
    <>
      <Topbar title="Proteção" subtitle="Planos de backup por dispositivo" />
      <div className="p-7"><p className="text-sm" style={{ color: "#6B6993" }}>Carregando…</p></div>
    </>
  );

  return (
    <>
      <Topbar title="Proteção" subtitle="Planos de backup por dispositivo" />
      <div className="space-y-5 p-7">
        {error && (
          <div className="rounded-xl px-4 py-3 text-sm" style={{ background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626" }}>
            {error}
          </div>
        )}

        {devices.length === 0 && !loading && (
          <div className="kp-card flex flex-col items-center gap-3 px-6 py-14">
            <ShieldIcon />
            <p className="text-sm" style={{ color: "#6B6993" }}>
              Nenhum dispositivo registrado. Instale o agente para configurar a proteção.
            </p>
          </div>
        )}

        {devices.map((device) => {
          const isDirty = !!drafts[device.id];
          const d = getDraft(device);
          const { days, time } = parseCron(d.scheduleCron || null);

          return (
            <div key={device.id} className="kp-card overflow-hidden">
              {/* Header */}
              <div className="flex items-center justify-between gap-4 px-6 py-4" style={{ borderBottom: "1px solid #F0EEF8" }}>
                <div className="flex items-center gap-3">
                  <div className="grid h-9 w-9 place-items-center rounded-xl" style={{ background: "#EDE9FF" }}>
                    <MonitorIcon />
                  </div>
                  <div>
                    <p className="text-sm font-semibold" style={{ color: "#18163A" }}>{device.name || device.hostname}</p>
                    <p className="text-xs" style={{ color: "#6B6993" }}>{device.hostname} · {device.osName ?? "—"}</p>
                  </div>
                </div>
                {device.planLoading && <span className="text-xs" style={{ color: "#6B6993" }}>Carregando plano…</span>}
                {isDirty && (
                  <div className="flex gap-2">
                    <button onClick={() => discard(device.id)}
                      className="rounded-lg border px-3 py-1.5 text-xs font-medium hover:bg-gray-50"
                      style={{ borderColor: "#E4E1F0", color: "#6B6993" }}>
                      Descartar
                    </button>
                    <button onClick={() => save(device)} disabled={saving === device.id}
                      className="rounded-lg px-3 py-1.5 text-xs font-medium text-white disabled:opacity-60"
                      style={{ background: "#7B61FF" }}>
                      {saving === device.id ? "Salvando…" : "Salvar"}
                    </button>
                  </div>
                )}
              </div>

              <div className="divide-y" style={{ borderColor: "#F5F3FC" }}>
                {/* Plan type */}
                <Row label="Plano">
                  <div className="flex rounded-lg overflow-hidden" style={{ border: "1px solid #E4E1F0" }}>
                    {(["DEFAULT", "CUSTOM"] as PlanType[]).map((type) => (
                      <button key={type} onClick={() => patch(device.id, { planType: type })}
                        className="px-3 py-1.5 text-xs font-medium transition-colors"
                        style={{ background: d.planType === type ? "#7B61FF" : "#FAFAFE", color: d.planType === type ? "#fff" : "#6B6993" }}>
                        {type === "DEFAULT" ? "Padrão" : "Personalizado"}
                      </button>
                    ))}
                  </div>
                </Row>

                {/* Sources */}
                <div className="px-6 py-4 space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wider mb-3" style={{ color: "#6B6993" }}>Pastas protegidas</p>
                  {d.sources.length === 0 && <p className="text-sm" style={{ color: "#6B6993" }}>Nenhuma pasta configurada.</p>}
                  {d.sources.map((src) => (
                    <div key={src} className="flex items-center justify-between gap-3 rounded-lg px-3 py-2" style={{ background: "#F5F3FB" }}>
                      <div className="flex items-center gap-2 min-w-0">
                        <FolderIcon />
                        <span className="text-sm truncate" style={{ color: "#18163A" }}>{src}</span>
                      </div>
                      <button onClick={() => patch(device.id, { sources: d.sources.filter((s) => s !== src), planType: "CUSTOM" })}
                        className="shrink-0 text-xs font-medium hover:text-red-600" style={{ color: "#6B6993" }}>×</button>
                    </div>
                  ))}
                  <AddSourceRow onAdd={(p) => {
                    if (!d.sources.includes(p)) patch(device.id, { sources: [...d.sources, p], planType: "CUSTOM" });
                  }} />
                </div>

                {/* Schedule */}
                <div className="px-6 py-4 space-y-3">
                  <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>Agendamento</p>
                  <div className="flex flex-wrap gap-1.5">
                    {DAY_LABELS.map((label, idx) => {
                      const dayNum = idx === 0 ? 0 : idx;
                      const active = days.includes(dayNum);
                      return (
                        <button key={label} onClick={() => {
                          const next = active ? days.filter((d2) => d2 !== dayNum) : [...days, dayNum];
                          patch(device.id, { scheduleCron: buildCron(next, time) ?? "" });
                        }}
                          className="rounded-full px-3 py-1 text-xs font-medium border transition-colors"
                          style={{ background: active ? "#EDE9FF" : "#FAFAFE", color: active ? "#7B61FF" : "#6B6993", borderColor: active ? "#7B61FF" : "#E4E1F0" }}>
                          {label}
                        </button>
                      );
                    })}
                  </div>
                  <div className="flex items-center gap-3">
                    <label className="text-xs" style={{ color: "#6B6993" }}>Horário</label>
                    <input type="time" value={time}
                      onChange={(e) => patch(device.id, { scheduleCron: buildCron(days, e.target.value) ?? "" })}
                      className="rounded-lg border px-3 py-1.5 text-sm focus:outline-none focus:ring-2"
                      style={{ borderColor: "#E4E1F0", color: "#18163A" }} />
                    {d.scheduleCron && <span className="text-xs font-mono" style={{ color: "#9CA3AF" }}>{d.scheduleCron}</span>}
                  </div>
                </div>

                {/* CDP */}
                <Row label="Proteção contínua (CDP)">
                  <Toggle value={d.cdpEnabled} onChange={(v) => patch(device.id, { cdpEnabled: v })} />
                </Row>

                {/* Encryption */}
                <Row label="Criptografia AES-256 · SHA-256">
                  <Toggle value={d.encryptionEnabled} onChange={(v) => patch(device.id, { encryptionEnabled: v, encryptionPassword: "", encryptionPasswordConfirm: "" })} />
                </Row>

                {d.encryptionEnabled && (
                  <div className="px-6 pb-4 space-y-3">
                    {device.plan?.encryptionPasswordSet && !d.encryptionPassword && (
                      <div className="flex items-center gap-2 rounded-lg px-3 py-2" style={{ background: "#ECFDF5", border: "1px solid #A7F3D0" }}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                        <span className="text-xs font-medium" style={{ color: "#065F46" }}>Senha já configurada — redefina abaixo se necessário</span>
                      </div>
                    )}
                    <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: "#6B6993" }}>
                      {device.plan?.encryptionPasswordSet ? "Redefinir senha" : "Definir senha de criptografia"}
                    </p>
                    <input
                      type="password"
                      value={d.encryptionPassword}
                      onChange={(e) => patch(device.id, { encryptionPassword: e.target.value })}
                      placeholder="Senha AES-256 (mín. 8 caracteres)"
                      className="w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2"
                      style={{ borderColor: "#E4E1F0", color: "#18163A" }}
                    />
                    <input
                      type="password"
                      value={d.encryptionPasswordConfirm}
                      onChange={(e) => patch(device.id, { encryptionPasswordConfirm: e.target.value })}
                      placeholder="Confirmar senha"
                      className="w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2"
                      style={{ borderColor: d.encryptionPassword && d.encryptionPassword !== d.encryptionPasswordConfirm ? "#FCA5A5" : "#E4E1F0", color: "#18163A" }}
                    />
                    {d.encryptionPassword && d.encryptionPassword !== d.encryptionPasswordConfirm && (
                      <p className="text-xs" style={{ color: "#DC2626" }}>As senhas não coincidem</p>
                    )}
                    <p className="text-xs" style={{ color: "#9CA3AF" }}>
                      A senha é usada pelo agente para cifrar os dados localmente antes do envio. Guarde-a com segurança — sem ela, os backups não podem ser recuperados.
                    </p>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 px-6 py-4">
      <span className="text-sm font-medium" style={{ color: "#18163A" }}>{label}</span>
      {children}
    </div>
  );
}

function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button onClick={() => onChange(!value)}
      className="relative inline-flex h-6 w-11 shrink-0 rounded-full border-2 border-transparent transition-colors"
      style={{ background: value ? "#7B61FF" : "#D1D5DB" }}>
      <span className="inline-block h-5 w-5 rounded-full bg-white shadow transition-transform"
        style={{ transform: value ? "translateX(20px)" : "translateX(0)" }} />
    </button>
  );
}

function AddSourceRow({ onAdd }: { onAdd: (path: string) => void }) {
  const [value, setValue] = useState("");
  return (
    <div className="flex gap-2 pt-1">
      <input value={value} onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Enter" && value.trim()) { onAdd(value.trim()); setValue(""); } }}
        placeholder="/home/usuario/Documentos"
        className="flex-1 rounded-lg border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2"
        style={{ borderColor: "#E4E1F0", color: "#18163A" }} />
      <button onClick={() => { if (value.trim()) { onAdd(value.trim()); setValue(""); } }}
        className="rounded-lg px-3 py-2 text-sm font-medium text-white"
        style={{ background: "#7B61FF" }}>
        + Adicionar
      </button>
    </div>
  );
}

function ShieldIcon() {
  return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>;
}
function MonitorIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" /></svg>;
}
function FolderIcon() {
  return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" /></svg>;
}
