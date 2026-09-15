import type { components, operations } from "@/types/api";

export type QueueResponse = operations["getKdsQueue"]["responses"][200]["content"]["application/json"];
export type KdsTicket = components["schemas"]["KdsTicket"];
export type OrderLine = components["schemas"]["OrderLine"];
export type LineStatus = OrderLine["status"];
export type Problem = components["schemas"]["Problem"];
export type VersionProblem = components["schemas"]["VersionConflictProblem"];
export type IngredientRef = components["schemas"]["IngredientRef"];
export type IngredientSoldOutResult = components["schemas"]["IngredientSoldOutResult"];

export class ApiError extends Error {
  constructor(readonly problem: Problem | VersionProblem, readonly status: number) {
    super(problem.detail ?? problem.title);
  }
}

export async function login(email: string, password: string, totp?: string): Promise<void> {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, ...(totp ? { totp } : {}) }),
  });
  if (!response.ok) throw new ApiError(await response.json() as Problem, response.status);
}

export async function getQueue(storeId: string): Promise<QueueResponse> {
  const response = await fetch("/api/v1/staff/kds/queue", {
    credentials: "include",
    headers: { "X-Store-Id": storeId },
  });
  if (!response.ok) throw new ApiError(await response.json() as Problem, response.status);
  return response.json() as Promise<QueueResponse>;
}

function cookie(name: string): string | undefined {
  return document.cookie.split("; ").find((part) => part.startsWith(`${name}=`))?.split("=")[1];
}

export async function patchLine(storeId: string, deviceId: string, lineId: string,
  version: number, status: LineStatus, reason?: string): Promise<OrderLine> {
  const response = await fetch(`/api/v1/staff/order-lines/${encodeURIComponent(lineId)}/status`, {
    method: "PATCH",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "If-Match": String(version),
      "X-Store-Id": storeId,
      "X-Device-Id": deviceId,
      "X-XSRF-TOKEN": decodeURIComponent(cookie("XSRF-TOKEN") ?? ""),
    },
    body: JSON.stringify({ status, ...(reason ? { reason } : {}) }),
  });
  if (!response.ok) throw new ApiError(await response.json() as Problem | VersionProblem, response.status);
  return response.json() as Promise<OrderLine>;
}

/** {@code FR-BAR-05}: báo hết và nhận ngay phạm vi món/đơn đang chờ bị ảnh hưởng. */
export async function markIngredientSoldOut(storeId: string,
  ingredientId: string): Promise<IngredientSoldOutResult> {
  const response = await fetch(`/api/v1/staff/ingredients/${encodeURIComponent(ingredientId)}/sold-out`, {
    method: "POST",
    credentials: "include",
    headers: {
      "X-Store-Id": storeId,
      "X-XSRF-TOKEN": decodeURIComponent(cookie("XSRF-TOKEN") ?? ""),
    },
  });
  if (!response.ok) throw new ApiError(await response.json() as Problem, response.status);
  return response.json() as Promise<IngredientSoldOutResult>;
}
