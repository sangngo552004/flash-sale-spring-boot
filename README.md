# Hệ thống Flash Sale - Đặt hàng đồng thời cao

Dự án này là một nguyên mẫu hệ thống xử lý giao dịch thương mại điện tử, tập trung giải quyết bài toán "Flash Sale" - nơi có lượng lớn người dùng tranh mua một số lượng sản phẩm có hạn trong thời gian rất ngắn.

Hệ thống được thiết kế để xử lý hàng ngàn yêu cầu mỗi giây (TPS cao) và đảm bảo các yếu tố cốt lõi:
- **Không bán lố (No Overselling)**
- **Chống Spam (Rate Limiting)**
- **Chống trùng lặp đơn hàng (Idempotency)**

---

## 🚀 Tính năng nổi bật

1.  **Kiến trúc hai luồng riêng biệt:**
    *   **Sản phẩm Flash Sale:** Sử dụng **Redis + Lua Script** để quản lý tồn kho tức thời (atomic operations), bảo vệ Database khỏi hàng ngàn request đồng thời.
    *   **Sản phẩm Thông thường:** Trừ tồn kho trực tiếp qua Database Transaction (dành cho các sản phẩm lượng mua đều đặn).
2.  **Rate Limiting đa cấp:** Giới hạn số request/giây của mỗi người dùng tại tầng Redis.
3.  **Local Cache (Short-circuiting):** Sử dụng In-Memory Cache (RAM) để lập tức từ chối mọi request khi sản phẩm đã "Hết hàng", giảm tải triệt để cho Redis và Database.
4.  **Hoàn kho tự động (Auto-compensate):**
    *   Tự động cộng lại kho trên Redis nếu việc tạo đơn hàng dưới Database bị lỗi.
    *   Cron-job (Worker) tự động hủy đơn hàng quá hạn thanh toán và hoàn trả tồn kho (cả Redis và Database).

---

## 🛠 Yêu cầu môi trường

Để chạy dự án một cách dễ dàng nhất, bạn chỉ cần cài đặt:
- **[Docker](https://docs.docker.com/get-docker/)** và **[Docker Compose](https://docs.docker.com/compose/install/)**.
- Hoặc nếu bạn muốn chạy thủ công: Java 17+, Maven, MySQL 8, Redis.

---

## 🏃 Hướng dẫn khởi chạy bằng Docker (Khuyên dùng)

Dự án đã được đóng gói sẵn Dockerfile và cấu hình docker-compose.yml. Nó sẽ tự động dựng lên ứng dụng Spring Boot, cơ sở dữ liệu MySQL và Redis.

**Bước 1:** Mở terminal tại thư mục gốc của dự án (nơi có file `docker-compose.yml`).

**Bước 2:** Chạy lệnh sau để build và khởi động toàn bộ hệ thống:
```bash
docker-compose up -d --build
```

**Bước 3:** Đợi khoảng 1-2 phút để các service khởi động. Bạn có thể kiểm tra trạng thái bằng lệnh:
```bash
docker-compose ps
```

*Lưu ý: Trong lần khởi chạy đầu tiên, hệ thống sẽ tự động tạo 2 sản phẩm mẫu trong Database và đồng bộ tồn kho lên Redis (xem file `DataInitializer.java`). Hệ thống thông minh sẽ không tạo lại nếu dữ liệu đã tồn tại.*

---

## 🧪 Hướng dẫn Kiểm thử tải (Load Testing)

Dự án có sẵn script test bằng công cụ **k6** để bạn tự mình kiểm chứng hiệu năng và logic "Không bán lố".

**1. Cài đặt k6:**
- Windows: `winget install k6` hoặc tải file cài đặt từ [k6.io](https://k6.io/).
- macOS: `brew install k6`

**2. Chạy kịch bản test:**
Trong thư mục gốc dự án, có sẵn file `load-test.js`. File này giả lập 500 người dùng liên tục bắn request mua hàng trong 10 giây.

```bash
k6 run load-test.js
```

**3. Đọc kết quả:**
Sau khi chạy xong, hãy kiểm tra các chỉ số trong console của k6:
- **Mua thành công:** Số lượng này phải đúng bằng tồn kho ban đầu (mặc định là 10).
- **Hết hàng:** Số lượng request bị chặn khi hàng đã bán hết (nhờ Local Cache).
- **Thao tác quá nhanh:** Số lượng bị chặn bởi Rate Limiter.

Sau bài test, bạn có thể kiểm tra Database để thấy chính xác 10 đơn hàng trạng thái `PENDING` được tạo ra. Và sau 1 phút, Worker sẽ tự động chuyển chúng thành `CANCELLED` và hoàn lại kho vào Redis.

---

## 🛑 Dừng ứng dụng

Để tắt ứng dụng và dọn dẹp các container, chạy lệnh:
```bash
docker-compose down
```
*(Thêm cờ `-v` nếu bạn muốn xóa sạch dữ liệu trong MySQL và Redis)*
