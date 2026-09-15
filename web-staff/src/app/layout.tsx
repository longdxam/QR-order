import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "QROS — Màn hình pha chế",
  description: "Hàng đợi pha chế theo thời gian thực",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="vi"><body>{children}</body></html>;
}
