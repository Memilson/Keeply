type Props = { size?: number; withWordmark?: boolean; className?: string; wordmarkColor?: string };

export function KeeplyLogo({ size = 32, withWordmark = true, className = "", wordmarkColor }: Props) {
  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      <KeeplyMark size={size} />
      {withWordmark && (
        <span
          className="text-xl font-semibold tracking-tight"
          style={{ color: wordmarkColor ?? "#18163A" }}
        >
          Keeply
        </span>
      )}
    </div>
  );
}

export function KeeplyMark({ size = 32, className = "" }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-label="Keeply"
    >
      <defs>
        <linearGradient id="kp-star" x1="6" y1="8" x2="40" y2="46" gradientUnits="userSpaceOnUse">
          <stop stopColor="#9C8BFF" />
          <stop offset="1" stopColor="#6C4DFF" />
        </linearGradient>
      </defs>
      <path
        d="M20 6
           C22 19 24.5 23.5 41 26
           C24.5 28.5 22 33 20 46
           C18 33 15.5 28.5 -1 26
           C15.5 23.5 18 19 20 6 Z"
        transform="translate(2 -2)"
        fill="url(#kp-star)"
      />
      <path
        d="M39 5
           C39.7 9.5 40.8 10.6 45 11.3
           C40.8 12 39.7 13.1 39 17.5
           C38.3 13.1 37.2 12 33 11.3
           C37.2 10.6 38.3 9.5 39 5 Z"
        fill="url(#kp-star)"
      />
    </svg>
  );
}
