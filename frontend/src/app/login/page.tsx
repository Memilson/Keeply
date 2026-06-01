"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthShell, AuthInput } from "@/components/AuthShell";
import { api, ApiError, setTokens, type AuthResponse } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
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
      if (err instanceof ApiError) {
        if (err.status === 401 || err.status === 403) {
          setError("E-mail ou senha incorretos.");
        } else {
          setError(err.message || `Erro ${err.status}`);
        }
      } else if (err instanceof TypeError) {
        setError(
          "Não foi possível conectar ao servidor. Verifique se o backend está rodando em http://localhost:8080 e se foi reiniciado após a última alteração."
        );
      } else {
        setError("Erro inesperado. Tente novamente.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      title="Entrar na sua conta"
      subtitle="Acesse seu painel de proteção."
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
        <button
          type="submit"
          disabled={loading}
          className="kp-btn-primary w-full rounded-xl px-4 py-2.5 text-sm font-semibold"
        >
          {loading ? "Entrando…" : "Entrar"}
        </button>
      </form>
    </AuthShell>
  );
}
