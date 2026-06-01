"use client";

import { useMemo, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { clearTokens } from "@/lib/api";

type Props = { title?: string; subtitle?: string };

function getStoredUserSnapshot() {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("keeply.user") ?? "";
}

function parseStoredUser(raw: string) {
  try {
    if (!raw) return { initials: "KP", userName: "Conta" };
    const u = JSON.parse(raw) as { name?: string; email?: string };
    const src = u.name ?? u.email ?? "Keeply";
    const parts = src.split(/[\s@]+/).filter(Boolean);
    const initials = `${parts[0]?.[0] ?? "K"}${parts[1]?.[0] ?? ""}`.toUpperCase();
    return { initials, userName: u.name ?? u.email ?? "Conta" };
  } catch {
    return { initials: "KP", userName: "Conta" };
  }
}

function subscribeToStoredUser(callback: () => void) {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

export function Topbar(props: Props) {
  void props;
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const rawUser = useSyncExternalStore(
    subscribeToStoredUser,
    getStoredUserSnapshot,
    () => ""
  );
  const { initials, userName } = useMemo(() => parseStoredUser(rawUser), [rawUser]);

  function logout() {
    clearTokens();
    localStorage.removeItem("keeply.user");
    router.replace("/login");
  }

  return (
    <header className="sticky top-0 z-20 flex min-h-[68px] items-center justify-end gap-4 bg-white/90 px-5 backdrop-blur lg:px-8" style={{ borderBottom: "1px solid #E9E6F4" }}>
      <div className="flex items-center gap-2.5">
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
            <div className="absolute right-0 mt-2 w-48 overflow-hidden rounded-xl bg-white shadow-lg" style={{ border: "1px solid #E4E1F0" }}>
              <div className="px-4 py-3" style={{ borderBottom: "1px solid #F0EEF8" }}>
                <p className="text-xs font-medium" style={{ color: "#18163A" }}>{userName}</p>
                <p className="text-[11px]" style={{ color: "#6B6993" }}>Administrador</p>
              </div>

              <div className="py-1">
                <button
                  onClick={() => {
                    setOpen(false);
                    router.push("/dashboard/perfil");
                  }}
                  className="flex w-full items-center gap-2 px-4 py-2.5 text-sm transition-colors hover:bg-gray-50"
                  style={{ color: "#18163A" }}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                  </svg>
                  Gerenciar Perfil
                </button>

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
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
