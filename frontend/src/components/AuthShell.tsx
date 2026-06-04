import Link from "next/link";
import { ReactNode } from "react";
import { KeeplyLogo } from "./KeeplyLogo";

type ShellProps = { title: string; subtitle: string; children: ReactNode; footer: ReactNode };

export function AuthShell({ title, subtitle, children, footer }: ShellProps) {
  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-[#0D0C1A] px-6 py-12">
      {/* grid pattern */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.12] [background-image:linear-gradient(rgba(123,97,255,0.4)_1px,transparent_1px),linear-gradient(90deg,rgba(123,97,255,0.4)_1px,transparent_1px)] [background-size:48px_48px]"
      />
      {/* glow */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-0 h-[500px] w-[700px] -translate-x-1/2 rounded-full bg-[#7B61FF] opacity-[0.07] blur-[100px]"
      />

      {/* back to home */}
      <div className="relative mb-8 w-full max-w-[440px]">
        <Link href="/" aria-label="Voltar para início">
          <KeeplyLogo size={28} wordmarkColor="#FFFFFF" />
        </Link>
      </div>

      {/* card */}
      <div className="relative w-full max-w-[440px] overflow-hidden rounded-2xl border border-white/10 bg-[#100F1E] shadow-[0_32px_80px_rgba(0,0,0,0.5)]">
        {/* window chrome strip */}
        <div className="flex items-center gap-1.5 border-b border-white/10 px-5 py-3">
          <span className="h-2 w-2 rounded-full bg-[#EF4444]/50" aria-hidden />
          <span className="h-2 w-2 rounded-full bg-[#F59E0B]/50" aria-hidden />
          <span className="h-2 w-2 rounded-full bg-[#10B981]/50" aria-hidden />
          <span className="ml-2 font-mono text-[10px] text-slate-600">keeply · auth</span>
        </div>

        <div className="px-7 pb-7 pt-6">
          <h1 className="text-2xl font-black text-white">{title}</h1>
          {subtitle ? (
            <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
          ) : null}

          <div className="mt-6">{children}</div>

          <div className="mt-6 border-t border-white/10 pt-5 text-center text-sm text-slate-500">
            {footer}
          </div>
        </div>
      </div>
    </main>
  );
}

export function AuthInput(
  props: React.InputHTMLAttributes<HTMLInputElement> & { label: string }
) {
  const { label, id, className: _cls, ...rest } = props;
  const inputId = id ?? rest.name;
  return (
    <label htmlFor={inputId} className="block">
      <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</span>
      <input
        id={inputId}
        {...rest}
        className="mt-1.5 w-full rounded-lg border border-white/10 bg-white/5 px-3.5 py-2.5 text-sm text-white placeholder-slate-600 transition focus:border-[#7B61FF]/60 focus:bg-white/8 focus:outline-none focus:ring-2 focus:ring-[#7B61FF]/30"
      />
    </label>
  );
}
