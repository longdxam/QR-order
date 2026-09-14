const STORAGE_KEY = "qros.deviceId";

/**
 * {@code deviceId} sinh phía client, lưu lâu dài trong trình duyệt — lớp 2 mục 5.3.2 PRD. Không
 * phải dữ liệu cá nhân (chỉ một UUID ngẫu nhiên), nên `localStorage` (không phải cookie) là đủ.
 */
export function getOrCreateDeviceId(): string {
  if (typeof window === "undefined") {
    // Server component/SSR: không có localStorage. Gọi lại ở client sau khi hydrate.
    throw new Error("getOrCreateDeviceId chỉ dùng được ở client");
  }

  const existing = window.localStorage.getItem(STORAGE_KEY);
  if (existing) {
    return existing;
  }

  const fresh = crypto.randomUUID();
  window.localStorage.setItem(STORAGE_KEY, fresh);
  return fresh;
}
