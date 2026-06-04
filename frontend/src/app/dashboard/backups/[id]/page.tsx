"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import {
  API_BASE,
  api,
  getAccessToken,
  type Snapshot,
  type SnapshotNode,
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

type NodeListResponse = {
  items?: SnapshotNode[];
};

function toTreeNodes(nodes: SnapshotNode[]): TreeNode[] {
  return nodes.map((node) => ({
    name: node.name,
    path: node.path,
    isDir: node.directory,
    size: node.size ?? undefined,
    lastModified: node.lastModified ?? undefined,
    children: [],
    loaded: false,
    loading: false,
  }));
}

async function fetchChildren(snapshotId: string, prefix: string): Promise<TreeNode[]> {
  const qs = new URLSearchParams();
  if (prefix) qs.set("prefix", prefix);
  const res = await api<NodeListResponse>(`/api/snapshots/${snapshotId}/nodes?${qs}`);
  return toTreeNodes(res.items ?? []);
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
  downloadingFiles: Set<string>;
  onToggle: (path: string) => void;
  onExpanded: (path: string, children: TreeNode[]) => void;
  onDownload: (path: string) => void;
  onSelectionChange: (path: string, checked: boolean) => void;
};

function TreeRow({ node, depth, snapshotId, selectedPaths, downloadingFiles, onToggle, onExpanded, onDownload, onSelectionChange }: TreeRowProps) {
  const isOpen = node.isDir && node.loaded;
  const checked = selectedPaths.has(node.path);

  async function handleExpand() {
    if (node.loading) return;
    if (node.loaded) {
      onToggle(node.path);
      return;
    }
    onToggle(node.path);
    const children = await fetchChildren(snapshotId, node.path);
    onExpanded(node.path, children);
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
            <span className="flex-1 min-w-0 text-sm truncate pr-3" style={{ color: "#374151" }} title={node.path}>{node.name}</span>
            <span className="hidden w-[76px] shrink-0 text-right text-xs sm:block" style={{ color: "#9CA3AF" }}>
              {formatBytes(node.size ?? 0)}
            </span>
            <span className="hidden w-[144px] shrink-0 text-right text-xs md:block" style={{ color: "#9CA3AF" }}>
              {formatDateTime(node.lastModified)}
            </span>
            <button
              onClick={(e) => { e.stopPropagation(); onDownload(node.path); }}
              disabled={downloadingFiles.has(node.path)}
              className="w-[88px] shrink-0 rounded-full border px-2.5 py-0.5 text-center text-xs font-medium transition-colors hover:bg-[#EDE9FF] disabled:opacity-50 disabled:cursor-not-allowed"
              style={{ borderColor: "#E4E1F0", color: "#7B61FF" }}
            >
              {downloadingFiles.has(node.path) ? "Baixando…" : "Baixar"}
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
          downloadingFiles={downloadingFiles}
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
  const [treeLoading, setTreeLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadingFiles, setDownloadingFiles] = useState<Set<string>>(new Set());
  const [downloadMsg, setDownloadMsg] = useState<string | null>(null);
  const [selectionMsg, setSelectionMsg] = useState<string | null>(null);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());

  useEffect(() => {
    (async () => {
      try {
        setSnapshot(await api<Snapshot>(`/api/snapshots/${id}`));
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
        const nodes = await fetchChildren(id, "");
        if (!cancelled) setRoots(nodes);
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
    if (downloadingFiles.has(path)) return;
    setDownloadingFiles((prev) => new Set(prev).add(path));
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
    } finally {
      setDownloadingFiles((prev) => { const next = new Set(prev); next.delete(path); return next; });
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

  const loading = treeLoading;
  const selectedCount = selectedPaths.size;
  const canDownloadSelected = !!snapshot && snapshot.status === "COMPLETED" && selectedCount > 0 && selectedCount <= MAX_SELECTED_FILES && !downloading;

  return (
    <>
      <Topbar />
      <div className="space-y-6 px-0 pb-8 pt-6">
        <Link href="/dashboard/backups" className="inline-flex items-center gap-2 text-sm text-keeply-700 hover:text-keeply-800">
          <i className="bi bi-arrow-left" aria-hidden="true" />
          <span>Backups</span>
        </Link>

        {downloadMsg && <p className="rounded-xl bg-keeply-100 px-4 py-3 text-sm text-keeply-800">{downloadMsg}</p>}
        {selectionMsg && <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">{selectionMsg}</p>}
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

        <section className="overflow-hidden">
          <div className="flex flex-col gap-4 border-b border-keeply-100 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-base font-semibold text-keeply-ink">Arquivos</h2>
              <span className="text-sm text-keeply-ink/60">{snapshot?.sourcePath ?? "—"}</span>
              <button
                onClick={downloadSelected}
                disabled={!canDownloadSelected}
                className="kp-btn-primary rounded-full px-5 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
              >
                {downloading ? "Preparando…" : `Baixar selecionados (${selectedCount}/${MAX_SELECTED_FILES})`}
              </button>
            </div>
            {snapshot && (
              <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                <InfoChip label="Status" value={snapshot.status} />
                <InfoChip label="Arquivos" value={String(snapshot.totalFiles ?? 0)} />
                <InfoChip label="Original" value={formatBytes(snapshot.totalOriginalSize ?? 0)} />
                <InfoChip label="Armazenado" value={formatBytes(snapshot.totalCompressedSize ?? 0)} />
              </div>
            )}
          </div>

          {loading ? (
            <p className="px-6 py-8 text-sm text-keeply-ink/50">Carregando arquivos…</p>
          ) : snapshot?.status !== "COMPLETED" ? (
            <p className="px-6 py-12 text-center text-sm text-keeply-ink/60">
              Arquivos disponíveis apenas para backups concluídos.
            </p>
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
                  downloadingFiles={downloadingFiles}
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

function InfoChip({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-full border px-3 py-1.5 text-xs" style={{ borderColor: "#E4E1F0", background: "#FAFAFE" }}>
      <span style={{ color: "#6B6993" }}>{label}: </span>
      <span className="font-semibold" style={{ color: "#18163A" }}>{value}</span>
    </div>
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
