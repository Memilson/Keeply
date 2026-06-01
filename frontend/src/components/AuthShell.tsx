import Link from "next/link";
import { ReactNode } from "react";
import { KeeplyLogo } from "./KeeplyLogo";

type Props = { title: string; subtitle: string; children: ReactNode; footer: ReactNode };

export function AuthShell({ title, subtitle, children, footer }: Props) {
  return (
    <main className="flex flex-1 items-center justify-center px-6 py-12" style={{ background: "#F5F3FB" }}>
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Link href="/" className="inline-flex">
            <KeeplyLogo size={34} />
          </Link>
        </div>

        <div className="text-center">
          <h1 className="text-2xl font-bold" style={{ color: "#18163A" }}>{title}</h1>
          <p className="mt-1.5 text-sm" style={{ color: "#6B6993" }}>{subtitle}</p>
        </div>

        <div className="mt-7 rounded-2xl bg-white p-8" style={{ border: "1px solid #E4E1F0", boxShadow: "0 2px 8px rgba(24, 22, 58, 0.06)" }}>
          {children}
        </div>

        <div className="mt-5 text-center text-sm" style={{ color: "#6B6993" }}>{footer}</div>
      </div>
    </main>
  );
}

export function AuthInput(
  props: React.InputHTMLAttributes<HTMLInputElement> & { label: string }
) {
  const { label, id, ...rest } = props;
  const inputId = id ?? rest.name;
  return (
    <label htmlFor={inputId} className="block">
      <span className="text-sm font-medium" style={{ color: "#18163A" }}>{label}</span>
      <input
        id={inputId}
        {...rest}
        className="mt-1.5 w-full rounded-lg px-3.5 py-2.5 text-sm transition-shadow focus:outline-none focus:ring-2"
        style={{
          border: "1px solid #E4E1F0",
          color: "#18163A",
          background: "#FAFAFE",
        }}
      />
    </label>
  );
}
