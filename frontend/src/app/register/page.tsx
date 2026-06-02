"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthShell, AuthInput } from "@/components/AuthShell";
import { api, ApiError, setTokens, type AuthResponse } from "@/lib/api";

export default function RegisterPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("As senhas não coincidem.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await api<AuthResponse>("/api/auth/register", {
        method: "POST",
        auth: false,
        body: JSON.stringify({ name, email, password }),
      });
      if (res.accessToken) {
        setTokens(res.accessToken, res.refreshToken);
        try {
          localStorage.setItem(
            "keeply.user",
            JSON.stringify({ userId: res.userId, email: res.email, name })
          );
        } catch {}
        router.push("/dashboard");
      } else {
        router.push("/login");
      }
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError("Não foi possível criar a conta. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      title="Criar conta"
      subtitle=""
      footer={
        <>
          Já tem conta?{" "}
          <Link href="/login" className="font-medium text-keeply-700 hover:text-keeply-800">
            Entrar
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <AuthInput
          label="Nome"
          name="name"
          type="text"
          autoComplete="name"
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Seu nome"
        />
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
          autoComplete="new-password"
          minLength={8}
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Mínimo 8 caracteres"
        />
        <AuthInput
          label="Confirmar senha"
          name="confirm"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          placeholder="Repita a senha"
        />
        {error && (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
        )}
        <button
          type="submit"
          disabled={loading}
          className="kp-btn-primary w-full rounded-xl px-4 py-2.5 text-sm font-semibold"
        >
          {loading ? "Criando conta…" : "Criar conta"}
        </button>
      </form>
    </AuthShell>
  );
}
