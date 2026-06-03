"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo, useSyncExternalStore } from "react";
import { KeeplyMark } from "./KeeplyLogo";

type Item = { href: string; label: string; icon: React.ReactNode; activePrefix?: string };

const ICONS = {
  grid: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  ),
  server: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" /><circle cx="7" cy="7.5" r="0.7" fill="currentColor" /><circle cx="7" cy="16.5" r="0.7" fill="currentColor" />
    </svg>
  ),
  archive: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" />
    </svg>
  ),
  agent: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 8h8v8H8z" /><path d="M4 10h4M4 14h4M16 10h4M16 14h4M10 4v4M14 4v4M10 16v4M14 16v4" />
    </svg>
  ),
  shield: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6l-8-3Z" /><path d="m9 12 2 2 4-4" />
    </svg>
  ),
  chart: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 19h16" /><path d="M7 19V11" /><path d="M12 19V6" /><path d="M17 19v-5" />
    </svg>
  ),
  cog: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
    </svg>
  ),
};

const ITEMS: Item[] = [
  { href: "/dashboard", label: "Visão geral", icon: ICONS.grid },
  { href: "/dashboard/machines", label: "Máquinas", icon: ICONS.server },
  { href: "/dashboard/activities", label: "Atividades", icon: ICONS.chart },
];

function getStoredUserSnapshot() {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("keeply.user") ?? "";
}

function subscribeToStoredUser(callback: () => void) {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function parseStoredUser(raw: string) {
  try {
    if (!raw) return { initials: "KP", userName: "Conta" };
    const user = JSON.parse(raw) as { name?: string; email?: string };
    const source = user.name ?? user.email ?? "Keeply";
    const parts = source.split(/[\s@]+/).filter(Boolean);
    const initials = `${parts[0]?.[0] ?? "K"}${parts[1]?.[0] ?? ""}`.toUpperCase();
    return { initials, userName: user.name ?? user.email ?? "Conta" };
  } catch {
    return { initials: "KP", userName: "Conta" };
  }
}

export function Sidebar() {
  const pathname = usePathname();
  const rawUser = useSyncExternalStore(subscribeToStoredUser, getStoredUserSnapshot, () => "");
  const { initials, userName } = useMemo(() => parseStoredUser(rawUser), [rawUser]);

  return (
    <aside className="hidden h-screen w-[276px] shrink-0 flex-col overflow-hidden p-3 lg:flex" style={{ background: "#F8F7FD", borderRight: "1px solid #E9E6F4" }}>
      {/* Logo */}
      <div className="flex items-center gap-3 rounded-2xl px-4 py-4">
        <KeeplyMark size={30} />
        <div className="min-w-0">
          <span className="block text-[17px] font-semibold tracking-tight" style={{ color: "#18163A" }}>Keeply</span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 space-y-1 px-1 py-5">
        {ITEMS.map((it) => {
          const activeBase = it.activePrefix ?? it.href.split("#")[0];
          const active = pathname === activeBase || (activeBase !== "/dashboard" && pathname.startsWith(activeBase));
          return (
            <Link
              key={it.href}
              href={it.href}
              style={active ? { background: "#EDE9FF", color: "#6046F0", boxShadow: "0 6px 18px rgba(123, 97, 255, 0.12)" } : { color: "#6B6993" }}
              className={`flex items-center gap-3 rounded-xl px-3.5 py-3 text-sm font-medium transition-all duration-150 ${
                active ? "" : "hover:bg-white hover:text-[#18163A]"
              }`}
            >
              <span className="grid h-5 w-5 shrink-0 place-items-center">{it.icon}</span>
              <span>{it.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Version */}
      <button className="mt-3 flex items-center gap-2 rounded-xl px-3 py-2.5 text-left text-xs font-medium transition-colors hover:bg-white" style={{ color: "#8A87A8" }}>
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="m15 18-6-6 6-6" />
        </svg>
        Recolher menu
      </button>

      <div className="mt-2 flex items-center gap-3 rounded-xl px-3 py-3">
        <span
          className="grid h-9 w-9 shrink-0 place-items-center rounded-full text-xs font-semibold text-white"
          style={{ background: "#7B61FF" }}
        >
          {initials}
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold" style={{ color: "#18163A" }}>
            {userName.split("@")[0]}
          </p>
          <p className="truncate text-[11px]" style={{ color: "#8A87A8" }}>
            Administrador
          </p>
        </div>
      </div>

      <div className="px-3 pb-1 pt-2 text-[10px]" style={{ color: "#A9A6C0" }}>
        Keeply v1.0
      </div>
    </aside>
  );
}
