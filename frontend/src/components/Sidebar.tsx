"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { KeeplyMark } from "./KeeplyLogo";

type Item = { href: string; label: string; icon: React.ReactNode };

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
  { href: "/dashboard/backups", label: "Backups", icon: ICONS.archive },
  { href: "/dashboard/protection", label: "Proteção", icon: ICONS.shield },
];

export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="hidden w-60 shrink-0 flex-col lg:flex" style={{ background: "#FFFFFF", borderRight: "1px solid #E9E6F4" }}>
      {/* Logo */}
      <div className="flex items-center gap-2 px-5 py-5" style={{ borderBottom: "1px solid #F0EEF8" }}>
        <KeeplyMark size={26} />
        <span className="text-[17px] font-semibold tracking-tight" style={{ color: "#18163A" }}>Keeply</span>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-0.5">
        {ITEMS.map((it) => {
          const active = pathname === it.href || (it.href !== "/dashboard" && pathname.startsWith(it.href));
          return (
            <Link
              key={it.href}
              href={it.href}
              style={active ? { background: "#EDE9FF", color: "#6046F0" } : { color: "#6B6993" }}
              className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-150 ${
                active ? "" : "hover:bg-[#F5F3FB] hover:text-[#18163A]"
              }`}
            >
              <span className="grid h-5 w-5 place-items-center shrink-0">{it.icon}</span>
              <span>{it.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Bottom agent install card */}
      <div className="mx-3 mb-4 rounded-xl p-4" style={{ background: "#F5F3FB", border: "1px solid #E9E6F4" }}>
        <div className="flex items-start gap-2.5">
          <div className="grid h-7 w-7 shrink-0 place-items-center rounded-lg" style={{ background: "#EDE9FF" }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7B61FF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 3 4 6v6c0 5 3.5 8.5 8 9 4.5-.5 8-4 8-9V6l-8-3Z" />
            </svg>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-semibold" style={{ color: "#18163A" }}>Instalar agente</p>
            <p className="mt-0.5 text-[11px] leading-relaxed" style={{ color: "#6B6993" }}>Adicione novas máquinas ao seu ambiente.</p>
          </div>
        </div>
        <button className="mt-3 w-full rounded-lg py-1.5 text-xs font-medium text-white transition-colors hover:opacity-90" style={{ background: "#7B61FF" }}>
          Ver guia
        </button>
      </div>

      {/* Version */}
      <div className="px-5 pb-4 text-[10px]" style={{ color: "#A9A6C0" }}>
        Keeply v1.0
      </div>
    </aside>
  );
}
