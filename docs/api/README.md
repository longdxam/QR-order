# Hợp đồng API — quy trình sinh code và chống trôi

Hai file trong thư mục này là **nguồn sự thật** cho ba phía triển khai. Chúng được viết tay
trước khi có code, và code được sinh ra từ chúng — không phải ngược lại.

| File | Phạm vi |
|---|---|
| `openapi.yaml` | REST · 22 đường dẫn · 23 operation · 19 schema |
| `asyncapi.yaml` | WebSocket/STOMP · 5 kênh · 14 loại sự kiện |

## Vì sao spec-first, không phải code-first

Cách phổ biến là để `springdoc` sinh OpenAPI từ annotation của backend. Cách đó hỏng ở một
điểm cốt tử: **backend luôn đúng theo định nghĩa**. Spec chỉ phản ánh những gì backend đã làm,
nên frontend và AI service vĩnh viễn chạy theo sau, và hợp đồng mất hết tác dụng ràng buộc.

Viết spec trước thì cả ba phía cùng chịu ràng buộc bởi một tài liệu mà không phía nào sở hữu.

## Sinh code

```bash
# Spring Boot — sinh interface/model vào build/ rồi biên dịch
./gradlew :backend:verifyGeneratedOpenApi :backend:compileJava

# Next.js — sinh cùng contract cho hai ứng dụng web
npm ci --ignore-scripts
npm run contract:generate:web

# Hai job Python theo lịch — sinh model Pydantic
python -m pip install --requirement ml/requirements-codegen.txt
datamodel-codegen \
  --input docs/api/openapi.yaml \
  --input-file-type openapi \
  --output ml/models/generated.py \
  --output-model-type pydantic_v2.BaseModel \
  --target-python-version 3.13 \
  --use-standard-collections \
  --use-union-operator \
  --use-schema-description \
  --field-constraints \
  --disable-timestamp \
  --formatters black isort
```

Phiên bản generator được ghim tại `backend/gradle/libs.versions.toml`, `package-lock.json` và
`ml/requirements-codegen.txt`; không dùng phiên bản trôi nổi trong CI.

Điểm mấu chốt nằm ở `interfaceOnly=true`. Controller của Spring Boot **implements** interface
được sinh ra, nên một thay đổi trong spec mà chưa cập nhật controller sẽ thành **lỗi biên dịch**,
chứ không phải một khác biệt âm thầm phát hiện ở môi trường tích hợp.

## Chống trôi trong CI

Ba cổng chặn, chạy trên mọi pull request:

1. **Kiểm tra tính hợp lệ.** `spectral lint` cho cả hai file, kèm luật riêng của dự án:
   mọi operation phải có `operationId` và `x-requirements`.
2. **Kiểm tra code sinh ra đã cập nhật.** Chạy lại bộ lệnh sinh code rồi `git diff --exit-code`.
   Có khác biệt nghĩa là ai đó sửa spec mà chưa sinh lại, hoặc sửa tay vào file sinh ra.
3. **Kiểm thử hợp đồng.** Chạy bộ test xác thực đáp ứng thật của backend theo schema
   (Schemathesis hoặc Pact). Đây là cổng bắt được trường hợp code biên dịch được nhưng
   trả về dữ liệu không đúng lược đồ.

Hai cổng đầu chạy từ `contract-check.yml`. Cổng thứ ba chưa thể chạy khi chưa có endpoint hiện thực;
nó được bổ sung cùng lát API đầu tiên ở M1, không được đánh dấu là đã kiểm chứng trước thời điểm đó.

Chạy lint cục bộ:

```bash
npm run contract:lint
```

## Bốn quy tắc không được nới lỏng

1. **`additionalProperties: false` trên `CreateOrderRequest` và `CreateOrderLine`.**
   Đây là cách `ADR-06` được cưỡng chế ngay tại tầng xác thực lược đồ: client gửi kèm
   trường giá thì bị chặn trước khi chạm vào nghiệp vụ. Có test riêng cho điều này.
2. **Tiền là `integer` đơn vị đồng.** Không có `number`, không có `float`, không có chuỗi
   định dạng tiền tệ ở bất kỳ đâu trong hai file.
3. **Ba tiền tố, ba chuỗi filter.** `/guest/**`, `/staff/**`, `/admin/**` không bao giờ
   dùng chung cấu hình bảo mật.
4. **Mọi lỗi là `application/problem+json`** kèm `code` và `traceId`.

## Khi cần đổi hợp đồng

Thay đổi tương thích ngược (thêm trường tuỳ chọn, thêm endpoint) thì sửa trực tiếp và
tăng `info.version` theo semver.

Thay đổi phá vỡ tương thích thì tạo `/api/v2/`, giữ `v1` thêm 6 tháng, và ghi vào nhật ký
sửa đổi của PRD. Không bao giờ đổi ý nghĩa của một trường đang tồn tại — thêm trường mới
và đánh dấu trường cũ là `deprecated: true`.

## Việc còn thiếu

Hợp đồng này phủ M1 và M2 (đặt món, KDS, thanh toán) cùng phần trợ lý ở M4. Chưa có:

- Nhóm `/admin/**`: quản trị thực đơn, kho, nhân viên, báo cáo — thuộc M3
- Endpoint xuất báo cáo và tác vụ chạy nền
- Chia hoá đơn (`FR-CUS-15`, ưu tiên `C`)

Viết bổ sung khi tới M3, theo đúng quy trình trên.
