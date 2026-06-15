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
        background: "#FFFFFF",
        borderBottom: "1px solid #E5E7EB",
      }}
    >
      <div className="flex items-center gap-2">
        <span className="text-[11px] font-semibold text-gray-400">Keeply</span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-3 w-3 text-gray-300">
          <polyline points="9 18 15 12 9 6" />
        </svg>
        {title && (
          <h1 className="text-[13px] font-semibold text-gray-800">{title}</h1>
        )}
        {subtitle && (
          <span className="ml-1 text-[11px] text-gray-400">{subtitle}</span>
        )}
      </div>

      <div className="flex items-center gap-4">
        {time && (
          <span className="text-[11px] tabular-nums font-medium text-gray-400">
            {time}
          </span>
        )}
        <KeeplyAiAssistant />
        {action}
      </div>
    </header>
  );
}
