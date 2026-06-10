export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "https://localhost:8080";

const ACCESS_KEY = "keeply.accessToken";
const REFRESH_KEY = "keeply.refreshToken";
const USER_KEY = "keeply.user";

export function setTokens(access: string, refresh?: string) {
  if (typeof window === "undefined") return;
  localStorage.setItem(ACCESS_KEY, access);
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
}

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function clearTokens() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

export function redirectToLogin() {
  if (typeof window === "undefined") return;
  if (window.location.pathname !== "/login") {
    window.location.replace("/login");
  }
}

export function clearSessionAndRedirect() {
  clearTokens();
  redirectToLogin();
}

export class ApiError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

type ApiOptions = RequestInit & { auth?: boolean };

export async function api<T = unknown>(path: string, options: ApiOptions = {}): Promise<T> {
  const { auth = true, headers, ...rest } = options;
  const finalHeaders: Record<string, string> = {
    "Content-Type": "application/json",
    ...(headers as Record<string, string> | undefined),
  };
  if (auth) {
    const tok = getAccessToken();
    if (!tok) {
      clearSessionAndRedirect();
      throw new ApiError(401, "Sessão expirada. Faça login novamente.", null);
    }
    finalHeaders["Authorization"] = `Bearer ${tok}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...rest, headers: finalHeaders });

  if ((res.status === 401 || res.status === 403) && auth) {
    clearSessionAndRedirect();
  }

  return parseResponse<T>(res);
}

export function handleAuthResponse(res: Response) {
  if (res.status === 401 || res.status === 403) {
    clearSessionAndRedirect();
    return true;
  }
  return false;
}

export function authHeaders(contentType?: string): Record<string, string> {
  const token = getAccessToken();
  if (!token) {
    clearSessionAndRedirect();
    throw new ApiError(401, "Sessão expirada. Faça login novamente.", null);
  }
  return {
    ...(contentType ? { "Content-Type": contentType } : {}),
    Authorization: `Bearer ${token}`,
  };
}

async function parseResponse<T>(res: Response): Promise<T> {
  const contentType = res.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await res.json().catch(() => null)
    : await res.text().catch(() => null);
  if (!res.ok) {
    const message =
      (body && typeof body === "object" && "message" in body && (body as { message?: string }).message) ||
      (body && typeof body === "object" && "error" in body && (body as { error?: string }).error) ||
      (typeof body === "string" && body) ||
      `HTTP ${res.status}`;
    throw new ApiError(res.status, message, body);
  }
  return body as T;
}

// Domain types
export type AuthResponse = {
  accessToken: string;
  refreshToken?: string;
  userId?: string;
  email?: string;
  deviceId?: string;
};

export type Device = {
  id: string;
  userId: string;
  name: string;
  hostname: string;
  osName: string;
  deviceInstallationId?: string;
  agentVersion?: string;
  lastSeenAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type DevicePlan = {
  planType: "DEFAULT" | "CUSTOM";
  sources: string[];
  cdpEnabled: boolean;
  validationEnabled: boolean;
  encryptionEnabled: boolean;
  scheduleCron: string | null;
  retentionMode: "KEEP_ALL" | "KEEP_DAYS";
  retentionDays: number | null;
  encryptionPasswordSet: boolean;
  updatedAt?: string;
};

export type SnapshotStatus = "RUNNING" | "IN_PROGRESS" | "PROCESSING" | "COMPLETED" | "FAILED";

export type Snapshot = {
  id: string;
  deviceId: string;
  status: SnapshotStatus;
  sourcePath: string;
  totalFiles: number;
  totalOriginalSize: number;
  totalCompressedSize: number;
  startedAt: string;
  completedAt?: string;
  errorMessage?: string;
};

export type PagedResponse<T> = {
  items: T[];
  pagination: { totalElements: number; page: number; size: number };
};

export type SnapshotFile = {
  path: string;
  size: number;
  lastModified?: string;
};

export type SnapshotNode = {
  name: string;
  path: string;
  directory: boolean;
  size?: number | null;
  lastModified?: string | null;
};

export type RestoreSession = {
  id: string;
  snapshotId: string;
  status: string;
  downloadEndpoint?: string;
  credentials?: {
    accessKey?: string;
    secretKey?: string;
    sessionToken?: string;
    bucket?: string;
  };
  expiresAt?: string;
};

export type AiChatMessage = {
  role: "user" | "assistant";
  content: string;
};

export type AiChatResponse = {
  answer: string;
  model: string;
};
