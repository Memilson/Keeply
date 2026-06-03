"use client";

type Props = { title?: string; subtitle?: string };

export function Topbar({ title, subtitle }: Props) {
  if (!title && !subtitle) return null;

  return (
    <header
      className="sticky top-0 z-20 flex min-h-[68px] items-center bg-white/90 px-5 backdrop-blur lg:px-8"
      style={{ borderBottom: "1px solid #E9E6F4" }}
    >
      <div>
        {title && <h1 className="text-lg font-extrabold" style={{ color: "#18163A" }}>{title}</h1>}
        {subtitle && <p className="mt-1 text-sm" style={{ color: "#6B6993" }}>{subtitle}</p>}
      </div>
    </header>
  );
}
