# Tài liệu Kiến trúc & Luồng Xử lý Toàn diện: Đăng nhập & Đăng ký (End-to-End Auth Flow)

> **Dự án:** DenHub (Full-Stack Realtime Communication Platform)  
> **Phiên bản:** 2.0  
> **Tài liệu tham chiếu:** [`01-PROJECT-OVERVIEW.md`](../01-PROJECT-OVERVIEW.md), [`02-SYSTEM-ARCHITECTURE.md`](../02-SYSTEM-ARCHITECTURE.md), [`03-DATABASE-DESIGN.md`](../03-DATABASE-DESIGN.md)

---

## 1. Tổng quan Kiến trúc Xác thực (Authentication Overview)

DenHub sử dụng kiến trúc **Xác thực phi trạng thái (Stateless Authentication)** kết hợp **Cơ chế Token Kép (Dual Token Pattern)**:

1. **Access Token (JWT):**
   - Thời hạn: 7 ngày (`denhub.jwt.access-token-validity-in-seconds=604800`).
   - Lưu trữ phía Frontend: Bộ nhớ ứng dụng (`AuthContext`) và `localStorage` (`denhub_access_token`).
   - Vận chuyển: Gắn vào Request Header `Authorization: Bearer <token>` trên mỗi request thông qua Axios Interceptor.
2. **Refresh Token (JWT):**
   - Thời hạn: 15 ngày (`denhub.jwt.refresh-token-validity-in-seconds=1296000`).
   - Lưu trữ phía Backend: Bảng `users`, cột `refresh_token` (`NVARCHAR(MAX)`).
   - Lưu trữ phía Frontend: Lưu trong Cookie an toàn (`HttpOnly=true`, `Secure=true`, `SameSite=Strict`, `Path=/`). Xóa khỏi Response Body để chống tấn công XSS.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   SƠ ĐỒ TỔNG THỂ                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘

  [ Người dùng ]
        │
        ▼ (Thao tác trên giao diện)
  ┌────────────────────────────────────────────────────────┐
  │ FRONTEND (React + Ant Design + Vite)                   │
  │  - Trang: LoginPage.jsx / RegisterPage.jsx             │
  │  - Quản lý trạng thái: AuthContext.jsx / useAuth       │
  │  - Tầng API: authApi.js                                │
  │  - Tầng HTTP Client: axiosClient.js (Interceptors)     │
  └──────────────────────────┬─────────────────────────────┘
                             │ HTTP POST (/api/login, /api/register, /api/social-login)
                             ▼
  ┌────────────────────────────────────────────────────────┐
  │ BACKEND (Spring Boot 3 + Spring Security 6)            │
  │  - Cổng bảo mật: SecurityConfiguration (FilterChain)   │
  │  - Bộ giải mã Token: GuestAwareJwtDecoder              │
  │  - Controller: AccountResource.java                    │
  │  - Nghiệp vụ: UserService.java & AuthService.java      │
  │  - Bảo mật & JWT: SecurityUtils.java & BCryptEncoder   │
  └──────────────────────────┬─────────────────────────────┘
                             │ Truy vấn SQL / Lưu trữ dữ liệu
                             ▼
  ┌────────────────────────────────────────────────────────┐
  │ DATABASE (Microsoft SQL Server)                        │
  │  - Bảng: users, authorities, user_authority            │
  └────────────────────────────────────────────────────────┘
```

---

## 2. Luồng 1: Đăng nhập bằng Email & Mật khẩu (Standard Login)

### Bước 1: Làm sao để người dùng vào được trang Đăng nhập?
Có 3 con đường dẫn đến trang Login (`/login`):
1. **Truy cập trực tiếp:** Người dùng gõ URL `http://localhost:3000/login` trên trình duyệt.
2. **Từ trang Đăng ký:** Ở chân form trang [`RegisterPage.jsx`](../../client/src/pages/auth/RegisterPage.jsx), người dùng nhấp vào link:
   ```jsx
   <Link to="/login" style={{ color: '#00F2FE' }}>Đã có tài khoản? Đăng nhập</Link>
   ```
3. **Chuyển hướng tự động (Route Protection):**
   - Khi người dùng chưa đăng nhập nhưng cố truy cập vào các trang được bảo vệ (`/`, `/rooms`, `/servers/:id`, `/profile`,...):
   - Tuyến đường được bảo vệ bởi [`ProtectedRoute.jsx`](../../client/src/routes/ProtectedRoute.jsx).
   - Component kiểm tra trạng thái `const { isAuthenticated, loading } = useAuth();`.
   - Nếu `!isAuthenticated`, lập tức kích hoạt `<Navigate to="/login" replace />`.

*Khai báo định tuyến tại:* [`client/src/routes/AppRoutes.jsx`](../../client/src/routes/AppRoutes.jsx):
```jsx
<Route element={<AuthLayout />}>
  <Route path="/login" element={<LoginPage />} />
  <Route path="/register" element={<RegisterPage />} />
</Route>
```

---

### Bước 2: Thao tác trên giao diện & Xử lý Form
- **Component đảm nhiệm:** [`client/src/pages/auth/LoginPage.jsx`](../../client/src/pages/auth/LoginPage.jsx).
- **Phần tử người dùng tương tác:**
  - Ô nhập email/username:
    ```jsx
    <Form.Item name="username" rules={[{ required: true, message: 'Vui lòng nhập Email hoặc Số điện thoại' }]}>
      <Input placeholder="name@example.com" className="discord-input" />
    </Form.Item>
    ```
  - Ô nhập mật khẩu:
    ```jsx
    <Form.Item name="password" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
      <Input.Password placeholder="••••••••" className="discord-input" />
    </Form.Item>
    ```
  - Nút bấm **"Đăng nhập"**:
    ```jsx
    <Button type="primary" htmlType="submit" loading={loading} className="discord-button-primary">
      Đăng nhập
    </Button>
    ```
- **Hàm xử lý sự kiện (Handler):**
  - Form của Ant Design kích hoạt sự kiện `onFinish={handleSubmit}`.
  - Hàm `handleSubmit(values)` trong `LoginPage.jsx`:
    ```javascript
    const handleSubmit = async (values) => {
      setLoading(true);
      setError(null);
      try {
        await login(values); // values = { username: "...", password: "..." }
        navigate('/');       // Thành công -> Chuyển hướng về trang chủ
      } catch (err) {
        // Bắt lỗi 401 hoặc thông báo từ backend để hiển thị Alert
        setError('Email hoặc mật khẩu không chính xác. Vui lòng thử lại!');
      } finally {
        setLoading(false);
      }
    };
    ```

---

### Bước 3: Tầng Context & Gửi Request sang Máy chủ
1. **Gọi hàm trong Context:**
   - Hàm `login` được cung cấp bởi [`client/src/contexts/AuthContext.jsx`](../../client/src/contexts/AuthContext.jsx):
   ```javascript
   const login = useCallback(async (credentials) => {
     const res = await authApi.login(credentials);
     const payload = res.data?.data || res.data;
     const accessToken = payload?.accessToken;
     const refreshToken = payload?.refreshToken;
     const user = payload?.user;

     setTokens(accessToken, refreshToken); // Lưu Access Token vào localStorage
     localStorage.setItem('denhub_user', JSON.stringify(normalizedUser));
     setCurrentUser(normalizedUser);
     return normalizedUser;
   }, []);
   ```
2. **Tầng API Client:**
   - [`client/src/api/authApi.js`](../../client/src/api/authApi.js):
   ```javascript
   login(credentials) {
     return axiosClient.post('/login', credentials);
   }
   ```
3. **Tầng HTTP Client:**
   - [`client/src/api/axiosClient.js`](../../client/src/api/axiosClient.js):
   - Đính kèm Base URL: `http://localhost:8080/api` (từ biến môi trường `VITE_API_BASE_URL`).
   - Thiết lập header: `Content-Type: application/json`.
   - Gửi HTTP Request:
     - **Method:** `POST`
     - **URL:** `http://localhost:8080/api/login`
     - **Body (JSON):**
       ```json
       {
         "username": "haohoangth809@gmail.com",
         "password": "mypassword123"
       }
       ```

---

### Bước 4: Backend tiếp nhận Request tại Cổng Bảo Mật (Spring Security)
1. Request đi vào bộ lọc [`SecurityConfiguration.java`](../../server/src/main/java/com/denhub/config/SecurityConfiguration.java):
   - Địa chỉ `/api/login` nằm trong danh sách `publicEndpoints` và được cấu hình mở:
     ```java
     .requestMatchers(HttpMethod.POST, "/api/login", ...).permitAll()
     ```
   - Bộ giải mã `GuestAwareJwtDecoder` kiểm tra: vì request gửi đến endpoint public và không mang JWT Header, nó cho phép request đi tiếp qua chuỗi bộ lọc (Filter Chain) mà không chặn lỗi 401.
   - CSRF được vô hiệu hóa (`csrf(AbstractHttpConfigurer::disable)`), CORS cho phép Client gọi sang.

---

### Bước 5: Controller / Resource tiếp nhận & Bóc tách dữ liệu
- **File:** [`server/src/main/java/com/denhub/web/rest/AccountResource.java`](../../server/src/main/java/com/denhub/web/rest/AccountResource.java)
- **Method tiếp nhận:**
  ```java
  @PostMapping("/login")
  @Operation(summary = "Đăng nhập", description = "Xác thực người dùng và nhận về Access Token cùng Refresh Token (cookie).")
  public ResponseEntity<ResLoginDTO> loginWithEmail(@Valid @RequestBody LoginVM vm) {
      String normalizedEmail = vm.getUsername().trim().toLowerCase(Locale.ENGLISH);
      
      // Tạo token xác thực của Spring Security
      UsernamePasswordAuthenticationToken userToken = 
          new UsernamePasswordAuthenticationToken(normalizedEmail, vm.getPassword());
  ```

---

### Bước 6: Xác thực Mật khẩu & Truy vấn Database
1. `AccountResource` gọi trình quản lý xác thực:
   ```java
   Authentication authentication = authenticationManagerBuilder.getObject().authenticate(userToken);
   ```
2. Spring Security tự động kích hoạt Service tìm kiếm người dùng [`DomainUserDetailsService.java`](../../server/src/main/java/com/denhub/security/DomainUserDetailsService.java):
   - Gọi `userRepository.findOneWithAuthoritiesByEmail(email)`.
   - Sinh câu lệnh SQL truy vấn vào database:
     ```sql
     SELECT u.*, a.name 
     FROM users u 
     LEFT JOIN user_authority ua ON u.id = ua.user_id 
     LEFT JOIN authorities a ON ua.authority_name = a.name 
     WHERE LOWER(u.email) = 'haohoangth809@gmail.com';
     ```
3. Bộ so khớp mật khẩu [`BCryptPasswordEncoder`](../../server/src/main/java/com/denhub/config/SecurityConfiguration.java):
   - So sánh chuỗi mật khẩu người dùng gửi lên với trường `password_hash` được lưu trong bảng `users`.
   - **Nếu mật khẩu sai:** Ném ngoại lệ `BadCredentialsException` $\rightarrow$ Trả về mã lỗi HTTP 400/401 kèm message chi tiết.
   - **Nếu mật khẩu đúng:** Trả về đối tượng `Authentication` hợp lệ, lưu vào phiên hiện tại:
     ```java
     SecurityContextHolder.getContext().setAuthentication(authentication);
     ```

---

### Bước 7: Sinh Token & Thiết lập Phản hồi (AuthService)
1. `AccountResource` lấy thực thể `User` từ `UserDetails` và chuyển sang [`AuthService.java`](../../server/src/main/java/com/denhub/service/AuthService.java):
   ```java
   ResLoginDTO res = authService.generateResLoginDTO(user);
   ```
2. Trong `AuthService.java`:
   - Khởi tạo session: `String sessionId = sessionManager.initSessionId(user.getId());`
   - Gọi [`SecurityUtils.java`](../../server/src/main/java/com/denhub/security/SecurityUtils.java) tạo **Access Token**:
     - Thuật toán ký: `HS256`.
     - Claims: `sub = user.getEmail()`, `auth = [ROLE_USER, ...]`, `sessionId = ...`.
   - Gọi `securityUtils.createRefreshToken(userLogin)` tạo **Refresh Token**.
   - Cập nhật Refresh Token vào Database:
     ```java
     userRepository.updatedRefreshTokenById(userLogin.getId(), newRefreshToken);
     ```
     Sinh lệnh SQL:
     ```sql
     UPDATE users SET refresh_token = 'eyJhbGciOiJIUzI1NiJ...' WHERE id = 12;
     ```
3. Đóng gói Cookie an toàn trong `AccountResource.java`:
   ```java
   ResponseCookie cookie = ResponseCookie.from("refresh_token", res.getRefreshToken())
           .httpOnly(true)
           .secure(true)
           .path("/")
           .maxAge(refreshTokenExpiration)
           .sameSite("Strict")
           .build();
   res.setRefreshToken(null); // Không để lộ refresh token trong body
   return ResponseEntity.ok()
           .header(HttpHeaders.SET_COOKIE, cookie.toString())
           .body(res);
   ```

---

### Bước 8: Frontend nhận Kết quả & Chuyển vào Màn hình Chính
1. Trình duyệt tự động lưu Cookie `refresh_token` vào kho Cookie an toàn của domain `localhost`.
2. Hàm `login` trong `AuthContext.jsx` nhận được JSON Body:
   ```json
   {
     "statusCode": 200,
     "message": "Đăng nhập thành công",
     "data": {
       "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
       "user": {
         "id": 12,
         "email": "haohoangth809@gmail.com",
         "name": "Hoàng Hạo",
         "authorities": ["ROLE_USER"]
       }
     }
   }
   ```
3. `setTokens(accessToken)` lưu chuỗi token vào `localStorage.setItem('denhub_access_token', token)`.
4. Cập nhật State React: `setCurrentUser(normalizedUser)`.
5. Trong `LoginPage.jsx`, câu lệnh `navigate('/')` được thực thi.
6. React Router chuyển sang màn hình chính [`HomePage.jsx`](../../client/src/pages/home/HomePage.jsx) hoặc phòng chat. Lúc này mọi request API tiếp theo đều được `axiosClient` tự động gắn `Authorization: Bearer <accessToken>`.

---

## 3. Luồng 2: Đăng ký Tài khoản Mới (Registration Flow)

```
[ Người dùng ]
      │ Nhập Email, Password, Name, Username
      ▼
LoginPage / Link ──> RegisterPage.jsx (onFinish={handleSubmit})
                            │
                            ▼
                     authApi.register(payload)
                            │
                            ▼ POST /api/register
                     SecurityConfiguration (permitAll)
                            │
                            ▼
                     AccountResource.registerAccount(req)
                            │
                            ▼
                     UserService.registerUser(req)
                            │
            ┌───────────────┴───────────────┐
            │ Kiểm tra email trùng lặp?     │
            │ (userRepository.findOneByEmail)
            └───────────────┬───────────────┘
                            ▼
                     Mã hóa mật khẩu: passwordEncoder.encode(req.getPassword())
                     Gán quyền: ROLE_USER
                     Lưu vào SQL: userRepository.save(newUser)
                            │
                            ▼ HTTP 201 Created (UserDTO)
                     RegisterPage hiển thị thông báo thành công
                            │
                            ▼ setTimeout 1.2s
                     navigate('/login')
```

### Bước 1: Cách vào trang Đăng ký
- Người dùng vào `http://localhost:3000/register`.
- Hoặc từ trang `LoginPage.jsx`, nhấp vào:
  ```jsx
  <Link to="/register" style={{ color: '#00F2FE' }}>Cần một tài khoản? Đăng ký</Link>
  ```

---

### Bước 2: Thao tác Form & Handler phía Client
- **File:** [`client/src/pages/auth/RegisterPage.jsx`](../../client/src/pages/auth/RegisterPage.jsx)
- **Các trường nhập liệu:**
  - `email`: Email người dùng (bắt buộc, đúng định dạng).
  - `displayName`: Tên hiển thị công khai.
  - `username`: Tên định danh (handle @username).
  - `password`: Mật khẩu (tối thiểu 6 ký tự).
  - `confirmPassword`: Xác nhận lại mật khẩu (kiểm tra trùng khớp qua validator rule).
- **Hàm xử lý:**
  ```javascript
  const handleSubmit = async (values) => {
    setLoading(true);
    setError(null);
    setSuccessMsg(null);
    try {
      const payload = {
        email: values.email,
        password: values.password,
        name: values.displayName || values.username || values.email.split('@')[0],
        username: values.username,
      };
      await authApi.register(payload); // Gửi API
      setSuccessMsg('Đăng ký tài khoản thành công! Đang chuyển hướng đến đăng nhập...');
      setTimeout(() => {
        navigate('/login');
      }, 1200);
    } catch (err) {
      setError(err.response?.data?.message || 'Đăng ký tài khoản không thành công.');
    } finally {
      setLoading(false);
    }
  };
  ```

---

### Bước 3: Tầng API Client
- [`client/src/api/authApi.js`](../../client/src/api/authApi.js):
  ```javascript
  register(data) {
    return axiosClient.post('/register', data);
  }
  ```
- Gửi HTTP Request:
  - **Method:** `POST`
  - **URL:** `http://localhost:8080/api/register`
  - **Body (JSON):**
    ```json
    {
      "email": "newbie@gmail.com",
      "password": "Password@123",
      "name": "Người Dùng Mới",
      "username": "newbie2026"
    }
    ```

---

### Bước 4: Backend tiếp nhận tại Controller & Service
1. **Security Configuration:** Endpoint `/api/register` được mở quyền tự do (`permitAll()`).
2. **AccountResource:**
   - [`server/src/main/java/com/denhub/web/rest/AccountResource.java`](../../server/src/main/java/com/denhub/web/rest/AccountResource.java)
   ```java
   @PostMapping({"/register", "/auth/register"})
   @ResponseStatus(HttpStatus.CREATED)
   @ApiMessage("Đăng ký tài khoản thành công")
   public ResponseEntity<UserDTO> registerAccount(@Valid @RequestBody RegisterReqDTO req) {
       User user = userService.registerUser(req);
       return ResponseEntity.status(HttpStatus.CREATED).body(new UserDTO(user));
   }
   ```
3. **UserService:**
   - [`server/src/main/java/com/denhub/service/UserService.java`](../../server/src/main/java/com/denhub/service/UserService.java)
   ```java
   public User registerUser(RegisterReqDTO req) {
       String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ENGLISH);
       
       // Kiểm tra xem email đã được đăng ký chưa
       userRepository.findOneByEmail(normalizedEmail).ifPresent(existingUser -> {
           boolean removed = this.removeNoneActivatedUser(existingUser);
           if (!removed) {
               throw new LoginAlreadyUsedException(); // Ném lỗi nếu đã tồn tại
           }
       });

       User newUser = new User();
       newUser.setEmail(normalizedEmail);
       // Mã hóa mật khẩu bảo mật chuẩn BCrypt
       String encryptedPassword = passwordEncoder.encode(req.getPassword());
       newUser.setPassword(encryptedPassword);
       newUser.setName(req.getName());
       newUser.setUsername(req.getUsername());
       newUser.setType("SYSTEM");
       newUser.setActivated(true);

       // Gán quyền cơ bản ROLE_USER
       Set<Authority> authorities = new HashSet<>();
       authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
       newUser.setAuthorities(authorities);

       // Lưu thực thể vào cơ sở dữ liệu
       newUser = userRepository.save(newUser);
       this.clearUserCaches(newUser);
       return newUser;
   }
   ```

---

### Bước 5: Phản hồi & Trải nghiệm Người dùng
- Backend trả về mã HTTP `201 Created` kèm thông tin tài khoản vừa tạo (không kèm hash mật khẩu).
- Trên giao diện `RegisterPage.jsx`, một thông báo màu xanh (Alert Success) xuất hiện.
- Trình duyệt đợi 1.2 giây rồi chuyển hướng người dùng mượt mà sang trang `/login` để đăng nhập.

---

## 4. Luồng 3: Đăng nhập bằng Mạng Xã Hội (Google & GitHub Social Login)

### Luồng Google Sign-In
1. **Khởi tạo Google Identity Services:**  
   Trong `LoginPage.jsx`, script `https://accounts.google.com/gsi/client` được tải động với `VITE_GOOGLE_CLIENT_ID`.
2. **Người dùng chọn tài khoản Google:**  
   Google popup trả về chuỗi `credential` (JWT ID Token).
3. **Frontend gửi Token:**  
   `handleGoogleResponse` gọi `loginWithGoogle({ credential })` gửi `POST /api/auth/google`.
4. **Backend xử lý (`AccountResource.java` & `UserService.java`):**  
   - Bóc tách Google JWT: lấy `email`, `name`, `picture`, `sub` (googleId).
   - `userService.processGoogleUser(...)`: Nếu email đã có thì cập nhật thông tin và kích hoạt; nếu chưa có thì tạo mới user với mật khẩu ngẫu nhiên đã mã hóa và gán quyền `ROLE_USER`.
   - Sinh Access Token và Set HttpOnly Cookie Refresh Token tương tự luồng đăng nhập thường.

---

### Luồng GitHub OAuth & Token Sign-In
1. **Người dùng bấm nút GitHub:**  
   Trong `LoginPage.jsx`, hàm `handleGithubClick`:
   - Nếu có sẵn Token Developer (`VITE_GITHUB_TOKEN`): gọi trực tiếp `loginWithSocial({ accessToken, type: 'GITHUB' })`.
   - Nếu dùng OAuth chuẩn: chuyển hướng trình duyệt sang:
     `https://github.com/login/oauth/authorize?client_id=...&scope=read:user,user:email`
2. **GitHub phản hồi chuyển hướng:**  
   GitHub redirect người dùng về:
   `http://localhost:3000/api/auth/callback/github?code=...` (hoặc `/login?code=...`).
3. **Frontend bắt Authorization Code:**  
   Tại `LoginPage.jsx`, `useEffect` đọc `code` từ URL query params và gọi `loginWithSocial({ code, type: 'GITHUB' })`.
4. **Backend trao đổi mã & Xử lý bảo mật:**
   - Gửi request đến `AccountResource.socialLogin` (`POST /api/social-login`).
   - `UserService.java`:
     - Gọi `getGithubAccessTokenFromCode(code)` sang GitHub API để đổi lấy Access Token.
     - Lấy profile người dùng: `https://api.github.com/user`.
     - Lấy email từ profile hoặc truy vấn `/user/emails`.
     - **Cơ chế Fallback thông minh:** Nếu tài khoản GitHub đặt email ở chế độ riêng tư (Private) hoặc token không có quyền đọc email, hệ thống tự động sinh email chuẩn:
       `{githubId}+{username}@users.noreply.github.com`
     - `processLogin(...)`: Tìm hoặc tạo mới người dùng, gán mật khẩu ngẫu nhiên mã hóa `UUID.randomUUID()`.
     - `authService.generateResLoginDTO(...)`: Lưu Refresh Token vào cột `refresh_token NVARCHAR(MAX)` trong SQL Server.
     - Đặt Cookie an toàn `Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict`.
5. **Frontend hoàn tất:**  
   Lưu Access Token vào `localStorage`, dọn sạch query param trên URL bằng `window.history.replaceState` và chuyển hướng vào `/`.

---

## 5. Bảng Đối chiếu Nhanh: Từ Giao diện đến Cơ sở dữ liệu

| Thao tác | File Frontend | Hàm / Xử lý FE | Endpoint API | Controller Backend | Service Backend | Thao tác Database |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Đến trang Login** | `AppRoutes.jsx` | Router Route `/login` | N/A | N/A | N/A | N/A |
| **Bấm Đăng nhập** | `LoginPage.jsx` | `onFinish={handleSubmit}` | `POST /api/login` | `AccountResource.java#loginWithEmail` | `AuthService.java#generateResLoginDTO` | `SELECT users`, `UPDATE users.refresh_token` |
| **Đến trang Register** | `AppRoutes.jsx` | Router Route `/register` | N/A | N/A | N/A | N/A |
| **Bấm Đăng ký** | `RegisterPage.jsx` | `onFinish={handleSubmit}` | `POST /api/register` | `AccountResource.java#registerAccount` | `UserService.java#registerUser` | `SELECT users`, `INSERT INTO users` |
| **Bấm Google** | `LoginPage.jsx` | `handleGoogleResponse` | `POST /api/auth/google` | `AccountResource.java#loginWithGoogle` | `UserService.java#processGoogleUser` | `SELECT/INSERT users`, `UPDATE refresh_token` |
| **Bấm GitHub** | `LoginPage.jsx` | `handleGithubClick` | `POST /api/social-login` | `AccountResource.java#socialLogin` | `UserService.java#loginWithSocialMedia` | `SELECT/INSERT users`, `UPDATE refresh_token` |

---

## 6. Các Điểm Cần Lưu Ý về Bảo Mật (Security Best Practices)

1. **Không lưu mật khẩu dạng thô (Raw Password):** Mật khẩu luôn được băm bằng thuật toán `BCrypt` với salt ngẫu nhiên trước khi ghi vào database.
2. **Bảo vệ Refresh Token:** Refresh Token tuyệt đối không lưu trong `localStorage` để ngăn ngừa bị đánh cắp qua tấn công XSS; token này được bảo vệ trong `HttpOnly Cookie` với thuộc tính `SameSite=Strict`.
3. **Cơ chế Tự động Gia hạn (Silent Refresh):** Khi Access Token hết hạn, `axiosClient` sẽ tự động bắt mã lỗi `401` để kích hoạt endpoint `/api/refresh` lấy token mới mà không bắt người dùng phải đăng nhập lại.
4. **Xử lý tài khoản Social:** Các tài khoản tạo qua Google/GitHub được cấp mật khẩu ngẫu nhiên cực dài (UUID) được băm an toàn, đảm bảo không thể bị dò quét hay đăng nhập trực tiếp qua form mật khẩu thông thường.

