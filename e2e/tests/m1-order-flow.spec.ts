import { expect, test } from "@playwright/test";

const guestUrl = process.env.QROS_GUEST_URL ?? "http://localhost:3000";
const staffUrl = process.env.QROS_STAFF_URL ?? "http://localhost:3001";

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Thiếu biến môi trường ${name} cho fixture E2E staging`);
  return value;
}

test("BL-M1-06: một đơn đi từ mã bàn tới SERVED", async ({ browser }) => {
  const tableCode = required("QROS_E2E_TABLE_CODE");
  const email = required("QROS_E2E_STAFF_EMAIL");
  const password = required("QROS_E2E_STAFF_PASSWORD");
  const storeId = required("QROS_E2E_STORE_ID");

  const staffContext = await browser.newContext();
  const staff = await staffContext.newPage();
  await staff.goto(`${staffUrl}/login`);
  await staff.getByLabel("Email").fill(email);
  await staff.getByLabel("Mật khẩu").fill(password);
  await staff.getByLabel("ID chi nhánh").fill(storeId);
  const totp = process.env.QROS_E2E_STAFF_TOTP;
  if (totp) await staff.getByLabel(/Mã xác thực/).fill(totp);
  await staff.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(staff).toHaveURL(/\/kds$/);

  const guestContext = await browser.newContext();
  const guest = await guestContext.newPage();
  await guest.goto(`${guestUrl}/ma-ban`);
  await guest.getByPlaceholder("VD: A1B2C3").fill(tableCode);
  await guest.getByRole("button", { name: "Vào bàn" }).click();
  await guest.getByRole("link", { name: /Xem thực đơn/ }).click();

  await guest.getByRole("button", { name: /^Chọn / }).first().click();
  const dialog = guest.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const fieldsets = dialog.locator("fieldset");
  for (let index = 1; index < await fieldsets.count(); index += 1) {
    const fieldset = fieldsets.nth(index);
    if ((await fieldset.locator("legend").textContent())?.includes("chọn ít nhất")) {
      await fieldset.locator("input:enabled").first().check();
    }
  }
  await dialog.getByRole("button", { name: "Thêm vào giỏ" }).click();
  await guest.getByRole("link", { name: /Giỏ hàng/ }).click();
  await guest.getByRole("button", { name: "Đặt món" }).click();
  await expect(guest.getByText("Đặt món thành công")).toBeVisible();

  await staff.getByRole("button", { name: "Tải lại" }).click();
  for (const action of ["Xác nhận", "Bắt đầu", "Sẵn sàng", "Đã phục vụ"]) {
    const button = staff.getByRole("button", { name: action }).first();
    await expect(button).toBeVisible();
    await button.click();
  }

  await expect(guest.getByText("Đã phục vụ", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
  await guestContext.close();
  await staffContext.close();
});
