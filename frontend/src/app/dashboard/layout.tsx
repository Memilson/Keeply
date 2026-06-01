"use client";

import { ReactNode } from "react";
import { Sidebar } from "@/components/Sidebar";
import { useAuthGuard } from "@/lib/useAuthGuard";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  const ready = useAuthGuard();

  if (!ready) {
    return (
      <main className="flex flex-1 items-center justify-center" style={{ background: "#F5F3FB" }}>
        <p className="text-sm" style={{ color: "#6B6993" }}>Carregando…</p>
      </main>
    );
  }

  return (
    <div className="flex flex-1" style={{ background: "#F5F3FB" }}>
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  );
}
