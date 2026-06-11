"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthShell, AuthInput } from "@/components/AuthShell";
import { api, setTokens, type AuthResponse } from "@/lib/api";
import { resolveAuthError } from "@/lib/authFeedback";

export default function RegisterPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [registrationCode, setRegistrationCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await api<AuthResponse>("/api/auth/register", {
        method: "POST",
        auth: false,
        body: JSON.stringify({ name, email, password, registrationCode }),
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
      setError(resolveAuthError(err).message);
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
          Ja tem conta?{" "}
          <Link href="/login" className="font-semibold text-[#A78BFA] transition hover:text-white">
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
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Minimo 8 caracteres"
        />
        <AuthInput
          label="Codigo de registro"
          name="registrationCode"
          type="password"
          autoComplete="off"
          required
          value={registrationCode}
          onChange={(e) => setRegistrationCode(e.target.value)}
          placeholder="Codigo privado"
        />
        {error && (
          <p className="rounded-lg border border-[#EF4444]/20 bg-[#EF4444]/10 px-3 py-2 text-sm text-[#EF4444]">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={loading}
          className="mt-1 w-full cursor-pointer rounded-lg bg-[#7B61FF] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#6046F0] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? "Criando..." : "Criar conta"}
        </button>
      </form>
    </AuthShell>
  );
}
