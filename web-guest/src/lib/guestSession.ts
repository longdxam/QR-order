import type { components } from "@/types/api";

export type TableSession = components["schemas"]["TableSession"];

const STORAGE_KEY = "qros.guestSession";

/**
 * Phiên khách đang giữ ở client — vùng khách nhận token qua header {@code Authorization}, không
 * qua cookie ({@code GuestSecurityConfig}, {@code BL-M0-07}), nên trình duyệt không tự đính token;
 * ta phải tự lưu và tự gắn lại ở mọi lượt gọi API sau đó.
 */
export function saveGuestSession(session: TableSession): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function loadGuestSession(): TableSession | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as TableSession;
  } catch {
    return null;
  }
}

export function clearGuestSession(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(STORAGE_KEY);
}

/** {@code true} nếu phiên đã lưu còn hiệu lực theo {@code expiresAt} — không tính là hết hạn thật cho tới khi máy chủ nói vậy, đây chỉ là gợi ý UX để tránh gọi API biết trước sẽ hỏng. */
export function isLikelyExpired(session: TableSession): boolean {
  return new Date(session.expiresAt).getTime() <= Date.now();
}
