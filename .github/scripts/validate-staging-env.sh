#!/usr/bin/env bash
set -euo pipefail

# Không in giá trị biến: đa số là secret. Mục tiêu là dừng sớm khi GitHub Environment chưa được cấp
# đủ hoặc ai đó vô tình để URL mẫu/local khiến gate staging không hề chạm môi trường thật.
required=(
  QROS_GUEST_URL
  QROS_STAFF_URL
  QROS_API_URL
  QROS_E2E_TABLE_CODE
  QROS_E2E_STORE_ID
  QROS_E2E_STAFF_EMAIL
  QROS_E2E_STAFF_PASSWORD
  QROS_E2E_MENU_ITEM_ID
  QROS_E2E_VARIANT_ID
)

missing=0
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "::error::Thiếu secret staging: ${name}"
    missing=1
  fi
done
[[ "$missing" -eq 0 ]] || exit 1

for name in QROS_GUEST_URL QROS_STAFF_URL QROS_API_URL; do
  value="${!name}"
  if [[ ! "$value" =~ ^https:// ]]; then
    echo "::error::${name} phải là URL HTTPS công khai"
    exit 1
  fi
  if [[ "$value" == *localhost* || "$value" == *127.0.0.1* || "$value" == *'.example.com'* ]]; then
    echo "::error::${name} không được trỏ tới localhost hoặc domain mẫu"
    exit 1
  fi
done
