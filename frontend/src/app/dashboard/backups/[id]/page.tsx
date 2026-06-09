"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import type { ReactNode } from "react";
import { use, useCallback, useEffect, useMemo, useState } from "react";
import {
  SnapshotKindPill,
  SnapshotStatusPill,
} from "@/components/backup-ui";
import { Topbar } from "@/components/Topbar";
import {
  API_BASE,
  api,
  authHeaders,
  handleAuthResponse,
  type Device,
  type PagedResponse,
  type Snapshot,
  type SnapshotNode,
} from "@/lib/api";
import { deviceName, inferSnapshotType } from "@/lib/backup-view";
import { formatBytes, formatDateTime } from "@/lib/format";

type NodeListResponse = {
  items?: SnapshotNode[];
};

type FileEntry = {
  path: string;
  name: string;
  directory: boolean;
  size?: number | null;
  lastModified?: string | null;
};

export default function BackupDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const searchParams = useSearchParams();
  const requestedDir = searchParams.get("dir") ?? "";

  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [allSnapshots, setAllSnapshots] = useState<Snapshot[]>([]);
  const [nodeCache, setNodeCache] = useState<Record<string, FileEntry[]>>({});
  const [loadingDirectory, setLoadingDirectory] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadMsg, setDownloadMsg] = useState<string | null>(null);
  const [selectionMsg, setSelectionMsg] = useState<string | null>(null);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());
  const currentPath = useMemo(
    () => normalizeDirectoryPrefix(requestedDir, snapshot?.sourcePath),
    [requestedDir, snapshot?.sourcePath],
  );

  useEffect(() => {
    (async () => {
      try {
        setError(null);
        const [snapshotData, deviceList, snapshotPage] = await Promise.all([
          api<Snapshot>(`/api/snapshots/${id}`),
          api<Device[]>("/api/devices"),
          api<PagedResponse<Snapshot>>("/api/snapshots"),
        ]);
        setSnapshot(snapshotData);
        setDevices(deviceList ?? []);
        setAllSnapshots(snapshotPage?.items ?? []);
        setNodeCache({});
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Falha ao carregar snapshot.");
      }
    })();
  }, [id]);

  const loadDirectory = useCallback(
    async (prefix: string) => {
      if (!snapshot || snapshot.status !== "COMPLETED") return;
      if (nodeCache[prefix]) return;

      setLoadingDirectory(true);
      try {
        const query = new URLSearchParams();
        if (prefix) query.set("prefix", prefix);
        const response = await api<NodeListResponse>(`/api/snapshots/${id}/nodes?${query.toString()}`);
        setNodeCache((current) => ({
          ...current,
          [prefix]: (response.items ?? []).map((item) => ({
            path: item.path,
            name: item.name,
            directory: item.directory,
            size: item.size,
            lastModified: item.lastModified,
          })),
        }));
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Falha ao carregar arquivos.");
      } finally {
        setLoadingDirectory(false);
      }
    },
    [id, nodeCache, snapshot],
  );

  useEffect(() => {
    if (!snapshot || snapshot.status !== "COMPLETED") return;
    const timer = window.setTimeout(() => {
      void loadDirectory(currentPath);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [currentPath, loadDirectory, snapshot]);

  const currentEntries = nodeCache[currentPath] ?? [];

  const relatedSnapshots = useMemo(() => {
    if (!snapshot) return [];
    return allSnapshots
      .filter(
        (item) =>
          item.deviceId === snapshot.deviceId &&
          item.sourcePath === snapshot.sourcePath,
      )
      .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
  }, [allSnapshots, snapshot]);

  const device = useMemo(
    () => devices.find((item) => item.id === snapshot?.deviceId) ?? null,
    [devices, snapshot?.deviceId],
  );

  const breadcrumbItems = useMemo(() => {
    const source = snapshot?.sourcePath || "/";
    const pieces = currentPath.split("/").filter(Boolean);
    const items = [{ label: source, path: "" }];
    let pathBuilder = "";
    for (const piece of pieces) {
      pathBuilder = `${pathBuilder}${piece}/`;
      items.push({ label: piece, path: pathBuilder });
    }
    return items;
  }, [currentPath, snapshot]);

  const selectedCount = selectedPaths.size;
  const canDownloadSelected =
    !!snapshot &&
    snapshot.status === "COMPLETED" &&
    selectedCount > 0 &&
    !downloading;

  const navigateToDirectory = useCallback(
    (path: string) => {
      const normalizedPath = normalizeDirectoryPrefix(path, snapshot?.sourcePath);
      const query = normalizedPath ? `?dir=${encodeURIComponent(normalizedPath)}` : "";
      router.replace(`/dashboard/backups/${id}${query}`);
      setSelectedPaths(new Set());
    },
    [id, router, snapshot?.sourcePath],
  );

  const handleSelectionChange = useCallback((path: string, checked: boolean) => {
    setSelectionMsg(null);
    setSelectedPaths((current) => {
      const next = new Set(current);
      if (checked) {
        if (next.has(path)) return current;
        next.add(path);
        return next;
      }
      next.delete(path);
      return next;
    });
  }, []);

  async function downloadArchive(paths: string[], filename: string) {
    if (!snapshot || snapshot.status !== "COMPLETED" || paths.length === 0 || downloading) return;
    setDownloading(true);
    setDownloadMsg(null);
    setSelectionMsg(null);
    try {
      const response = await fetch(`${API_BASE}/api/snapshots/${id}/archive-selected`, {
        method: "POST",
        headers: authHeaders("application/json"),
        body: JSON.stringify({ paths }),
      });
      if (handleAuthResponse(response)) return;
      if (!response.ok) throw new Error(await readErrorMessage(response));
      const blob = await response.blob();
      triggerDownload(blob, filename);
    } catch (cause) {
      setDownloadMsg(cause instanceof Error ? cause.message : "Falha no download do ZIP.");
    } finally {
      setDownloading(false);
    }
  }

  async function downloadSelected() {
    if (!canDownloadSelected) return;
    await downloadArchive(Array.from(selectedPaths), `keeply-selected-${id}.zip`);
  }

  async function downloadSnapshot() {
    await downloadArchive([""], `keeply-snapshot-${id}.zip`);
  }

  return (
    <>
      <Topbar title="Explorar snapshot" />
      <div className="min-h-0 flex-1 overflow-hidden">
        <div className="border-b px-4 py-3 md:px-6" style={{ borderColor: "rgba(148,163,184,0.1)" }}>
          <Link href="/dashboard/machines" className="text-sm font-semibold text-[#A78BFA] transition-colors duration-200 hover:text-[#C4B5FD]">
            Voltar para máquinas
          </Link>
        </div>

        <div className="px-4 pt-3 md:px-6">
          {downloadMsg ? <Banner tone="info">{downloadMsg}</Banner> : null}
          {selectionMsg ? <Banner tone="warn">{selectionMsg}</Banner> : null}
          {error ? <Banner tone="error">{error}</Banner> : null}
        </div>

        <div className="grid h-[calc(100%-49px)] min-h-0 xl:grid-cols-[280px_minmax(0,1fr)]">
          <aside className="min-h-0 overflow-y-auto border-r px-4 py-4 md:px-6" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
            <div className="mb-4">
              <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">Histórico</p>
              <h2 className="mt-1 text-base font-semibold text-slate-50">Snapshots</h2>
            </div>

            <div className="space-y-2">
              {relatedSnapshots.length === 0 ? (
                <p className="text-sm text-slate-500">Nenhum snapshot relacionado encontrado.</p>
              ) : (
                relatedSnapshots.map((item) => (
                  <Link
                    key={item.id}
                    href={`/dashboard/backups/${item.id}${currentPath ? `?dir=${encodeURIComponent(currentPath)}` : ""}`}
                    className="block rounded-lg border px-3 py-3 transition-colors duration-150 hover:bg-white/5"
                    style={{
                      borderColor: item.id === id ? "rgba(167,139,250,0.38)" : "rgba(148,163,184,0.12)",
                      background: item.id === id ? "rgba(123,97,255,0.12)" : "transparent",
                    }}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-semibold text-slate-100">{formatDateTime(item.startedAt)}</span>
                      <SnapshotStatusPill status={item.status} />
                    </div>
                    <p className="mt-1 truncate text-xs text-slate-500">{item.sourcePath}</p>
                  </Link>
                ))
              )}
            </div>
          </aside>

          <main className="min-h-0 overflow-y-auto px-4 py-4 md:px-6">
            <section className="overflow-hidden rounded-lg border" style={{ borderColor: "rgba(148,163,184,0.14)", background: "rgba(15,23,42,0.58)" }}>
              <div className="flex flex-col gap-4 border-b px-5 py-4 lg:flex-row lg:items-start lg:justify-between" style={{ borderColor: "rgba(148,163,184,0.12)" }}>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-lg font-semibold text-slate-50">{device ? deviceName(device) : "Dispositivo"}</h2>
                    {snapshot ? <SnapshotStatusPill status={snapshot.status} /> : null}
                    {snapshot ? <SnapshotKindPill type={inferSnapshotType(allSnapshots, snapshot)} /> : null}
                  </div>
                  <p className="mt-1 truncate text-sm text-slate-400">{snapshot?.sourcePath ?? "Origem não informada"}</p>
                  {snapshot ? (
                    <p className="mt-2 text-xs text-slate-500">
                      {formatDateTime(snapshot.startedAt)} · {snapshot.totalFiles ?? 0} arquivos · {formatBytes(snapshot.totalCompressedSize ?? 0)}
                    </p>
                  ) : null}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={downloadSnapshot}
                    disabled={!snapshot || snapshot.status !== "COMPLETED" || downloading}
                    className="inline-flex items-center rounded-lg border px-3 py-2 text-xs font-semibold text-slate-200 transition-colors duration-200 hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-50"
                    style={{ borderColor: "rgba(148,163,184,0.18)" }}
                  >
                    Baixar snapshot
                  </button>
                  <button
                    type="button"
                    onClick={downloadSelected}
                    disabled={!canDownloadSelected}
                    className="inline-flex items-center rounded-lg px-3 py-2 text-xs font-semibold text-slate-950 transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50"
                    style={{ background: "#4ADE80" }}
                  >
                    {downloading ? "Preparando..." : `Baixar selecionados (${selectedCount})`}
                  </button>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3" style={{ borderColor: "rgba(148,163,184,0.1)" }}>
                {breadcrumbItems.map((item, index) => (
                  <button
                    key={`${item.label}-${item.path}`}
                    type="button"
                    onClick={() => navigateToDirectory(item.path)}
                    className="inline-flex items-center rounded-lg border px-2.5 py-1.5 text-xs font-semibold text-slate-200 transition-colors duration-200 hover:bg-white/5"
                    style={{ borderColor: "rgba(148,163,184,0.16)" }}
                  >
                    {index > 0 ? <span className="mr-2 text-slate-500">/</span> : null}
                    {item.label || "/"}
                  </button>
                ))}
              </div>

              {snapshot?.status !== "COMPLETED" ? (
                <p className="px-5 py-8 text-sm text-slate-500">Arquivos disponíveis apenas para snapshots concluídos.</p>
              ) : loadingDirectory && !currentEntries.length ? (
                <p className="px-5 py-8 text-sm text-slate-500">Carregando diretório...</p>
              ) : currentEntries.length === 0 ? (
                <p className="px-5 py-8 text-sm text-slate-500">Nenhum arquivo encontrado neste caminho.</p>
              ) : (
                <div>
                  <div className="grid grid-cols-[minmax(0,1fr)_110px_150px] gap-3 border-b px-5 py-3 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500" style={{ borderColor: "rgba(148,163,184,0.12)", background: "rgba(2,6,23,0.34)" }}>
                    <span>Nome</span>
                    <span className="text-right">Tamanho</span>
                    <span className="text-right">Modificado</span>
                  </div>
                  <div>
                    {currentEntries.map((entry) => {
                      const isChecked = selectedPaths.has(entry.path);
                      return (
                        <div
                          key={entry.path}
                          className="grid grid-cols-[minmax(0,1fr)_110px_150px] gap-3 border-b px-5 py-3 transition-colors duration-200 last:border-b-0 hover:bg-white/5"
                          style={{ borderColor: "rgba(148,163,184,0.08)" }}
                        >
                          <div className="flex min-w-0 items-center gap-3">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={(event) => handleSelectionChange(entry.path, event.target.checked)}
                              className="h-4 w-4 shrink-0"
                              style={{ accentColor: "#4ADE80" }}
                              aria-label={`Selecionar ${entry.path}`}
                            />
                            {entry.directory ? (
                              <button
                                type="button"
                                onClick={() => navigateToDirectory(entry.path)}
                                className="flex min-w-0 items-center gap-3 text-left text-sm font-semibold text-slate-100"
                              >
                                <FolderIcon />
                                <span className="truncate">{entry.name}</span>
                              </button>
                            ) : (
                              <>
                                <FileIcon />
                                <span className="truncate text-sm text-slate-200">{entry.name}</span>
                              </>
                            )}
                          </div>
                          <div className="text-right text-sm text-slate-400">
                            {entry.directory ? "Pasta" : formatBytes(entry.size ?? 0)}
                          </div>
                          <div className="text-right text-sm text-slate-500">
                            {formatDateTime(entry.lastModified ?? undefined)}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </section>
          </main>
        </div>
      </div>
    </>
  );
}

function Banner({ children, tone }: { children: ReactNode; tone: "info" | "warn" | "error" }) {
  const toneMap = {
    info: { border: "rgba(167,139,250,0.24)", background: "rgba(76,29,149,0.18)", color: "#C4B5FD" },
    warn: { border: "rgba(251,191,36,0.24)", background: "rgba(120,53,15,0.18)", color: "#FBBF24" },
    error: { border: "rgba(248,113,113,0.24)", background: "rgba(127,29,29,0.18)", color: "#F87171" },
  } as const;
  const theme = toneMap[tone];
  return (
    <div className="mb-4 rounded-2xl border px-4 py-3 text-sm" style={{ borderColor: theme.border, background: theme.background, color: theme.color }}>
      {children}
    </div>
  );
}

function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#FBBF24" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  );
}

function FileIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#94A3B8" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 shrink-0">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
    </svg>
  );
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function normalizeDirectoryPrefix(path: string, sourcePath?: string): string {
  let normalized = path.trim();
  if (!normalized) return "";

  const source = (sourcePath ?? "").trim().replace(/\/+$/, "");
  if (source && normalized.startsWith(source)) {
    normalized = normalized.slice(source.length);
  }

  normalized = normalized.replace(/^\/+/, "");
  if (!normalized) return "";
  return normalized.endsWith("/") ? normalized : `${normalized}/`;
}

async function readErrorMessage(res: Response): Promise<string> {
  const contentType = res.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const body = await res.json().catch(() => null);
    if (body && typeof body === "object") {
      if ("message" in body && typeof body.message === "string") return body.message;
      if ("error" in body && typeof body.error === "string") return body.error;
    }
  }
  const text = await res.text().catch(() => "");
  return text || `HTTP ${res.status}`;
}
