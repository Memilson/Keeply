"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { AuthShell, AuthInput } from "@/components/AuthShell";
import { api, setTokens, type AuthResponse } from "@/lib/api";
import { authCountdownLabel, resolveAuthError } from "@/lib/authFeedback";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);

  useEffect(() => {
    if (!cooldownUntil) return;
    const timer = window.setInterval(() => {
      if (cooldownUntil <= Date.now()) {
        setCooldownUntil(null);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [cooldownUntil]);

  const cooldownLabel = authCountdownLabel(cooldownUntil);
  const blocked = !!cooldownLabel;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (blocked) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api<AuthResponse>("/api/auth/login", {
        method: "POST",
        auth: false,
        body: JSON.stringify({ email, password }),
      });
      setTokens(res.accessToken, res.refreshToken);
      try {
        localStorage.setItem(
          "keeply.user",
          JSON.stringify({ userId: res.userId, email: res.email })
        );
      } catch {}
      router.push("/dashboard");
    } catch (err) {
      const feedback = resolveAuthError(err);
      setError(feedback.message);
      if (feedback.cooldownUntil) setCooldownUntil(feedback.cooldownUntil);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      title="Entrar"
      subtitle=""
      footer={
        <>
          Não tem conta?{" "}
          <Link href="/register" className="font-semibold text-[#A78BFA] transition hover:text-white">
            Criar agora
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <AuthInput
          label="E-mail"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="voce@empresa.com"
        />
        <AuthInput
          label="Senha"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
        />
        {error && (
          <p className="rounded-lg border border-[#EF4444]/20 bg-[#EF4444]/10 px-3 py-2 text-sm text-[#EF4444]">
            {error}
          </p>
        )}
        {cooldownLabel && (
          <p className="rounded-lg border border-[#F59E0B]/20 bg-[#F59E0B]/10 px-3 py-2 text-sm text-[#F59E0B]">
            Novo login liberado em {cooldownLabel}.
          </p>
        )}
        <button
          type="submit"
          disabled={loading || blocked}
          className="mt-1 w-full cursor-pointer rounded-lg bg-[#7B61FF] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#6046F0] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? "Entrando…" : blocked ? `Aguarde ${cooldownLabel}` : "Entrar"}
        </button>
      </form>
    </AuthShell>
  );
}
