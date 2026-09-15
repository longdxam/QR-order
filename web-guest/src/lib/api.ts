import type { components } from "@/types/api";

export type Problem = components["schemas"]["Problem"];
export type TableSession = components["schemas"]["TableSession"];
export type StartSessionRequest = components["schemas"]["StartSessionRequest"];
export type StartSessionByCodeRequest = components["schemas"]["StartSessionByCodeRequest"];
export type Menu = components["schemas"]["Menu"];
export type MenuItem = components["schemas"]["MenuItem"];
export type CreateOrderRequest = components["schemas"]["CreateOrderRequest"];
export type Order = components["schemas"]["Order"];

/**
 * Lỗi RFC 7807 từ máy chủ — bất biến số 4 của repo: mọi lỗi đều có {@code code}/{@code traceId}.
 * Giữ nguyên `Problem` để UI tự quyết định thông điệp theo `code`, không hiển thị thẳng `detail`
 * (tiếng Việt của `detail` đã ổn để hiển thị, nhưng `code` mới là thứ ổn định để rẽ nhánh logic).
 */
export class ApiProblemError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title);
    this.problem = problem;
  }
}

async function postJson<TResponse>(path: string, body: unknown): Promise<TResponse> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const problem = (await response.json()) as Problem;
    throw new ApiProblemError(problem);
  }

  return (await response.json()) as TResponse;
}

/** {@code POST /api/v1/guest/sessions} — đổi mã QR lấy phiên bàn (`FR-CUS-01`). */
export function startTableSession(request: StartSessionRequest): Promise<TableSession> {
  return postJson<TableSession>("/api/v1/guest/sessions", request);
}

/** {@code POST /api/v1/guest/sessions/by-code} — mã bàn 6 ký tự thay QR (`FR-CUS-02`). */
export function startTableSessionByCode(request: StartSessionByCodeRequest): Promise<TableSession> {
  return postJson<TableSession>("/api/v1/guest/sessions/by-code", request);
}

/**
 * {@code GET /api/v1/guest/menu} (`FR-CUS-03`, `NFR-PERF-01`) — vùng khách nhận token qua header,
 * không cookie, nên phải tự gắn {@code Authorization} vào mọi lượt gọi sau khi có phiên
 * ({@code BL-M0-07}). {@code etag} truyền vào thì gửi kèm {@code If-None-Match}; {@code null} nghĩa
 * là thực đơn chưa đổi kể từ lần gọi trước ({@code 304}), giữ nguyên dữ liệu cũ ở phía gọi.
 */
export async function getMenu(
  accessToken: string,
  etag?: string,
): Promise<{ menu: Menu; etag: string | null } | null> {
  const headers: Record<string, string> = { Authorization: `Bearer ${accessToken}` };
  if (etag) {
    headers["If-None-Match"] = etag;
  }
  const response = await fetch("/api/v1/guest/menu", { headers, cache: "no-cache" });

  if (response.status === 304) {
    return null;
  }
  if (!response.ok) {
    throw new ApiProblemError((await response.json()) as Problem);
  }
  return { menu: (await response.json()) as Menu, etag: response.headers.get("ETag") };
}

/** Đặt món với khoá UUIDv4 do client giữ qua mọi lần retry (`FR-CUS-08`, `FR-CUS-09`). */
export async function placeOrder(
  accessToken: string,
  idempotencyKey: string,
  request: CreateOrderRequest,
): Promise<Order> {
  const response = await fetch("/api/v1/guest/orders", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new ApiProblemError((await response.json()) as Problem);
  }
  return (await response.json()) as Order;
}

/** Đọc lại đơn thuộc đúng phiên hiện tại; server trả 404 nếu đơn thuộc phiên khác. */
export async function getOrder(accessToken: string, orderId: string): Promise<Order> {
  const response = await fetch(`/api/v1/guest/orders/${encodeURIComponent(orderId)}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    throw new ApiProblemError((await response.json()) as Problem);
  }
  return (await response.json()) as Order;
}
