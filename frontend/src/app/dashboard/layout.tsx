"use client";

import { ReactNode } from "react";
import { Sidebar } from "@/components/Sidebar";
import { useAuthGuard } from "@/lib/useAuthGuard";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  const ready = useAuthGuard();

  if (!ready) {
    return (
      <main
        className="flex h-screen flex-1 items-center justify-center overflow-hidden"
        style={{ background: "#0D0C1A" }}
      >
        <p className="text-sm text-slate-500">Carregando…</p>
      </main>
    );
  }

  return (
    <div
      className="fixed inset-0 flex overflow-hidden"
      style={{ background: "#0D0C1A" }}
    >
      <Sidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-y-auto">
        {children}
      </div>
    </div>
  );
}
