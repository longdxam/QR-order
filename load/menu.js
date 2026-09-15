import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.QROS_API_URL || "http://host.docker.internal:8080";
const vus = Number(__ENV.QROS_MENU_VUS || 500);
const duration = __ENV.QROS_MENU_DURATION || "30s";

export const options = {
  scenarios: {
    menu_revalidation: { executor: "constant-vus", vus, duration },
  },
  thresholds: {
    "http_req_failed{endpoint:menu}": ["rate<0.01"],
    "http_req_duration{endpoint:menu}": ["p(95)<120", "p(99)<250"],
  },
};

export function setup() {
  const tableCode = __ENV.QROS_TABLE_CODE;
  if (!tableCode) throw new Error("Thiếu QROS_TABLE_CODE");
  const response = http.post(`${baseUrl}/api/v1/guest/sessions/by-code`, JSON.stringify({
    tableCode,
    deviceId: __ENV.QROS_DEVICE_ID || "00000000-0000-4000-8000-000000000001",
    nickname: "k6-menu",
  }), { headers: { "Content-Type": "application/json" }, tags: { endpoint: "session_setup" } });
  check(response, { "tạo phiên tải thành công": (result) => result.status === 201 });
  if (response.status !== 201) throw new Error(`Không tạo được phiên: ${response.status} ${response.body}`);
  return { token: response.json("accessToken") };
}

let etag;
export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  if (etag) headers["If-None-Match"] = etag;
  const response = http.get(`${baseUrl}/api/v1/guest/menu`, {
    headers,
    tags: { endpoint: "menu" },
  });
  check(response, { "menu trả 200/304": (result) => result.status === 200 || result.status === 304 });
  if (response.headers.ETag) etag = response.headers.ETag;
  sleep(1);
}
