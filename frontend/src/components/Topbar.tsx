"use client";

import { useEffect, useState } from "react";
import { KeeplyAiAssistant } from "./KeeplyAiAssistant";

type Props = {
  title?: string;
  subtitle?: string;
  action?: React.ReactNode;
};

export function Topbar({ title, subtitle, action }: Props) {
  const [time, setTime] = useState("");

  useEffect(() => {
    function tick() {
      setTime(
        new Date().toLocaleTimeString("pt-BR", {
          hour: "2-digit",
          minute: "2-digit",
        })
      );
    }
    tick();
    const id = setInterval(tick, 30_000);
    return () => clearInterval(id);
  }, []);

  if (!title && !subtitle) return null;

  return (
    <header
      className="sticky top-0 z-20 flex min-h-[58px] items-center justify-between px-6"
      style={{
        background: "rgba(8,7,26,0.85)",
        backdropFilter: "blur(12px)",
        WebkitBackdropFilter: "blur(12px)",
        borderBottom: "1px solid rgba(255,255,255,0.06)",
      }}
    >
      <div className="flex items-center gap-2">
        <span className="text-[11px] font-semibold text-slate-600">Keeply</span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-3 w-3 text-slate-700">
          <polyline points="9 18 15 12 9 6" />
        </svg>
        {title && (
          <h1 className="text-[13px] font-semibold text-slate-300">{title}</h1>
        )}
        {subtitle && (
          <span className="ml-1 text-[11px] text-slate-600">{subtitle}</span>
        )}
      </div>

      <div className="flex items-center gap-4">
        {time && (
          <span className="text-[11px] tabular-nums font-medium text-slate-600">
            {time}
          </span>
        )}
        <KeeplyAiAssistant />
        {action}
      </div>
    </header>
  );
}
