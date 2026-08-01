# E-Learning Backend

Hệ thống backend quản lý học tập & luyện thi trực tuyến (E-Learning / Assessment Engine) được xây dựng trên nền tảng Java 21 và Spring Boot.

## 📌 Tiến độ khối nghiệp vụ (Chi tiết tại `e_learning.md`)

### 1. Khối Nghiệp vụ Người dùng & Phân quyền (Auth & Security) - 100% ✅
- **Đăng ký / Đăng nhập / Đăng xuất:** Standard Email + Password (BCrypt).
- **JWT Authentication:** Access Token + Refresh Token flow.
- **Quên / Đặt lại mật khẩu:** Token-based reset qua Email/OTP framework.
- **Phân quyền người dùng (RBAC):** Phân quyền `STUDENT`, `INSTRUCTOR`, `ADMIN` bảo mật bằng Spring Security.

### 2. Khối Nghiệp vụ Quản lý Khóa học & Nội dung (Course & Content) - 100% ✅
- **Cấu trúc phân cấp:** Khóa học (`Course`) $\rightarrow$ Chương (`CourseSection`) $\rightarrow$ Bài học (`Lesson`).
- **Đa dạng nội dung:** Bài học hỗ trợ Video (`videoUrl`), Document (`documentUrl` Text/PDF/Markdown) và Loại bài học (`LessonContentType`).
- **Theo dõi tiến độ học tập (Progress Tracking):**
  - Lưu vị trí xem dở (`lastPositionSeconds`).
  - Đánh dấu hoàn thành từng bài học (`LessonProgressStatus.COMPLETED`).
  - Tự động tính toán % tiến độ khóa học theo thời gian thực và cập nhật trạng thái `COMPLETED` cho khóa học khi đạt 100%.

### 3. Khối Nghiệp vụ Ngân hàng Câu hỏi & Tạo Đề thi (Quiz & Exam Engine) - 100% ✅
- **Ngân hàng câu hỏi (Question Bank):**
  - Quản lý theo Chủ đề (`QuestionTopic`) và Độ khó (`EASY`, `MEDIUM`, `HARD`).
  - Đa dạng dạng câu hỏi (`SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FILL_IN_BLANK`, `SHORT_ANSWER`).
- **Tạo đề thi đa dạng (`compositionMode`):**
  - **Tạo đề cố định (`FIXED`):** Chọn đích danh từng câu hỏi và tùy chỉnh số điểm/vị trí.
  - **Tạo đề ngẫu nhiên (`RANDOM_RULE`):** Tạo quy tắc tự động bốc câu hỏi ngẫu nhiên theo Chủ đề, Độ khó và Số điểm.
- **Cấu hình phòng thi nâng cao:**
  - Giới hạn thời gian (`timeLimitMinutes`), thời điểm mở/đóng đề (`availableFrom`/`availableUntil`).
  - Giới hạn số lần nộp bài (`maxAttempts`), điểm đạt (`passingScore`).
  - Cấu hình Xáo trộn câu hỏi (`shuffleQuestions`) & Xáo trộn đáp án (`shuffleOptions`) chống gian lận.

---

## 🛠 Công nghệ sử dụng

- **Java 21+** & **Spring Boot 3.x** (Spring WebMVC, Spring Data JPA, Spring Security)
- **MySQL 8.0** (Cơ sở dữ liệu chính)
- **Flyway** (Quản lý và tự động hóa cơ sở dữ liệu migrations V1 -> V7)
- **H2 Database** (Cơ sở dữ liệu in-memory cho Unit/Integration Testing)
- **JJWT** (Xác thực và phân quyền qua Stateless JSON Web Token)
- **Lombok** (Giảm thiểu boilerplate code)
- **Maven** (Quản lý dependencies và build tool)
- **Graphify** (Đồ thị tri thức mã nguồn hỗ trợ truy vấn kiến trúc dự án)

---

## 🚀 Hướng dẫn chạy ứng dụng

### 1. Yêu cầu hệ thống
- JDK 21 trở lên
- MySQL Server 8.0 trở lên
- Maven 3.8+ (hoặc dùng wrapper `./mvnw`)

### 2. Cấu hình cơ sở dữ liệu
Tạo cơ sở dữ liệu MySQL:
```sql
CREATE DATABASE IF NOT EXISTS e_learning;
```

Các biến môi trường tùy chọn (nếu chạy sản xuất):
- `MYSQL_URL=jdbc:mysql://localhost:3306/e_learning?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- `MYSQL_USER=root`
- `MYSQL_PASSWORD=<your_mysql_password>`
- `JWT_SECRET=<min_32_chars_secret_key>`

*(Ở môi trường local dev, các giá trị trên tự động rơi về thông số mặc định nếu không truyền biến môi trường).*

### 3. Chạy lệnh
- **Chạy bài kiểm thử tự động (Unit & Integration tests):**
  ```bash
  ./mvnw test
  ```
- **Khởi chạy ứng dụng backend:**
  ```bash
  ./mvnw spring-boot:run
  ```
  Ứng dụng sẽ lắng nghe tại cổng `8080` (`http://localhost:8080`).

---

## 🧠 Tích hợp Graphify Knowledge Graph

Dự án tích hợp Graphify để xây dựng đồ thị tri thức cho toàn bộ mã nguồn:
- **Cập nhật đồ thị mã nguồn:** `python -m graphify update .`
- **Truy vấn mối quan hệ kiến trúc:** `python -m graphify query "<câu_hỏi_kiến_trúc>"`

---

## 📂 Cấu trúc dự án

```text
src/main/java/com/example/learning_backend/
├── auth/           # Xử lý đăng nhập, cấp phát JWT, đăng ký, quên mật khẩu
├── user/           # Quản lý người dùng, phân quyền (Role)
├── course/         # Quản lý khóa học, phần học (Section), bài học (Lesson)
├── enrollment/     # Đăng ký khóa học, theo dõi % tiến độ học tập
├── assessment/     # Ngân hàng câu hỏi, chủ đề, quy tắc ra đề & tạo đề thi
├── submission/     # (Đang phát triển) Xử lý lượt làm bài & chấm điểm bài thi
└── common/         # Entity cơ sở (BaseEntity), ExceptionHandler toàn cục
```
