import Link from "next/link";
import { ReactNode } from "react";
import { KeeplyLogo } from "./KeeplyLogo";

type Props = { title: string; subtitle: string; children: ReactNode; footer: ReactNode };

export function AuthShell({ title, subtitle, children, footer }: Props) {
  return (
    <main
      className="relative flex flex-1 items-center justify-center overflow-hidden px-6 py-10"
      style={{
        background:
          "radial-gradient(circle at 50% 0%, rgba(123,97,255,0.10), transparent 28%), #F5F3FB",
      }}
    >
      <div
        className="pointer-events-none absolute -left-[14%] top-[58%] h-[520px] w-[520px] rounded-full"
        style={{ border: "1px solid rgba(123, 97, 255, 0.14)" }}
      />
      <div
        className="pointer-events-none absolute -right-[12%] -top-[8%] h-[520px] w-[520px] rounded-full"
        style={{ border: "1px solid rgba(123, 97, 255, 0.14)" }}
      />

      <div
        className="relative w-full max-w-[500px] rounded-[26px] bg-white px-5 py-6 sm:px-7 sm:py-7"
        style={{
          border: "1px solid #E8E2FA",
          boxShadow: "0 26px 80px rgba(95, 75, 255, 0.10)",
        }}
      >
        <div className="flex justify-center">
          <Link href="/" className="inline-flex">
            <KeeplyLogo size={54} />
          </Link>
        </div>

        <div className="mt-5 text-center">
          <h1 className="text-[1.8rem] font-bold sm:text-[2.05rem]" style={{ color: "#18163A" }}>{title}</h1>
          <p className="mt-1.5 text-[14px]" style={{ color: "#6B6993" }}>{subtitle}</p>
        </div>

        <div className="mx-auto mt-6 max-w-[390px]">
          {children}
        </div>

        <div
          className="mx-auto mt-6 max-w-[390px] border-t pt-4 text-center text-sm"
          style={{ borderColor: "#EEE9FA", color: "#6B6993" }}
        >
          {footer}
        </div>
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
