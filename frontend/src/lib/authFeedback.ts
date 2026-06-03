"use client";

import { ApiError } from "@/lib/api";

export type AuthErrorState = {
  message: string;
  cooldownUntil?: number;
};

export function authCountdownLabel(cooldownUntil: number | null) {
  if (!cooldownUntil) return null;
  const remainingMs = cooldownUntil - Date.now();
  if (remainingMs <= 0) return null;
  const totalSeconds = Math.ceil(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function resolveAuthError(err: unknown): AuthErrorState {
  if (err instanceof ApiError) {
    const normalizedMessage = (err.message || "").toLowerCase();
    if (err.status === 429) {
      const minutes = parseRetryMinutes(err.message) ?? 15;
      return {
        message: err.message || "Muitas tentativas. Aguarde antes de tentar novamente.",
        cooldownUntil: Date.now() + minutes * 60_000,
      };
    }
    if (
      err.status === 401 ||
      err.status === 403 ||
      (err.status === 400 && normalizedMessage.includes("credenciais invalidas")) ||
      (err.status === 400 && normalizedMessage.includes("credenciais inválidas"))
    ) {
      return { message: "E-mail ou senha incorretos." };
    }
    if (err.status >= 500) {
      return { message: "Erro no backend. Tente novamente em instantes." };
    }
    return { message: err.message || `Erro ${err.status}` };
  }

  if (err instanceof TypeError) {
    return {
      message: "Não foi possível conectar ao servidor. Verifique se o backend está rodando e se foi reiniciado após a última alteração.",
    };
  }

  return { message: "Erro inesperado. Tente novamente." };
}

function parseRetryMinutes(message: string) {
  const match = /(\d+)\s*minuto/i.exec(message);
  if (!match) return null;
  const minutes = Number(match[1]);
  return Number.isFinite(minutes) && minutes > 0 ? minutes : null;
}
