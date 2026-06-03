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
          <Link href="/register" className="font-medium text-keeply-700 hover:text-keeply-800">
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
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
        )}
        {cooldownLabel && (
          <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Novo login liberado em {cooldownLabel}.
          </p>
        )}
        <button
          type="submit"
          disabled={loading || blocked}
          className="kp-btn-primary w-full rounded-xl px-4 py-2.5 text-sm font-semibold"
        >
          {loading ? "Entrando…" : blocked ? `Aguarde ${cooldownLabel}` : "Entrar"}
        </button>
      </form>
    </AuthShell>
  );
}
