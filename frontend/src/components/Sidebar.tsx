"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo, useState, useSyncExternalStore } from "react";
import { KeeplyMark } from "./KeeplyLogo";

type Item = { href: string; label: string; icon: React.ReactNode };

const ICONS: Record<string, React.ReactNode> = {
  grid: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="2" />
      <rect x="14" y="3" width="7" height="7" rx="2" />
      <rect x="3" y="14" width="7" height="7" rx="2" />
      <rect x="14" y="14" width="7" height="7" rx="2" />
    </svg>
  ),
  server: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="7" rx="2" />
      <rect x="3" y="13" width="18" height="7" rx="2" />
      <circle cx="7.5" cy="7.5" r="0.8" fill="currentColor" />
      <circle cx="7.5" cy="16.5" r="0.8" fill="currentColor" />
    </svg>
  ),
  archive: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="4" rx="1.5" />
      <path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" />
      <path d="M10 12h4" />
    </svg>
  ),
  chart: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
    </svg>
  ),
  shield: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6l-8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  ),
  collapse: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M11 19l-7-7 7-7" /><path d="M18 19l-7-7 7-7" />
    </svg>
  ),
  expand: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M13 5l7 7-7 7" /><path d="M6 5l7 7-7 7" />
    </svg>
  ),
};

const NAV_ITEMS: Item[] = [
  { href: "/dashboard", label: "Visão geral", icon: ICONS.grid },
  { href: "/dashboard/machines", label: "Máquinas", icon: ICONS.server },
  { href: "/dashboard/activities", label: "Atividades", icon: ICONS.chart },
  { href: "/dashboard/protection", label: "Proteção", icon: ICONS.shield },
];

function getStored() {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("keeply.user") ?? "";
}
function subscribe(cb: () => void) {
  window.addEventListener("storage", cb);
  return () => window.removeEventListener("storage", cb);
}
function parseUser(raw: string) {
  try {
    if (!raw) return { initials: "KP", name: "Conta", role: "Administrador" };
    const u = JSON.parse(raw) as { name?: string; email?: string; role?: string };
    const src = u.name ?? u.email ?? "Keeply";
    const parts = src.split(/[\s@]+/).filter(Boolean);
    return {
      initials: `${parts[0]?.[0] ?? "K"}${parts[1]?.[0] ?? ""}`.toUpperCase(),
      name: u.name ?? u.email ?? "Conta",
      role: u.role ?? "Administrador",
    };
  } catch {
    return { initials: "KP", name: "Conta", role: "Administrador" };
  }
}

export function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const raw = useSyncExternalStore(subscribe, getStored, () => "");
  const user = useMemo(() => parseUser(raw), [raw]);

  const W = collapsed ? 52 : 196;

  return (
    <aside
      className="hidden h-screen shrink-0 flex-col overflow-hidden lg:flex"
      style={{
        width: W,
        minWidth: W,
        background: "#1B2541",
        borderRight: "1px solid rgba(255,255,255,0.09)",
        transition: "width 220ms cubic-bezier(0.4,0,0.2,1), min-width 220ms cubic-bezier(0.4,0,0.2,1)",
      }}
      aria-label="Sidebar"
    >
      {/* Logo */}
      <div
        className="flex items-center gap-2.5 px-3.5"
        style={{ height: 58, borderBottom: "1px solid rgba(255,255,255,0.09)" }}
      >
        <div className="shrink-0 flex items-center justify-center">
          <KeeplyMark size={24} />
        </div>
        {!collapsed && (
          <span
            className="truncate text-[14px] font-bold tracking-tight"
            style={{ color: "#E2E8F0" }}
          >
            Keeply
          </span>
        )}
      </div>

      {/* Nav */}
      <nav className="flex-1 px-2 pt-3 pb-1 space-y-px">
        {NAV_ITEMS.map((item) => {
          const isExact = item.href === "/dashboard";
          const active = isExact
            ? pathname === item.href
            : pathname.startsWith(item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              title={collapsed ? item.label : undefined}
              aria-current={active ? "page" : undefined}
              className="relative flex items-center gap-2.5 rounded-lg px-2.5 py-2.5 text-[13px] font-medium outline-none transition-colors duration-150"
              style={{
                color: active ? "#C4B5FD" : "#94A3B8",
                background: active
                  ? "linear-gradient(90deg, rgba(123,97,255,0.18), rgba(123,97,255,0.08))"
                  : "transparent",
              }}
              onMouseEnter={(e) => {
                if (!active) {
                  (e.currentTarget as HTMLElement).style.color = "#E2E8F0";
                  (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.07)";
                }
              }}
              onMouseLeave={(e) => {
                if (!active) {
                  (e.currentTarget as HTMLElement).style.color = "#94A3B8";
                  (e.currentTarget as HTMLElement).style.background = "transparent";
                }
              }}
              onFocus={(e) => {
                (e.currentTarget as HTMLElement).style.outline = "2px solid rgba(123,97,255,0.5)";
              }}
              onBlur={(e) => {
                (e.currentTarget as HTMLElement).style.outline = "none";
              }}
            >
              {/* Active left bar */}
              {active && (
                <span
                  className="absolute left-0 top-1.5 bottom-1.5 rounded-r-full"
                  style={{
                    width: "2.5px",
                    background: "#7B61FF",
                    boxShadow: "0 0 8px #7B61FF, 0 0 16px rgba(123,97,255,0.4)",
                  }}
                />
              )}

              <span
                className="flex h-[18px] w-[18px] shrink-0 items-center justify-center"
                aria-hidden="true"
              >
                {item.icon}
              </span>

              {!collapsed && (
                <span className="truncate leading-none">{item.label}</span>
              )}
            </Link>
          );
        })}
      </nav>

      {/* Bottom area */}
      <div
        className="px-2 pb-2 pt-2 space-y-1"
        style={{ borderTop: "1px solid rgba(255,255,255,0.05)" }}
      >
        {/* Collapse btn */}
        <button
          type="button"
          onClick={() => setCollapsed((v) => !v)}
          className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-[12px] font-medium transition-colors duration-150 cursor-pointer"
          style={{ color: "#94A3B8" }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLElement).style.color = "#E2E8F0";
            (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.07)";
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.color = "#94A3B8";
            (e.currentTarget as HTMLElement).style.background = "transparent";
          }}
          aria-label={collapsed ? "Expandir menu" : "Recolher menu"}
        >
          <span className="flex h-[18px] w-[18px] shrink-0 items-center justify-center" aria-hidden="true">
            {collapsed ? ICONS.expand : ICONS.collapse}
          </span>
          {!collapsed && <span>Recolher</span>}
        </button>

        {/* User */}
        <div className="flex items-center gap-2.5 rounded-lg px-2.5 py-2">
          <span
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[11px] font-bold text-white"
            style={{
              minWidth: 28,
              background: "linear-gradient(135deg,#7B61FF,#5B3FE0)",
              boxShadow: "0 2px 8px rgba(123,97,255,0.35)",
            }}
            aria-hidden="true"
          >
            {user.initials}
          </span>
          {!collapsed && (
            <div className="min-w-0">
              <p className="truncate text-[12px] font-semibold leading-tight" style={{ color: "#D1D5DB" }}>
                {user.name.split("@")[0]}
              </p>
              <p className="truncate text-[10px] leading-tight mt-0.5" style={{ color: "#94A3B8" }}>
                {user.role}
              </p>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
