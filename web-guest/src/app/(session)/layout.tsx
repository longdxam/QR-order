"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { isLikelyExpired, loadGuestSession } from "@/lib/guestSession";

function khongLangNghe() {
  // localStorage không tự bắn sự kiện thay đổi trong cùng tab — phiên chỉ được set() một lần lúc
  // vào bàn (trang /t/[qrToken] hoặc /ma-ban) trước khi điều hướng tới đây, nên không cần subscribe.
  return () => {};
}

function docPhienTrenClient(): boolean {
  const daLuu = loadGuestSession();
  return daLuu !== null && !isLikelyExpired(daLuu);
}

function docPhienLucSsr(): boolean {
  // Không có localStorage phía server — luôn trả false, khớp lần render client đầu tiên trước khi
  // hydrate xong để tránh lệch cây DOM (hydration mismatch) giữa server và client.
  return false;
}

/**
 * Mọi trang dưới {@code (session)} cần một phiên bàn còn hiệu lực — kiểm ở client vì token nằm
 * trong {@code localStorage}, không phải cookie nên không SSR kiểm được. Đây chỉ là gợi ý UX
 * (chuyển hướng sớm); máy chủ vẫn là nơi thật sự kiểm tra token trên mọi request.
 *
 * <p>{@code useSyncExternalStore} thay vì {@code useState}+{@code useEffect}: đọc một nguồn dữ
 * liệu bên ngoài React ({@code localStorage}) đúng cách, snapshot phía server luôn nhất quán với
 * lần render client đầu tiên — không hydration mismatch, không cảnh báo "setState trong effect".
 */
export default function SessionLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const coPhienHopLe = useSyncExternalStore(khongLangNghe, docPhienTrenClient, docPhienLucSsr);

  useEffect(() => {
    if (!coPhienHopLe) {
      router.replace("/");
    }
  }, [coPhienHopLe, router]);

  if (!coPhienHopLe) {
    return null;
  }

  return <>{children}</>;
}
