# Hướng Dẫn Sử Dụng Swagger / OpenAPI cho DenHub

Tài liệu này hướng dẫn cách sử dụng Swagger UI để xem tài liệu API, gửi request và kiểm tra response của backend DenHub mà không cần dùng Postman hay đọc code.

---

## A. Swagger là gì?
Swagger (OpenAPI) là công cụ tự động tạo giao diện tài liệu cho các REST API dựa trên mã nguồn backend. Nó cho phép bạn:
- Xem danh sách toàn bộ các API hiện có.
- Xem cấu trúc Request và Response (DTO).
- Gửi HTTP request trực tiếp từ trình duyệt và xem kết quả trả về.

## B. Cách khởi động backend
Đảm bảo bạn đã cài đặt Java 21. Mở terminal, trỏ vào thư mục `server/` và chạy lệnh sau để khởi động Spring Boot:

**Trên Windows (PowerShell/CMD):**
```bash
.\gradlew.bat bootRun
```

**Trên Linux / macOS:**
```bash
./gradlew bootRun
```

Server sẽ khởi động và lắng nghe ở cổng mặc định là **8080** (hoặc theo cấu hình `SERVER_PORT` trong file `server/.env`).

## C. Cách mở Swagger UI
Sau khi backend khởi động thành công, hãy mở trình duyệt và truy cập vào:
- **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

*Lưu ý: Thay `8080` bằng port thực tế nếu bạn đã đổi cấu hình.*

## D. Cách đọc một API
Trong giao diện Swagger UI, các API được nhóm theo **Tag** (ví dụ: `Account`, `Server`, `File`).
Khi click mở một API (ví dụ: `POST /api/v1/servers`), bạn sẽ thấy:
- **Parameters:** Các tham số truyền trên URL (Path variable) hoặc Query string.
- **Request body:** Định dạng JSON và các trường bắt buộc để gửi lên server. Bạn có thể xem tab `Schema` để biết kiểu dữ liệu chi tiết.
- **Responses:** Các mã trạng thái HTTP (200, 201, 400, 401...) và cấu trúc JSON trả về tương ứng.
- Biểu tượng **Ổ khóa (Lock)**: API này yêu cầu phải đăng nhập (có token) mới gọi được.

## E. Cách sử dụng JWT để test API yêu cầu đăng nhập
Một số API (có biểu tượng ổ khóa) yêu cầu xác thực bằng JWT. Làm theo các bước sau:

1. Kéo xuống nhóm `Account`, tìm API `POST /api/login`.
2. Bấm **Try it out**, nhập thông tin đăng nhập (email và password hợp lệ đã có trong DB).
3. Bấm **Execute**. Kéo xuống phần Response body, copy đoạn mã của `accessToken` (không copy dấu ngoặc kép).
4. Kéo lên đầu trang Swagger UI, bấm nút **Authorize**.
5. Dán đoạn mã token vừa copy vào ô **Value** và bấm **Authorize**.
6. Bây giờ bạn có thể gọi các API yêu cầu đăng nhập (như tạo Server, tải ảnh). Hệ thống sẽ tự động gắn header `Authorization: Bearer <token>` vào request.

*Lưu ý: Nếu token hết hạn (mặc định 24h), API sẽ trả về lỗi `401 Unauthorized`. Bạn chỉ cần đăng nhập lại để lấy token mới.*

## F. Quy trình test API
1. Chọn API cần test, bấm nút **Try it out**.
2. Nhập các giá trị tham số và sửa đổi Request body JSON (nếu có). 
   *(Nên dùng dữ liệu test trên môi trường local, không dùng dữ liệu production).*
3. Bấm **Execute** để gửi request tới backend.
4. Cuộn xuống xem kết quả tại mục **Server response**, kiểm tra mã HTTP Code (ví dụ: 200, 400) và Response body.

## G. Quy ước cập nhật Swagger khi phát triển
Mọi thành viên làm việc với Backend khi tạo/sửa API đều **BẮT BUỘC** cập nhật tài liệu:
- Khi thêm endpoint mới: Thêm `@Operation` mô tả mục đích.
- Khi API yêu cầu đăng nhập: Đảm bảo có annotation `@SecurityRequirement(name = "bearerAuth")`.
- Sắp xếp API vào nhóm hợp lý thông qua `@Tag`.
- Không tự ý thêm cấu hình làm bypass toàn bộ Security.

## H. Lỗi thường gặp
- **Không truy cập được Swagger UI:** Kiểm tra lại backend đã thực sự chạy chưa (báo `Started DenHubApplication in ... seconds` trên terminal) và đúng port chưa.
- **API trả về 401 hoặc 403:** Bạn chưa nhập Token ở nút Authorize, hoặc token đã hết hạn, hoặc user của bạn không đủ quyền.
- **Lỗi 400 Bad Request:** Bạn gửi sai định dạng JSON hoặc thiếu trường bắt buộc (ví dụ thiếu name khi tạo server). Kiểm tra kỹ lại Request body.
- **API không hiển thị trong Swagger:** Có thể do controller chưa được gắn `@RestController` hoặc thiếu quyền truy cập class.

## I. Checklist cho thành viên
Trước khi test API hoặc làm Frontend, hãy tự xác nhận:
- [ ] Tôi đã bật Backend thành công.
- [ ] Tôi đã vào được Swagger UI.
- [ ] Tôi đã đăng nhập và gắn được Token vào Authorize.
- [ ] Tôi hiểu cách dùng nút "Try it out" để kiểm thử một endpoint.
