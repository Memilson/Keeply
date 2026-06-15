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
        style={{ background: "#F4F5F7" }}
      >
        <p className="text-sm text-gray-400">Carregando…</p>
      </main>
    );
  }

  return (
    <div
      className="fixed inset-0 flex overflow-hidden"
      style={{ background: "#F4F5F7" }}
    >
      <Sidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-y-auto">
        {children}
      </div>
    </div>
  );
}
