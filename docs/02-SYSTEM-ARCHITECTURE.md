# 02 - SYSTEM ARCHITECTURE: DENHUB

> **Tài liệu kiến trúc hệ thống & Quy chuẩn giao tiếp**\
> **Source of Truth:** Tài liệu này quản lý architecture, API, DTO, Security và WebSocket; nghiệp vụ ở [`01-PROJECT-OVERVIEW.md`](./01-PROJECT-OVERVIEW.md), dữ liệu ở [`03-DATABASE-DESIGN.md`](./03-DATABASE-DESIGN.md).\
> **Cập nhật 2026-10-11:** Duy chốt giữ wrapper response thành công hiện có; xem mục 4.1, 4.4 và 7.1. Contract được chốt không đồng nghĩa source code đã triển khai xong; xem mục 7.

---

## 1. Technology Stack (`CONFIRMED`)

| Tầng / Phân hệ | Công nghệ lựa chọn | Mục đích sử dụng | Trạng thái |
| :--- | :--- | :--- | :---: |
| **Frontend Framework** | **React + Vite** | SPA hiệu năng cao | `CONFIRMED` |
| **Routing** | **React Router DOM** | Điều hướng và bảo vệ Protected Routes | `CONFIRMED` |
| **UI Component Library** | **Ant Design (antd)** | Layout, Navigation, Chat Form, Modals | `CONFIRMED` |
| **HTTP Client** | **Axios** | REST client có Bearer token interceptor | `CONFIRMED` |
| **Real-time Client** | **STOMP / WebSocket (@stomp/stompjs)** | Kết nối WebSocket và xử lý STOMP protocol | `CONFIRMED` |
| **Backend Framework** | **Java 21 + Spring Boot 3** | REST API, Business Services, WebSocket Broker | `CONFIRMED` |
| **Security & Auth** | **Spring Security 6 + Nimbus JWT** | Xác thực JWT Access Token, bảo vệ API & STOMP | `CONFIRMED` |
| **ORM / Data Access** | **Spring Data JPA (Hibernate)** | Tương tác dữ liệu quan hệ Microsoft SQL Server | `CONFIRMED` |
| **Database** | **Microsoft SQL Server** | Cơ sở dữ liệu duy nhất toàn bộ hệ thống | `CONFIRMED` |
| **File Storage** | **Cloudinary / Local File Storage** | Lưu trữ ảnh và file đính kèm (DB lưu metadata) | `CONFIRMED` |
| **Backend Testing** | **JUnit 5 + Mockito + MockMvc** | Kiểm thử đơn vị và API integration | `CONFIRMED` |

---

## 2. Kiến trúc tổng thể hệ thống

```mermaid
graph TB
    subgraph ClientLayer ["Client Layer (Frontend SPA)"]
        UI["React 18 + Ant Design"]
        AxiosClient["Axios HTTP Client (REST)"]
        StompClient["@stomp/stompjs (WebSocket)"]
    end

    subgraph BackendLayer ["Backend Layer (Spring Boot 3)"]
        SecurityFilter["Spring Security Filter (JWT Validation)"]
        RestControllers["REST Controllers (Auth, Server, Channel, Message, DM)"]
        WsEndpoints["STOMP Broker & Channel Interceptors"]
        Services["Domain Business Services"]
        JpaRepo["Spring Data JPA Repositories"]
    end

    subgraph DatabaseLayer ["Database Layer"]
        SqlServer[("Microsoft SQL Server\n(Relational Schema)")]
    end

    UI --> AxiosClient
    UI --> StompClient

    AxiosClient -->|HTTP/HTTPS REST| SecurityFilter
    SecurityFilter --> RestControllers
    RestControllers --> Services

    StompClient -->|WebSocket / STOMP| WsEndpoints
    WsEndpoints --> Services

    Services --> JpaRepo
    JpaRepo -->|JDBC / Hibernate| SqlServer
```

---

## 3. Phân tách ranh giới REST vs WebSocket

| Giao thức | Phạm vi áp dụng | Lý do thiết kế |
| :--- | :--- | :--- |
| **REST API (HTTP/HTTPS)** | - Authentication (Đăng ký, Đăng nhập, Profile)<br>- Quản lý Server, Channel, Member, Invite<br>- Gửi tin nhắn Channel (Task 1.07)<br>- Tải lịch sử tin nhắn (Cursor Pagination)<br>- Quản lý Direct Conversation | Xử lý các tác vụ truy vấn, giao dịch ACID, phân trang và tải dữ liệu khởi tạo đáng tin cậy. |
| **WebSocket (STOMP)** | - Phát sóng tin nhắn kênh mới (`MESSAGE_CREATED`)<br>- Cập nhật / Xóa tin nhắn (`UPDATED`, `DELETED`)<br>- Sự kiện thả cảm xúc (`REACTION_ADDED`, `REMOVED`)<br>- Phân phối tin nhắn riêng tư 1-1 qua private queue | Truyền tải sự kiện thời gian thực độ trễ thấp, không gây tải polling liên tục lên server. |

---

## 4. Chuẩn REST Response & Error Contract

### 4.1 Phản hồi thành công: Wrapper `RestResponse<T>` (`CONFIRMED`)

- Quyết định của Duy ngày 2026-10-11 thay thế convention DTO trực tiếp trước đó: giữ wrapper thành công hiện có của `ResFormatResponse` và `RestResponse<T>`.
- Với response JSON DTO thành công có body, đặt DTO trong `data`; không trả JPA Entity. Wrapper có `statusCode` (khớp HTTP status), `message` (thông báo kết quả), `error` (`null` khi thành công) và `data` (dữ liệu nghiệp vụ).
- Controller trả `ResponseEntity<DTO>`; `ResFormatResponse` bọc đúng một lần trước khi ghi HTTP body. Không tự tạo thêm `RestResponse` trong Controller vì advice hiện có sẽ bọc lồng nhau.
- `message` lấy từ `@ApiMessage`, mặc định là `Call API success!` khi method không có annotation. Với gửi tin nhắn, giá trị là `Gửi tin nhắn thành công`; nội dung chat nằm ở `data.content`.
- Backend dùng `ResponseEntity` để chọn HTTP status phù hợp; không trả HTTP 200 cho mọi kết quả.
- HTTP `204` không có body. Giữ các ngoại lệ hiện có của advice cho Swagger/API docs, String, file/resource và byte array; không áp dụng wrapper JSON cho các response đó.
- Response lỗi có status từ `400` trở lên không đi qua wrapper thành công; yêu cầu Problem Details ở mục 4.3 giữ nguyên. Việc chốt wrapper không xác nhận các error handlers đã đáp ứng contract.
- Không để lộ password, password hash, token hoặc thông tin nhạy cảm.
- Đây là **convention riêng của DenHub**. HTTP/REST không bắt buộc mọi dự án phải dùng hoặc phải bỏ wrapper.

| HTTP status | Ý nghĩa áp dụng |
| :--- | :--- |
| `200 OK` | Lấy hoặc cập nhật dữ liệu thành công, có body. |
| `201 Created` | Tạo tài nguyên mới thành công. |
| `204 No Content` | Thành công, không có response body. |
| `400 Bad Request` | Request không hợp lệ. |
| `401 Unauthorized` | Chưa xác thực hoặc token không hợp lệ. |
| `403 Forbidden` | Đã xác thực nhưng không có quyền thực hiện. |
| `404 Not Found` | Không tìm thấy tài nguyên. |
| `500 Internal Server Error` | Lỗi hệ thống ngoài dự kiến. |

### 4.2 Cấu trúc phân trang Cursor chuẩn (Lịch sử tin nhắn)

Đây là cấu trúc DTO phân trang đặt trong `data` của wrapper tại mục 4.1; ví dụ dưới đây chỉ mô tả phần `data`. Lịch sử tin nhắn nằm ngoài Task 1.07 / 1.12.

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false,
  "limit": 30
}
```

### 4.3 Phản hồi lỗi: Problem Details theo RFC 9457 (`CONFIRMED`)

Sử dụng `application/problem+json` cho response Problem Details. Ví dụ:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Bạn không có quyền gửi tin nhắn trong Channel này"
}
```

- Ưu tiên `ProblemDetail` có sẵn của Spring khi phiên bản hỗ trợ.
- Kiểm tra và tái sử dụng cơ chế xử lý lỗi hiện tại; không tạo GlobalExceptionHandler trùng hoặc xung đột.
- Phân biệt validation (`400`), authentication (`401`), authorization (`403`) và tài nguyên không tồn tại (`404`).
- Không trả stack trace, nguyên nhân nội bộ nhạy cảm hoặc credentials cho client.
- Lỗi do Security filter phát sinh cũng phải được kiểm tra, không chỉ lỗi trong Controller.

### 4.4 Axios đọc Response DTO trong wrapper

```javascript
const response = await axiosClient.post(`/channels/${channelId}/messages`, { content });
const message = response.data.data;
```

`axiosClient` hiện có base URL kết thúc bằng `/api`; không thêm `/api` lần nữa vào đường dẫn trên. Interceptor hiện trả nguyên Axios response: `response.data` là toàn bộ wrapper, `response.data.message` là thông báo thành công và `response.data.data` là DTO nghiệp vụ. Ví dụ `message.content` là nội dung chat. Quy tắc đọc này áp dụng cho response thành công được bọc; response lỗi đọc theo mục 4.3.

### 4.5 Danh mục từ nhánh incoming — cần đối chiếu với contract

Bảng dưới giữ các endpoint và trạng thái được ghi ở nhánh incoming trước khi gộp ngày 2026-10-11. Đây là bản ghi để đối chiếu, không thay thế contract mục 5 và không xác nhận mọi endpoint đã tồn tại. Các mục TBD vẫn chưa được quyết định.

| Phân hệ | Phương thức | Endpoint | Chức năng dự kiến | Trạng thái |
| :--- | :---: | :--- | :--- | :---: |
| **Auth** | `POST` | `/api/v1/auth/register` | Đăng ký tài khoản mới | `TBD` |
| **Auth** | `POST` | `/api/v1/auth/login` | Đăng nhập và lấy Access Token JWT | `TBD` |
| **Auth** | `GET` | `/api/v1/auth/me` | Lấy thông tương user hiện tại qua token | `TBD` |
| **Auth** | `POST` | `/api/v1/auth/logout` | Đăng xuất người dùng | `TBD` |
| **Server** | `GET` | `/api/v1/servers` | Lấy danh sách Server mà User là Member / Owner | `CONFIRMED` |
| **Server** | `POST` | `/api/v1/servers` | Tạo một Server mới | `CONFIRMED` |
| **Server** | `GET` | `/api/v1/servers/{serverId}` | Lấy thông tin chi tiết một Server | `TBD` |
| **Server** | `PUT` | `/api/v1/servers/{serverId}` | Cập nhật thông tin Server | `TBD` |
| **Server** | `DELETE`| `/api/v1/servers/{serverId}` | Xóa Server | `TBD` |
| **Server** | `POST` | `/api/v1/servers/{serverId}/join` | Tham gia vào một Server | `IMPLEMENTED` |
| **Server** | `POST` | `/api/v1/servers/{serverId}/leave` | Rời khỏi Server | `TBD` |
| **Member** | `GET` | `/api/v1/servers/{serverId}/members` | Lấy danh sách Member của Server | `TBD` |
| **Member** | `DELETE`| `/api/v1/servers/{serverId}/members/{userId}` | Kick một Member ra khỏi Server | `TBD` |
| **Member** | `PUT` | `/api/v1/servers/{serverId}/members/{userId}/role` | Phân quyền Member trong Server | `TBD` |
| **Message** | `GET` | `/api/v1/servers/{serverId}/messages` | Lấy lịch sử Message (có pagination) | `TBD` |
| **Message** | `DELETE`| `/api/v1/servers/{serverId}/messages/{messageId}` | Xóa Message (nếu có tính năng) | `TBD` |
| **Channel** | `POST` | `/api/v1/channels` | Tạo mới một Channel trong Server (yêu cầu là Owner/Member) | `CONFIRMED` |
| **Channel** | `GET` | `/api/v1/servers/{serverId}/channels` | Lấy danh sách Channel của Server | `TBD` |
| **Media / File** | `POST` | `/api/v1/files/upload` | Tải lên file ảnh (multipart/form-data) nhận URL tĩnh | `CONFIRMED` |
| **Media / File** | `GET` | `/api/v1/files/{folder}/{filename}` | Phục vụ file ảnh công khai cho trình duyệt | `CONFIRMED` |

Hiện trạng đã đọc từ source sau gộp:

- AccountResource có POST /api/login; Auth frontend gọi /login và /register với base URL /api. Chưa tìm thấy backend GET /api/users/me; Auth khôi phục cache/JWT theo incoming, không gọi endpoint này. JWT decode trên frontend không xác minh chữ ký; backend vẫn chịu trách nhiệm xác thực/phân quyền.
- ServerResource dùng /api/v1/servers (GET, POST, POST /{serverId}/join); ChannelResource dùng POST /api/v1/channels; FileResource dùng /api/v1/files. Đây là chênh lệch với mục 5, không tự đổi route trong lần giải conflict.
- ChannelService cho Owner hoặc Member tạo kênh, khác Owner/Admin tại mục 5.3. Giữ hành vi incoming theo phạm vi được duyệt; cần quyết định riêng trước khi đổi permission.
- ResFormatResponse bọc success response đúng quyết định SUCCESS-WRAPPER tại mục 4.1/7.1. Hai file MessageResource.java và MessageResourceIT.java còn untracked; test kiểm tra $.data.* phù hợp wrapper. Task 1.07 chưa hoàn tất do còn thiếu kiểm thử Security filter/JWT, Problem Details và ghi SQL Server.
- Backend 58 tests và frontend build PASS thuộc lượt gộp code trước khi áp dụng stash. Lượt gộp tài liệu sau stash không chạy lại test/build; các kết quả lịch sử không xác nhận lại toàn bộ working tree hiện tại hoặc các chênh lệch còn lại.

---

## 5. Danh mục REST API Chuẩn hóa (`CONFIRMED`)

### 5.1 Authentication & User
- `POST /api/auth/register`: Đăng ký tài khoản mới
- `POST /api/auth/login`: Đăng nhập, nhận Access Token JWT và cookie Refresh Token
- `POST /api/auth/logout`: Đăng xuất, hủy refresh token
- `GET /api/users/me`: Thông tin người dùng hiện tại
- `PATCH /api/users/me`: Cập nhật display name, avatar
- `GET /api/users/search?q={query}`: Tìm kiếm người dùng theo tên

### 5.2 Server & Member Management
- `GET /api/servers`: Lấy danh sách server người dùng tham gia
- `POST /api/servers`: Tạo server mới (transactional với `#general` channel và Owner)
- `GET /api/servers/{serverId}`: Chi tiết server kèm danh sách kênh
- `PATCH /api/servers/{serverId}`: Cập nhật tên/ảnh server (Owner)
- `DELETE /api/servers/{serverId}`: Xóa server (Owner)
- `POST /api/servers/{serverId}/leave`: Rời server (Member, Admin)
- `POST /api/servers/{serverId}/invites`: Tạo invite code
- `POST /api/invites/{code}/join`: Tham gia server qua code
- `DELETE /api/invites/{code}`: Thu hồi invite code
- `GET /api/servers/{serverId}/members`: Danh sách thành viên
- `DELETE /api/servers/{serverId}/members/{userId}`: Kick thành viên
- `PATCH /api/servers/{serverId}/members/{userId}/role`: Phân quyền `ADMIN`/`MEMBER` (Owner)

### 5.3 Channels & Messages
- `GET /api/servers/{serverId}/channels`: Danh sách kênh trong server
- `POST /api/servers/{serverId}/channels`: Tạo kênh văn bản mới (Owner/Admin)
- `PATCH /api/channels/{channelId}`: Đổi tên / thứ tự kênh
- `DELETE /api/channels/{channelId}`: Xóa kênh (kênh `#general` không thể xóa)
- `GET /api/channels/{channelId}/messages?before={id}&limit=30`: Lịch sử tin nhắn
- `POST /api/channels/{channelId}/messages`: Gửi tin nhắn mới
- `PATCH /api/messages/{messageId}`: Chỉnh sửa tin nhắn (tác giả)
- `DELETE /api/messages/{messageId}`: Xóa mềm tin nhắn
- `POST /api/messages/{messageId}/reactions`: Thả emoji reaction
- `DELETE /api/messages/{messageId}/reactions/{emoji}`: Gỡ emoji reaction

### 5.4 Direct Messages (1-1)
- `GET /api/direct-conversations`: Danh sách các cuộc trò chuyện 1-1
- `POST /api/direct-conversations`: Khởi tạo hoặc tìm cuộc trò chuyện 1-1
- `GET /api/direct-conversations/{id}/messages?before={id}&limit=30`: Lịch sử DM
- `POST /api/direct-conversations/{id}/messages`: Gửi tin nhắn DM

---

## 6. Quy chuẩn WebSocket / STOMP Real-time Contract (`CONFIRMED`)

WebSocket là task sau, không triển khai trong Task 1.07 hoặc 1.12. Các destination dưới đây giữ vai trò contract cho giai đoạn real-time.

- **Handshake Endpoint:** `/ws`
- **Authentication:** Header `Authorization: Bearer <jwt-token>` trong frame STOMP `CONNECT`.
- **Authorization on Subscribe:** `ChannelInterceptor` kiểm tra người dùng phải là `ServerMember` của server sở hữu channel mới cho phép subscribe topic.

### Ma trận Destination & Topic:
```
Client ──[SEND]──> /app/channels/{channelId}/send ──> Server Process & Commit
                                                            │
Server ──[BROADCAST]──> /topic/channels/{channelId} ───────(Tất cả Members)
Server ──[PRIVATE]────> /user/queue/direct-messages ───────(Người nhận DM)
```

| Destination | Action | Mục đích |
| :--- | :---: | :--- |
| `/app/channels/{channelId}/send` | `SEND` | Gửi tin nhắn văn bản vào channel |
| `/topic/channels/{channelId}` | `SUBSCRIBE` | Nhận sự kiện tin nhắn (`CREATED`, `UPDATED`, `DELETED`) |
| `/topic/channels/{channelId}/reactions` | `SUBSCRIBE` | Nhận sự kiện reaction (`ADDED`, `REMOVED`) |
| `/topic/servers/{serverId}` | `SUBSCRIBE` | Nhận sự kiện quản trị server (kênh mới, thành viên bị kick) |
| `/user/queue/direct-messages` | `SUBSCRIBE` | Nhận tin nhắn riêng tư 1-1 gửi trực tiếp cho người dùng |
| `/user/queue/errors` | `SUBSCRIBE` | Nhận thông báo lỗi thời gian thực cá nhân |

---

## 7. Task 1.07 — API gửi tin nhắn Channel

### 7.1 Request và response (`CONFIRMED`)

`POST /api/channels/{channelId}/messages`, xác thực bằng JWT theo cơ chế hiện có.

Request DTO `SendMessageRequest` chỉ nhận nội dung:

```json
{
  "content": "Hello mọi người!"
}
```

HTTP `201 Created`, Response DTO `MessageResponse` nằm trong `data`:

```json
{
  "statusCode": 201,
  "error": null,
  "message": "Gửi tin nhắn thành công",
  "data": {
    "id": 101,
    "channelId": 5,
    "senderId": 2,
    "content": "Hello mọi người!",
    "createdAt": "2026-10-10T07:30:00Z"
  }
}
```

Các ID và thời gian chỉ minh họa cấu trúc, không được hardcode. `senderId` ánh xạ từ `Message.authorId`; không nhận danh tính người gửi từ request. `MessageResponse.createdAt` dùng `Instant`, lấy từ `Message.createdDate`; contract JSON dùng ISO-8601 UTC với hậu tố `Z`. MockMvc test hiện kiểm tra HTTP 201, wrapper, thông báo, `error: null` và DTO kể cả timestamp; giới hạn kiểm thử được ghi tại mục 7.4.

### 7.2 Xử lý và bảo mật

1. Xác định người dùng từ Spring Security/JWT đã được xác thực; không hardcode tài khoản và không bỏ qua authentication.
2. Validate `content`: không null, không rỗng, không toàn khoảng trắng kể cả Unicode, tối đa 4000 đơn vị UTF-16 theo quyết định mục 4.2 của tài liệu database. Giữ nguyên nội dung hợp lệ; không trim hoặc cắt bớt tin nhắn. Database vẫn dùng `NVARCHAR(MAX)`.
3. Kiểm tra Channel tồn tại; trả `404` nếu không tìm thấy.
4. Kiểm tra người gửi thuộc Server chứa Channel; trả `403` nếu không có membership.
5. Lưu Message bằng Repository vào SQL Server, map sang Response DTO; advice bọc DTO trong `data` và trả HTTP `201`.

Luồng: `HTTP Request → Security/JWT → Controller/Validation → Service → Repository → SQL Server → Response DTO → ResFormatResponse → HTTP Response`.

Chỉ gửi tin nhắn văn bản trong task này; không làm lịch sử, sửa/xóa, reply, reaction, attachment, Direct Message hoặc WebSocket.

### 7.3 Hiện trạng cần xử lý khi lập kế hoạch (2026-10-11)

Đây là hiện trạng lượt 1A và phần lượt 1B đã có ngày 2026-10-11, gồm Controller/test untracked; xem thêm mục 4.5.

| Thành phần đã kiểm tra | Hiện trạng / tác động |
| :--- | :--- |
| `Message`, `MessageRepository` | Đã tồn tại; tái sử dụng, không tạo trùng. |
| `Channel`, `ServerMember`, `ServerMemberRepository` | Tái sử dụng Entity; lượt 1A thêm `ChannelRepository` và `existsByServerIdAndUserId` vào Repository membership. |
| Message Service / Controller / DTO | Lượt 1A đã thêm `MessageService`, `SendMessageRequest`, `MessageResponse` và unit tests. Lượt 1B có `MessageResource` (`POST /api/channels/{channelId}/messages`, HTTP 201 Created) và `MessageResourceIT` dùng MockMvc standalone với Service mock; chưa kiểm thử Security filter hoặc ghi SQL Server. |
| `ResFormatResponse` | `ResponseBodyAdvice` tự bọc response thành công theo convention được Duy chốt ngày 2026-10-11 tại mục 4.1. |
| `ExceptionTranslator` / Security errors | Đã có handler dùng Zalando Problem. `SmartAuthenticationEntryPoint` hiện trả JSON tùy chỉnh (`application/json`), chưa đúng contract Problem Details; các test lỗi hiện chỉ kiểm tra status. Cần kế hoạch riêng trước khi sửa shared handlers. |
| JWT hiện có | JWT đã xác thực có subject là email; Service dùng `SecurityUtils` đọc subject rồi `UserRepository.findOneByEmail` lấy User ID. Từ chối anonymous, principal chưa xác thực, User không tồn tại hoặc chưa activated; không dùng ID do client gửi. |
| `client/src/api/messageApi.js` | Đã cập nhật contract `/channels/{channelId}/messages`. |

Các thành phần và giới hạn trên dùng để lập kế hoạch phần còn lại; không coi MockMvc standalone là kiểm thử toàn chuỗi qua JWT và SQL Server. Nếu thay đổi shared infrastructure đụng module của Ben, Hoàng, Vinh hoặc Kỳ Anh, phải trình phạm vi cụ thể và xin quyết định trước khi sửa.

### 7.4 Phần đã triển khai trong lượt 1A

`MessageService.sendMessage(channelId, request)` thực hiện trong `@Transactional`: xác định email từ Security Context, validate request, tra User, tra Channel, kiểm tra membership của chính Server chứa Channel, rồi `MessageRepository.save` và map DTO từ Entity trả về.

Service dùng `ResponseStatusException` của Spring với `400`, `401`, `403`, `404`; chưa tạo handler mới. Exception mang status/reason không có nghĩa HTTP Problem Details đã được kiểm chứng. Lượt 1B đã có MockMvc kiểm tra `201` và wrapper thành công, validation nội dung rỗng/quá dài trả `400`, Service mock ném lỗi `401`/`403`/`404`. Còn thiếu kiểm thử JWT qua Security filter và xác nhận body/content type `application/problem+json`; Task 1.07 vẫn `IN_PROGRESS`.

Unit tests hiện dùng Validator thật, Repository mock và Security Context chứa JWT fixture đã xác thực. Chúng không xác minh chữ ký JWT, HTTP response, transaction rollback thực tế hoặc việc ghi vào SQL Server. Spring context test dùng H2 riêng theo cấu hình test hiện có.

## 8. Task 1.12 — UI nhập và gửi tin nhắn

- Dùng React + Ant Design; tái sử dụng hoặc tạo `MessageInput` sau khi kiểm tra component hiện tại.
- Nhận `channelId` qua props hoặc cơ chế state/router có sẵn; không hardcode `channelId` hoặc `senderId`.
- Controlled input có nút Send; Enter gửi, Shift + Enter xuống dòng.
- Không gửi nội dung rỗng hoặc toàn khoảng trắng; disable Send khi không hợp lệ hoặc đang gửi, hiển thị loading và ngăn gửi trùng trong lúc chờ request.
- Hiển thị lỗi khi thất bại, giữ nội dung để người dùng thử lại; chỉ xóa input sau khi gửi thành công.
- Khi API sẵn sàng **và Duy đồng ý tích hợp**, dùng Axios service theo cấu trúc hiện có, lấy JWT theo Authentication hiện tại, POST đúng endpoint và đọc DTO từ `response.data.data` theo mục 4.4.
- Trả kết quả thành công cho component cha để cập nhật UI. Không thêm WebSocket hoặc chat riêng trong task này.
- Nếu chưa thể tích hợp, test UI độc lập bằng mock; không đưa mock sender hoặc bypass xác thực vào production.

Luồng tích hợp: `Người dùng nhập → React State cập nhật → Send → Axios → Backend lưu → Wrapper chứa Response DTO → Axios đọc response.data.data → Component cha cập nhật UI`.
