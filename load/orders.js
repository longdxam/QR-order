import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.QROS_API_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    place_order: {
      executor: "constant-vus",
      vus: Number(__ENV.QROS_ORDER_VUS || 25),
      duration: __ENV.QROS_ORDER_DURATION || "30s",
    },
  },
  thresholds: {
    "http_req_failed{endpoint:order}": ["rate<0.01"],
    "http_req_duration{endpoint:order}": ["p(95)<400", "p(99)<800"],
  },
};

export function setup() {
  for (const name of ["QROS_TABLE_CODE", "QROS_MENU_ITEM_ID", "QROS_VARIANT_ID"]) {
    if (!__ENV[name]) throw new Error(`Thiếu ${name}`);
  }
  const response = http.post(`${baseUrl}/api/v1/guest/sessions/by-code`, JSON.stringify({
    tableCode: __ENV.QROS_TABLE_CODE,
    deviceId: __ENV.QROS_DEVICE_ID || "00000000-0000-4000-8000-000000000002",
    nickname: "k6-order",
  }), { headers: { "Content-Type": "application/json" }, tags: { endpoint: "session_setup" } });
  if (response.status !== 201) throw new Error(`Không tạo được phiên: ${response.status} ${response.body}`);
  return { token: response.json("accessToken") };
}

function uuid() {
  const hex = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx";
  return hex.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    return (character === "x" ? random : (random & 0x3) | 0x8).toString(16);
  });
}

export default function (data) {
  const response = http.post(`${baseUrl}/api/v1/guest/orders`, JSON.stringify({ lines: [{
    menuItemId: __ENV.QROS_MENU_ITEM_ID,
    variantId: __ENV.QROS_VARIANT_ID,
    optionIds: __ENV.QROS_OPTION_ID ? [__ENV.QROS_OPTION_ID] : [],
    quantity: 1,
  }] }), {
    headers: {
      Authorization: `Bearer ${data.token}`,
      "Content-Type": "application/json",
      "Idempotency-Key": uuid(),
    },
    tags: { endpoint: "order" },
  });
  check(response, { "đặt món thành công": (result) => result.status === 200 || result.status === 201 });
  sleep(1);
}
