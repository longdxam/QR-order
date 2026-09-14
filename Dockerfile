# Ảnh backend QROS — TM-OPS-01: image tối giản, không chạy bằng root.
#
# Hai giai đoạn: build bằng JDK đầy đủ, chạy bằng JRE — ảnh chạy không mang theo trình biên dịch
# hay Gradle. Chưa dùng distroless: chưa xác nhận được distroless đã có ảnh cho Java 25 tại thời
# điểm viết, và eclipse-temurin là ảnh chính thức, có bản dựng multi-arch ổn định — đổi sau nếu
# cần, không phải quyết định một chiều.

FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :backend:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-jammy
RUN groupadd --system qros && useradd --system --gid qros --uid 10001 qros
USER qros
WORKDIR /app
COPY --from=build --chown=qros:qros /workspace/backend/build/libs/*.jar app.jar

EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
