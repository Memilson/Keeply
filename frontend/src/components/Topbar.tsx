"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { clearTokens } from "@/lib/api";

type Props = { title: string; subtitle?: string };

export function Topbar({ title, subtitle }: Props) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [initials, setInitials] = useState("KP");
  const [userName, setUserName] = useState("Conta");

  useEffect(() => {
    try {
      const raw = localStorage.getItem("keeply.user");
      if (raw) {
        const u = JSON.parse(raw) as { name?: string; email?: string };
        const src = u.name ?? u.email ?? "Keeply";
        const parts = src.split(/[\s@]+/).filter(Boolean);
        const i = (parts[0]?.[0] ?? "K") + (parts[1]?.[0] ?? "");
        setInitials(i.toUpperCase());
        setUserName(u.name ?? u.email ?? "Conta");
      }
    } catch {}
  }, []);

  function logout() {
    clearTokens();
    localStorage.removeItem("keeply.user");
    router.replace("/login");
  }

  return (
    <header className="sticky top-0 z-20 flex items-center justify-between gap-4 bg-white px-8 py-3.5" style={{ borderBottom: "1px solid #E4E1F0" }}>
      <div className="min-w-0">
        <h1 className="truncate text-lg font-semibold" style={{ color: "#18163A" }}>{title}</h1>
        {subtitle && <p className="truncate text-xs" style={{ color: "#6B6993" }}>{subtitle}</p>}
      </div>

      <div className="flex items-center gap-2.5">
        {/* Search */}
        <div className="hidden items-center gap-2 rounded-lg border px-3 py-2 md:flex" style={{ border: "1px solid #E4E1F0", background: "#F8F7FD" }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#6B6993" strokeWidth="2">
            <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
          </svg>
          <input
            placeholder="Buscar…"
            className="w-52 bg-transparent text-sm focus:outline-none"
            style={{ color: "#18163A" }}
          />
        </div>

        {/* Bell */}
        <button
          className="relative grid h-9 w-9 place-items-center rounded-lg transition-colors hover:bg-gray-50"
          style={{ border: "1px solid #E4E1F0" }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#6B6993" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10 21a2 2 0 0 0 4 0" />
          </svg>
          <span className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full" style={{ background: "#EF4444" }} />
        </button>

        {/* User */}
        <div className="relative">
          <button
            onClick={() => setOpen((o) => !o)}
            className="flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm transition-colors hover:bg-gray-50"
            style={{ border: "1px solid #E4E1F0" }}
          >
            <span
              className="grid h-7 w-7 place-items-center rounded-full text-xs font-semibold text-white"
              style={{ background: "#7B61FF" }}
            >
              {initials}
            </span>
            <span className="hidden pr-1 text-sm font-medium md:inline" style={{ color: "#18163A" }}>
              {userName.length > 18 ? initials : userName.split("@")[0]}
            </span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#6B6993" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="hidden md:block">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </button>

          {open && (
            <div className="absolute right-0 mt-2 w-44 overflow-hidden rounded-xl bg-white shadow-lg" style={{ border: "1px solid #E4E1F0" }}>
              <div className="px-4 py-3" style={{ borderBottom: "1px solid #F0EEF8" }}>
                <p className="text-xs font-medium" style={{ color: "#18163A" }}>{userName}</p>
                <p className="text-[11px]" style={{ color: "#6B6993" }}>Administrador</p>
              </div>
              <button
                onClick={logout}
                className="flex w-full items-center gap-2 px-4 py-2.5 text-sm transition-colors hover:bg-gray-50"
                style={{ color: "#EF4444" }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                </svg>
                Sair
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
