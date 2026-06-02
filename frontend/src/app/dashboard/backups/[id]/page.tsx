"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useRef, useState } from "react";
import {
  API_BASE,
  api,
  getAccessToken,
  type Snapshot,
  type SnapshotFile,
} from "@/lib/api";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Topbar } from "@/components/Topbar";

const MAX_SELECTED_FILES = 10;

type TreeNode = {
  name: string;
  path: string;
  isDir: boolean;
  size?: number;
  lastModified?: string;
  children: TreeNode[];
  loaded: boolean;
  loading: boolean;
};

type FileListResponse = {
  items?: SnapshotFile[];
  pagination?: { totalElements?: number };
  total?: number;
};

function buildImmediateChildren(prefix: string, files: SnapshotFile[]): TreeNode[] {
  const folderMap = new Map<string, true>();
  const nodes: TreeNode[] = [];

  for (const f of files) {
    if (!f.path.startsWith(prefix)) continue;
    const rel = f.path.slice(prefix.length);
    if (!rel) continue;
    const slash = rel.indexOf("/");
    if (slash < 0) {
      nodes.push({ name: rel, path: f.path, isDir: false, size: f.size, lastModified: f.lastModified, children: [], loaded: true, loading: false });
    } else {
      const folderName = rel.slice(0, slash);
      const folderPath = prefix + folderName + "/";
      if (!folderMap.has(folderPath)) {
        folderMap.set(folderPath, true);
        nodes.push({ name: folderName, path: folderPath, isDir: true, children: [], loaded: false, loading: false });
      }
    }
  }

  return nodes.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
}

async function fetchChildren(snapshotId: string, prefix: string): Promise<SnapshotFile[]> {
  const all: SnapshotFile[] = [];
  let page = 0;
  const size = 500;
  while (true) {
    const qs = new URLSearchParams({ page: String(page), size: String(size), prefix });
    const res = await api<FileListResponse>(`/api/snapshots/${snapshotId}/files?${qs}`);
    const batch = res.items ?? [];
    all.push(...batch);
    const total = res.pagination?.totalElements ?? res.total ?? batch.length;
    if (all.length >= total || batch.length < size) break;
    page++;
  }
  return all;
}

function toggleNode(nodes: TreeNode[], path: string): TreeNode[] {
  return nodes.map((n) => {
    if (n.path === path) {
      if (!n.loaded) return { ...n, loading: true };
      return { ...n, loaded: false, children: [] };
    }
    if (n.isDir && n.children.length > 0) return { ...n, children: toggleNode(n.children, path) };
    return n;
  });
}

function markLoaded(nodes: TreeNode[], path: string, children: TreeNode[]): TreeNode[] {
  return nodes.map((n) => {
    if (n.path === path) return { ...n, loaded: true, loading: false, children };
    if (n.isDir && n.children.length > 0) return { ...n, children: markLoaded(n.children, path, children) };
    return n;
  });
}

function FileIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </svg>
  );
}

function FolderIcon({ open }: { open: boolean }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill={open ? "#EDE9FF" : "#F3F4F6"} stroke="#7B61FF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  );
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="10"
      height="10"
      viewBox="0 0 24 24"
      fill="none"
      stroke="#9CA3AF"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      style={{ transform: open ? "rotate(90deg)" : "rotate(0deg)", transition: "transform 150ms" }}
    >
      <polyline points="9 18 15 12 9 6" />
    </svg>
  );
}

type TreeRowProps = {
  node: TreeNode;
  depth: number;
  snapshotId: string;
  selectedPaths: Set<string>;
  onToggle: (path: string) => void;
  onExpanded: (path: string, children: TreeNode[]) => void;
  onDownload: (path: string) => void;
  onSelectionChange: (path: string, checked: boolean) => void;
};

function TreeRow({ node, depth, snapshotId, selectedPaths, onToggle, onExpanded, onDownload, onSelectionChange }: TreeRowProps) {
  const isOpen = node.isDir && node.loaded;
  const checked = selectedPaths.has(node.path);

  async function handleExpand() {
    if (node.loading) return;
    if (node.loaded) {
      onToggle(node.path);
      return;
    }
    onToggle(node.path);
    const files = await fetchChildren(snapshotId, node.path);
    onExpanded(node.path, buildImmediateChildren(node.path, files));
  }

  return (
    <div>
      <div
        className="flex items-center gap-2 px-4 py-1.5 hover:bg-[#F5F3FB] cursor-pointer select-none"
        style={{ paddingLeft: `${16 + depth * 20}px` }}
        onClick={node.isDir ? handleExpand : undefined}
      >
        {node.isDir ? (
          <>
            <span className="w-3 flex items-center justify-center">
              {node.loading ? <span className="h-2 w-2 rounded-full border border-[#7B61FF] border-t-transparent animate-spin inline-block" /> : <ChevronIcon open={isOpen} />}
            </span>
            <FolderIcon open={isOpen} />
            <span className="text-sm font-medium" style={{ color: "#18163A" }}>{node.name}</span>
          </>
        ) : (
          <>
            <span className="w-3" />
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => onSelectionChange(node.path, e.target.checked)}
              onClick={(e) => e.stopPropagation()}
              className="h-4 w-4 shrink-0 accent-keeply-700"
              aria-label={`Selecionar ${node.path}`}
            />
            <FileIcon />
            <span className="flex-1 min-w-0 text-sm truncate" style={{ color: "#374151" }} title={node.path}>{node.name}</span>
            <span className="text-xs shrink-0" style={{ color: "#9CA3AF" }}>{formatBytes(node.size ?? 0)}</span>
            <span className="text-xs shrink-0 hidden sm:block" style={{ color: "#9CA3AF" }}>{formatDateTime(node.lastModified)}</span>
            <button
              onClick={(e) => { e.stopPropagation(); onDownload(node.path); }}
              className="shrink-0 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors hover:bg-[#EDE9FF]"
              style={{ borderColor: "#E4E1F0", color: "#7B61FF" }}
            >
              Baixar
            </button>
          </>
        )}
      </div>

      {node.isDir && node.loaded && node.children.map((child) => (
        <TreeRow
          key={child.path}
          node={child}
          depth={depth + 1}
          snapshotId={snapshotId}
          selectedPaths={selectedPaths}
          onToggle={onToggle}
          onExpanded={onExpanded}
          onDownload={onDownload}
          onSelectionChange={onSelectionChange}
        />
      ))}
    </div>
  );
}

export default function BackupDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [roots, setRoots] = useState<TreeNode[]>([]);
  const [search, setSearch] = useState("");
  const [searchFiles, setSearchFiles] = useState<SnapshotFile[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [treeLoading, setTreeLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadMsg, setDownloadMsg] = useState<string | null>(null);
  const [selectionMsg, setSelectionMsg] = useState<string | null>(null);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const all = await api<Snapshot[]>("/api/snapshots");
        setSnapshot((all ?? []).find((s) => s.id === id) ?? null);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha ao carregar snapshot.");
      }
    })();
  }, [id]);

  useEffect(() => {
    if (!snapshot || snapshot.status !== "COMPLETED") return;

    let cancelled = false;
    const loadTree = async () => {
      setTreeLoading(true);
      try {
        const files = await fetchChildren(id, "");
        if (!cancelled) setRoots(buildImmediateChildren("", files));
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Falha ao carregar arquivos.");
      } finally {
        if (!cancelled) setTreeLoading(false);
      }
    };

    void loadTree();
    return () => {
      cancelled = true;
    };
  }, [id, snapshot]);

  useEffect(() => {
    if (!search.trim()) return;
    if (searchTimer.current) clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(async () => {
      setSearchLoading(true);
      try {
        const qs = new URLSearchParams({ page: "0", size: "200", search: search.trim() });
        const res = await api<FileListResponse>(`/api/snapshots/${id}/files?${qs}`);
        setSearchFiles(res.items ?? []);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Falha na busca.");
      } finally {
        setSearchLoading(false);
      }
    }, 300);
    return () => {
      if (searchTimer.current) clearTimeout(searchTimer.current);
    };
  }, [search, id]);

  const handleToggle = useCallback((path: string) => {
    setRoots((prev) => toggleNode(prev, path));
  }, []);

  const handleExpanded = useCallback((path: string, children: TreeNode[]) => {
    setRoots((prev) => markLoaded(prev, path, children));
  }, []);

  const handleSelectionChange = useCallback((path: string, checked: boolean) => {
    setSelectionMsg(null);
    setSelectedPaths((prev) => {
      const next = new Set(prev);
      if (checked) {
        if (next.has(path)) return prev;
        if (next.size >= MAX_SELECTED_FILES) {
          setSelectionMsg(`Você pode selecionar no máximo ${MAX_SELECTED_FILES} arquivos por ZIP.`);
          return prev;
        }
        next.add(path);
        return next;
      }
      next.delete(path);
      return next;
    });
  }, []);

  async function downloadSelected() {
    if (selectedPaths.size === 0 || selectedPaths.size > MAX_SELECTED_FILES) return;
    setDownloading(true);
    setDownloadMsg(null);
    setSelectionMsg(null);
    try {
      const token = getAccessToken();
      const res = await fetch(`${API_BASE}/api/snapshots/${id}/archive-selected`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ paths: Array.from(selectedPaths) }),
      });
      if (!res.ok) {
        throw new Error(await readErrorMessage(res));
      }
      const blob = await res.blob();
      triggerDownload(blob, `keeply-selected-${id}.zip`);
    } catch (e) {
      setDownloadMsg(e instanceof Error ? e.message : "Falha no download do ZIP.");
    } finally {
      setDownloading(false);
    }
  }

  async function downloadFile(path: string) {
    try {
      const token = getAccessToken();
      const qs = new URLSearchParams({ path });
      const res = await fetch(`${API_BASE}/api/snapshots/${id}/files/download?${qs}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!res.ok) throw new Error(await readErrorMessage(res));
      const blob = await res.blob();
      triggerDownload(blob, path.split("/").pop() ?? "arquivo");
    } catch (e) {
      setDownloadMsg(e instanceof Error ? e.message : "Falha no download do arquivo.");
    }
  }

  function triggerDownload(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  const isSearching = search.trim().length > 0;
  const loading = treeLoading;
  const selectedCount = selectedPaths.size;
  const canDownloadSelected = !!snapshot && snapshot.status === "COMPLETED" && selectedCount > 0 && selectedCount <= MAX_SELECTED_FILES && !downloading;

  return (
    <>
      <Topbar title={snapshot?.sourcePath ?? "Snapshot"} subtitle="Detalhes do backup" />
      <div className="space-y-6 p-8">
        <Link href="/dashboard/backups" className="text-sm text-keeply-700 hover:text-keeply-800">
          ← Backups
        </Link>

        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm text-keeply-ink/50">Snapshot</p>
            <h1 className="text-2xl font-semibold tracking-tight text-keeply-ink">
              {snapshot?.sourcePath ?? "—"}
            </h1>
            <p className="mt-1 text-sm text-keeply-ink/60">
              Iniciado em {formatDateTime(snapshot?.startedAt)} ·{" "}
              {snapshot ? formatBytes(snapshot.totalCompressedSize ?? 0) : "—"} ·{" "}
              {snapshot?.totalFiles ?? 0} arquivos
            </p>
          </div>
        </header>

        {downloadMsg && <p className="rounded-xl bg-keeply-100 px-4 py-3 text-sm text-keeply-800">{downloadMsg}</p>}
        {selectionMsg && <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">{selectionMsg}</p>}
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

        <section className="kp-card overflow-hidden">
          <div className="flex flex-col gap-4 border-b border-keeply-100 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-base font-semibold text-keeply-ink">Arquivos</h2>
              <button
                onClick={downloadSelected}
                disabled={!canDownloadSelected}
                className="kp-btn-primary rounded-full px-5 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
              >
                {downloading ? "Preparando…" : `Baixar selecionados (${selectedCount}/${MAX_SELECTED_FILES})`}
              </button>
            </div>
            <input
              type="search"
              value={search}
              onChange={(e) => {
                const value = e.target.value;
                setSearch(value);
                if (!value.trim()) {
                  setSearchFiles([]);
                }
              }}
              placeholder="Buscar arquivo…"
              className="rounded-xl border border-keeply-200 bg-white px-3 py-1.5 text-sm focus:border-keeply-500 focus:outline-none focus:ring-2 focus:ring-keeply-200"
            />
          </div>

          {loading ? (
            <p className="px-6 py-8 text-sm text-keeply-ink/50">Carregando arquivos…</p>
          ) : snapshot?.status !== "COMPLETED" ? (
            <p className="px-6 py-12 text-center text-sm text-keeply-ink/60">
              Arquivos disponíveis apenas para backups concluídos.
            </p>
          ) : isSearching ? (
            searchLoading ? (
              <p className="px-6 py-8 text-sm text-keeply-ink/50">Buscando…</p>
            ) : searchFiles.length === 0 ? (
              <p className="px-6 py-12 text-center text-sm text-keeply-ink/60">Nenhum arquivo encontrado.</p>
            ) : (
              <div className="overflow-y-auto" style={{ maxHeight: "520px" }}>
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-keeply-soft/60 text-left text-xs uppercase tracking-wide text-keeply-ink/50">
                    <tr>
                      <th className="px-6 py-3 w-12">Sel.</th>
                      <th className="px-6 py-3">Caminho</th>
                      <th className="px-6 py-3">Tamanho</th>
                      <th className="px-6 py-3">Modificado em</th>
                      <th className="px-6 py-3" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-keeply-100">
                    {searchFiles.map((f) => (
                      <tr key={f.path} className="hover:bg-keeply-soft/30">
                        <td className="px-6 py-2.5">
                          <input
                            type="checkbox"
                            checked={selectedPaths.has(f.path)}
                            onChange={(e) => handleSelectionChange(f.path, e.target.checked)}
                            className="h-4 w-4 accent-keeply-700"
                            aria-label={`Selecionar ${f.path}`}
                          />
                        </td>
                        <td className="px-6 py-2.5 text-keeply-ink">
                          <span className="block max-w-[520px] truncate" title={f.path}>{f.path}</span>
                        </td>
                        <td className="px-6 py-2.5 text-keeply-ink/70">{formatBytes(f.size)}</td>
                        <td className="px-6 py-2.5 text-keeply-ink/70">{formatDateTime(f.lastModified)}</td>
                        <td className="px-6 py-2.5 text-right">
                          <button
                            onClick={() => downloadFile(f.path)}
                            className="rounded-full border border-keeply-200 px-3 py-1 text-xs font-medium text-keeply-700 hover:bg-keeply-50"
                          >
                            Baixar
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : roots.length === 0 ? (
            <p className="px-6 py-12 text-center text-sm text-keeply-ink/60">Nenhum arquivo encontrado.</p>
          ) : (
            <div className="overflow-y-auto py-1" style={{ maxHeight: "520px" }}>
              {roots.map((node) => (
                <TreeRow
                  key={node.path}
                  node={node}
                  depth={0}
                  snapshotId={id}
                  selectedPaths={selectedPaths}
                  onToggle={handleToggle}
                  onExpanded={handleExpanded}
                  onDownload={downloadFile}
                  onSelectionChange={handleSelectionChange}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  );
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
