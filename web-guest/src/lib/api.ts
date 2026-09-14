import type { components } from "@/types/api";

export type Problem = components["schemas"]["Problem"];
export type TableSession = components["schemas"]["TableSession"];
export type StartSessionRequest = components["schemas"]["StartSessionRequest"];
export type StartSessionByCodeRequest = components["schemas"]["StartSessionByCodeRequest"];
export type Menu = components["schemas"]["Menu"];
export type MenuItem = components["schemas"]["MenuItem"];

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
  const response = await fetch("/api/v1/guest/menu", { headers });

  if (response.status === 304) {
    return null;
  }
  if (!response.ok) {
    throw new ApiProblemError((await response.json()) as Problem);
  }
  return { menu: (await response.json()) as Menu, etag: response.headers.get("ETag") };
}
