"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, login } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    try {
      await login(String(data.get("email")), String(data.get("password")), String(data.get("totp") ?? ""));
      localStorage.setItem("qros-store-id", String(data.get("storeId")));
      router.replace("/kds");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không thể kết nối máy chủ.");
    } finally {
      setBusy(false);
    }
  }

  return <main className="login-shell">
    <form className="login-card" onSubmit={submit}>
      <p className="eyebrow">QROS · STAFF</p>
      <h1>Vào màn hình pha chế</h1>
      <label>Email<input name="email" type="email" required autoComplete="username" /></label>
      <label>Mật khẩu<input name="password" type="password" minLength={12} required autoComplete="current-password" /></label>
      <label>Mã xác thực <span>(nếu đã bật)</span><input name="totp" inputMode="numeric" pattern="[0-9]{6}" /></label>
      <label>ID chi nhánh<input name="storeId" type="text" required pattern="[0-9a-fA-F-]{36}" defaultValue="" /></label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit" className="primary" disabled={busy}>{busy ? "Đang đăng nhập…" : "Đăng nhập"}</button>
    </form>
  </main>;
}
