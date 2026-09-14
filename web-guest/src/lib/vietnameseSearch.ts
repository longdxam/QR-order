/**
 * {@code FR-CUS-04}: "Tìm kiếm chấp nhận tiếng Việt không dấu" — bỏ dấu để so khớp, việc backend
 * cố tình chưa làm ở tầng CSDL ({@code V1__baseline.sql}: {@code unaccent()} không phải
 * {@code IMMUTABLE} nên không đánh index được; xử lý tạm ở tầng ứng dụng). Ở đây search diễn ra
 * hoàn toàn phía client trên thực đơn đã tải về (không có tham số tìm kiếm nào trên
 * {@code GET /menu}), nên bỏ dấu ở JS là đủ, không cần cổng riêng.
 */
export function boDauTiengViet(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase();
}

export function khopTimKiem(text: string, tuKhoa: string): boolean {
  if (!tuKhoa.trim()) return true;
  return boDauTiengViet(text).includes(boDauTiengViet(tuKhoa));
}
