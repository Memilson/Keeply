"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import {
  API_BASE,
  api,
  authHeaders,
  handleAuthResponse,
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
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </svg>
  );
}

function FolderIcon({ open }: { open: boolean }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill={open ? "rgba(123,97,255,0.3)" : "rgba(123,97,255,0.1)"} stroke="#7B61FF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
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
      stroke="#64748B"
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
    if (node.loaded) { onToggle(node.path); return; }
    onToggle(node.path);
    const children = await fetchChildren(snapshotId, node.path);
    onExpanded(node.path, children);
  }

  return (
    <div>
      <div
        className="flex items-center gap-2 px-4 py-1.5 cursor-pointer select-none transition-colors duration-150"
        style={{
          paddingLeft: `${16 + depth * 20}px`,
          background: "transparent",
        }}
        onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.03)"; }}
        onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "transparent"; }}
        onClick={node.isDir ? handleExpand : undefined}
      >
        {node.isDir ? (
          <>
            <span className="w-3 flex items-center justify-center">
              {node.loading ? (
                <span className="h-2 w-2 rounded-full border border-[#7B61FF] border-t-transparent animate-spin inline-block" />
              ) : (
                <ChevronIcon open={isOpen} />
              )}
            </span>
            <FolderIcon open={isOpen} />
            <span className="text-sm font-medium text-white">{node.name}</span>
          </>
        ) : (
          <>
            <span className="w-3" />
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => onSelectionChange(node.path, e.target.checked)}
              onClick={(e) => e.stopPropagation()}
              className="h-4 w-4 shrink-0"
              style={{ accentColor: "#7B61FF" }}
              aria-label={`Selecionar ${node.path}`}
            />
            <FileIcon />
            <span className="flex-1 min-w-0 text-sm truncate pr-3 text-slate-400" title={node.path}>{node.name}</span>
            <span className="hidden w-[76px] shrink-0 text-right text-xs text-slate-600 sm:block tabular-nums">
              {formatBytes(node.size ?? 0)}
            </span>
            <span className="hidden w-[144px] shrink-0 text-right text-xs text-slate-600 md:block">
              {formatDateTime(node.lastModified)}
            </span>
            <button
              onClick={(e) => { e.stopPropagation(); onDownload(node.path); }}
              disabled={downloadingFiles.has(node.path)}
              className="w-[88px] shrink-0 rounded-full border px-2.5 py-0.5 text-center text-xs font-semibold transition-colors duration-200 hover:bg-[#7B61FF]/20 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              style={{ borderColor: "rgba(123,97,255,0.3)", color: "#A78BFA" }}
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
    return () => { cancelled = true; };
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
      const res = await fetch(`${API_BASE}/api/snapshots/${id}/archive-selected`, {
        method: "POST",
        headers: authHeaders("application/json"),
        body: JSON.stringify({ paths: Array.from(selectedPaths) }),
      });
      if (handleAuthResponse(res)) return;
      if (!res.ok) throw new Error(await readErrorMessage(res));
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
      const qs = new URLSearchParams({ path });
      const res = await fetch(`${API_BASE}/api/snapshots/${id}/files/download?${qs}`, {
        headers: authHeaders(),
      });
      if (handleAuthResponse(res)) return;
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
      <Topbar title="Detalhe do backup" />
      <div className="space-y-5 px-6 pb-8 pt-5">
        <Link
          href="/dashboard/backups"
          className="inline-flex items-center gap-2 text-sm font-semibold text-[#A78BFA] hover:text-[#7B61FF] transition-colors duration-200 cursor-pointer"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          Backups
        </Link>

        {downloadMsg && (
          <div
            className="rounded-xl border px-4 py-3 text-sm text-[#A78BFA]"
            style={{ background: "rgba(123,97,255,0.1)", borderColor: "rgba(123,97,255,0.2)" }}
          >
            {downloadMsg}
          </div>
        )}
        {selectionMsg && (
          <div
            className="rounded-xl border px-4 py-3 text-sm text-[#F59E0B]"
            style={{ background: "rgba(245,158,11,0.1)", borderColor: "rgba(245,158,11,0.2)" }}
          >
            {selectionMsg}
          </div>
        )}
        {error && (
          <div
            className="rounded-xl border px-4 py-3 text-sm text-[#EF4444]"
            style={{ background: "rgba(239,68,68,0.08)", borderColor: "rgba(239,68,68,0.2)" }}
          >
            {error}
          </div>
        )}

        <div
          className="rounded-xl border bg-[#100F1E] overflow-hidden"
          style={{ borderColor: "rgba(255,255,255,0.08)" }}
        >
          <div
            className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"
            style={{ borderBottom: "1px solid rgba(255,255,255,0.08)" }}
          >
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-sm font-bold text-white">Arquivos</h2>
              <span className="text-sm text-slate-500">{snapshot?.sourcePath ?? "—"}</span>
              <button
                onClick={downloadSelected}
                disabled={!canDownloadSelected}
                className="rounded-full px-4 py-1.5 text-xs font-semibold text-white transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                style={{ background: canDownloadSelected ? "#7B61FF" : "rgba(123,97,255,0.3)" }}
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
            <p className="px-5 py-8 text-sm text-slate-500">Carregando arquivos…</p>
          ) : snapshot?.status !== "COMPLETED" ? (
            <p className="px-5 py-12 text-center text-sm text-slate-500">
              Arquivos disponíveis apenas para backups concluídos.
            </p>
          ) : roots.length === 0 ? (
            <p className="px-5 py-12 text-center text-sm text-slate-500">Nenhum arquivo encontrado.</p>
          ) : (
            <div className="overflow-y-auto pb-1 pt-3" style={{ maxHeight: "calc(100vh - 240px)" }}>
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
        </div>
      </div>
    </>
  );
}

function InfoChip({ label, value }: { label: string; value: string }) {
  return (
    <div
      className="rounded-full border px-3 py-1 text-xs"
      style={{ borderColor: "rgba(255,255,255,0.1)", background: "rgba(255,255,255,0.04)" }}
    >
      <span className="text-slate-500">{label}: </span>
      <span className="font-semibold text-slate-300">{value}</span>
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
