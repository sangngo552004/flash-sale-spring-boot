# Báo cáo Tổng kết Giai đoạn 1: Xây dựng và Đánh giá Hệ thống Flash Sale

**Ngày:** 25/05/2024

**Tác giả:** [Tên của bạn]
**Mục tiêu:** Xây dựng và kiểm thử hiệu năng cho hệ thống đặt hàng, bao gồm hai luồng chính: Flash Sale (ưu tiên tốc độ và xử lý đồng thời cao) và Mua hàng thông thường.

## 1. Tổng quan Kiến trúc và Luồng xử lý

Hệ thống được thiết kế để xử lý các yêu cầu đặt hàng với hai kịch bản khác nhau, sử dụng các công nghệ và pattern phù hợp cho từng mục tiêu.

### 1.1. Luồng Flash Sale (High-Concurrency)

Luồng này được tối ưu để xử lý hàng chục ngàn request đồng thời, với mục tiêu cốt lõi là **tốc độ** và **ngăn chặn bán lố (overselling)**.

**Các bước xử lý:**
1.  **Rate Limiting (Redis):** Chặn các request từ cùng một user nếu tần suất quá cao (>2 req/giây).
2.  **Local Cache Short-Circuit (In-Memory):** Khi sản phẩm đã hết hàng, hệ thống sử dụng một cache trên RAM (`localSoldOutCache`) để từ chối ngay lập tức các request mới mà không cần truy vấn xuống Redis hay Database, giúp giảm tải tối đa.
3.  **Quản lý Tồn kho (Redis Lua Script):** Tồn kho được quản lý hoàn toàn trên Redis. Một Lua script được sử dụng để đảm bảo thao tác trừ kho là nguyên tử (atomic), ngăn chặn tuyệt đối tình trạng race condition.
4.  **Tạo Đơn hàng (Database):** Nếu trừ kho Redis thành công, hệ thống sẽ tạo một bản ghi `Order` (trạng thái `PENDING`) và một `OutboxEvent` trong Database. Toàn bộ quá trình này được bọc trong một transaction.
5.  **Cơ chế Đền bù (Compensation):** Nếu việc tạo đơn hàng trong DB thất bại (ví dụ: do request trùng lặp), hệ thống sẽ tự động cộng trả lại số lượng tồn kho đã trừ trên Redis.
6.  **Xử lý Hậu kỳ (Outbox Pattern & CDC):** Các `OutboxEvent` được kỳ vọng sẽ được một hệ thống Change Data Capture (CDC) như Debezium đọc và đẩy lên message queue (Kafka/RabbitMQ) để các service khác xử lý (thanh toán, giao vận,...).

### 1.2. Luồng Mua hàng Thông thường (Standard)

Luồng này được thiết kế cho các sản phẩm thông thường, ưu tiên tính nhất quán và đơn giản, sử dụng hoàn toàn Database làm nguồn dữ liệu chính.

**Các bước xử lý:**
1.  **Trừ kho (Database):** Hệ thống thực thi một câu lệnh `UPDATE` trực tiếp trên bảng `Product` để trừ kho. Câu lệnh này sử dụng điều kiện `WHERE stock >= amount` để đảm bảo không bán lố.
2.  **Tạo Đơn hàng (Database):** Nếu trừ kho thành công, hệ thống sẽ tạo `Order` và `OutboxEvent` tương tự như luồng Flash Sale.
3.  **Transaction:** Toàn bộ quá trình trên được bọc trong một transaction của Database để đảm bảo tính toàn vẹn dữ liệu.

### 1.3. Cơ chế Hoàn kho Tự động

Một worker chạy nền (`OrderTimeoutWorker`) được lên lịch mỗi 60 giây để:
1.  Tìm các đơn hàng `PENDING` đã quá hạn thanh toán.
2.  Chuyển trạng thái của chúng thành `CANCELLED`.
3.  **Hoàn trả tồn kho:**
    *   Nếu là sản phẩm Flash Sale, cộng lại số lượng vào **Redis**.
    *   Nếu là sản phẩm thường, cộng lại số lượng vào **Database**

## 2. Đánh giá Kết quả Kiểm thử Tải (Load Testing)

Sử dụng công cụ **k6** với kịch bản giả lập **500 người dùng ảo (VUs)** liên tục đặt hàng trong **10 giây**.

### 2.1. Luồng Flash Sale

**Tình trạng:** **Rất Tốt & Hoạt động Đúng Thiết kế**

#### Phân tích số liệu chi tiết:
*   **`✗ Mua thành công: ✓ 10 / ✗ 23564`**: **Kết quả hoàn hảo.** Hệ thống đã bán ra chính xác **10 sản phẩm**, đúng bằng số lượng tồn kho ban đầu, và từ chối hơn 23,000 lượt mua khác. Điều này chứng tỏ cơ chế chống bán lố (overselling) hoạt động tuyệt đối.
*   **`checks_total: 117,483 (11,678/s)`**: **Hiệu năng cực cao.** Hệ thống xử lý được gần **12,000 thao tác/giây**. Đây là minh chứng cho thấy kiến trúc sử dụng Redis và Local Cache có khả năng chịu tải rất tốt.
*   **`✗ Hết hàng: ✓ 13,008` & `✗ Thao tác quá nhanh: ✓ 10,427`**: **Cơ chế bảo vệ hiệu quả.** Tổng cộng có hơn **23,400 request** đã bị chặn lại bởi các lớp phòng thủ (Rate Limiting và Cache), giúp bảo vệ tầng Database khỏi bị quá tải.
*   **`Status is 200: 99%`**: **Độ ổn định cao.** Dù chịu tải rất lớn, 99% request vẫn nhận được phản hồi thành công từ server, cho thấy ứng dụng không bị sập hay treo.

**Đánh giá:** Luồng Flash Sale đã đáp ứng xuất sắc các yêu cầu về hiệu năng, độ ổn định và tính chính xác trong môi trường tải cao.

### 2.2. Luồng Mua hàng Thông thường

**Tình trạng:** **Hoạt động Đúng về Logic, nhưng Gặp Vấn đề Nghiêm trọng về Hiệu năng**

#### Phân tích số liệu chi tiết:
*   **`✗ Mua thành công: ✓ 10`**: **Logic nghiệp vụ đúng.** Tương tự luồng Flash Sale, hệ thống vẫn đảm bảo chỉ bán đúng 10 sản phẩm theo số lượng trong kho DB.
*   **`http_req_duration: avg=1.77s, p(95)=2.49s`**: **Thời gian phản hồi RẤT CAO.** Thời gian phản hồi trung bình là 1.77 giây, và có tới 5% người dùng phải chờ gần **2.5 giây** hoặc hơn. Đây là trải nghiệm người dùng không thể chấp nhận được.
*   **`http_req_failed: 14.51% (430 requests)`**: **Độ ổn định kém.** Gần **15% request** bị lỗi hoàn toàn (timeout hoặc lỗi 5xx). Điều này cho thấy hệ thống không thể xử lý ổn định dưới tải.
*   **`vus: 8 min=8 / max=500`**: **Đây là bằng chứng rõ ràng nhất của nút thắt cổ chai.** Mặc dù có 500 người dùng ảo, hệ thống chỉ có thể xử lý đồng thời **8 người dùng**. 492 người dùng còn lại phải xếp hàng chờ đợi, gây ra tình trạng tắc nghẽn và tăng vọt thời gian phản hồi.
*   **`http_reqs: 2,962 (248/s)`**: **Thông lượng (throughput) thấp.** Hệ thống chỉ xử lý được khoảng **248 request/giây**, thấp hơn rất nhiều so với luồng Flash Sale.

**Đánh giá:** Mặc dù logic nghiệp vụ đúng, luồng mua hàng thông thường đang bị **nghẽn cổ chai nghiêm trọng ở tầng Database**. Việc nhiều request cùng lúc cố gắng `UPDATE` một dòng dữ liệu đã gây ra tình trạng tranh chấp khóa (lock contention), dẫn đến hiệu năng sụt giảm thê thảm.

## 3. Các Điểm cần Cải thiện

Dựa trên kết quả phân tích, các công việc cần ưu tiên trong giai đoạn tiếp theo bao gồm:

1.  **[Ưu tiên Cao] Tối ưu hóa Hiệu năng cho Luồng Mua hàng Thông thường:**
    *   **Phân tích Log & DB:** Kiểm tra log ứng dụng và các công cụ giám sát DB để tìm các lỗi cụ thể như `DeadlockFoundException`, `LockAcquisitionException` và các truy vấn chậm trong quá trình chạy test.
    *   **Xem xét lại Chiến lược Khóa:** Đánh giá lại việc sử dụng `UPDATE` trực tiếp. Cần cân nhắc các phương án thay thế nếu sản phẩm thường cũng có khả năng được mua nhiều cùng lúc, ví dụ:
        *   Áp dụng một phiên bản "nhẹ" của cơ chế Redis cho các sản phẩm "hot".
        *   Sử dụng Optimistic Locking (với cột `version`) một cách triệt để hơn để xử lý xung đột ở tầng ứng dụng thay vì để DB khóa.
    *   **Tối ưu hóa Transaction:** Giảm thiểu thời gian transaction được giữ bằng cách đưa các tác vụ không cần thiết ra ngoài.

2.  **[Ưu tiên Trung bình] Hoàn thiện Kịch bản Test:**
    *   Xây dựng kịch bản test riêng cho việc kiểm tra cơ chế chống trùng lặp (`Idempotency`), bằng cách cố định `requestId` cho một nhóm request.
    *   Xây dựng bài test để xác minh `OrderTimeoutWorker` hoạt động đúng và hoàn kho chính xác sau một khoảng thời gian nhất định.
