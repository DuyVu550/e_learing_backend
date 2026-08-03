# E-Learning Backend

Hệ thống quản lý học tập trực tuyến (E-Learning Backend) được xây dựng trên nền tảng Java 21 và Spring Boot.

## Tiến độ khối nghiệp vụ (e_learning.md)

### 1. Khối Nghiệp vụ Người dùng & Phân quyền (Authentication & Authorization) - Đã hoàn thành 100%
- Đăng ký tài khoản (Register): API POST /api/auth/register — nhận thêm `role` tùy chọn (`STUDENT` hoặc `INSTRUCTOR`) để đăng ký với tư cách học viên hay giảng viên. Bỏ trống thì mặc định `STUDENT`, nên client cũ không bị ảnh hưởng.
- Đăng nhập (Login): API POST /api/auth/login
- Đăng xuất (Logout): API POST /api/auth/logout
- Làm mới Token (Refresh Token): API POST /api/auth/refresh
- Đổi mật khẩu (Change Password): API POST /api/auth/change-password
- Đổi vai trò (Change Role): API POST /api/auth/change-role — người dùng tự chuyển giữa `STUDENT` và `INSTRUCTOR`, trả về cặp token mới đã mang role mới.
- Quên mật khẩu (Forgot Password): API POST /api/auth/forgot-password
- Đặt lại mật khẩu (Reset Password): API POST /api/auth/reset-password
- Phân quyền người dùng (RBAC): Mã hóa mật khẩu bằng BCrypt, phân quyền role STUDENT, INSTRUCTOR, ADMIN qua Spring Security và JWT.
- `ADMIN` không thể tự cấp qua API: `AssignableRole` chỉ gồm `STUDENT`/`INSTRUCTOR`, gửi `"role":"ADMIN"` bị trả 400 kèm danh sách giá trị hợp lệ. Tài khoản admin duy nhất được seed lúc khởi động từ `app.admin.email` / `app.admin.password` (mặc định dev `admin@learning.local` / `Admin@12345` — **đổi trước khi deploy**). Seed chỉ tạo khi chưa có, nên restart không reset mật khẩu admin.
- Phân quyền đọc từ database mỗi request (không tin claim trong JWT), nên hạ quyền có hiệu lực ngay với token cũ — đã kiểm chứng: sau khi chuyển về `STUDENT`, token cũ tạo khóa học bị 403.

### 2. Khối Nghiệp vụ Quản lý Khóa học & Nội dung (Course & Content Management) - Đã hoàn thành 100%
- Cấu trúc khóa học phân cấp: Course -> CourseSection -> Lesson.
- Đa dạng loại bài học: Hỗ trợ Video (`videoUrl`), Document (`documentUrl` Text/PDF/Markdown) và `LessonContentType`.
- Theo dõi tiến độ học tập: Đánh dấu bài học hoàn thành (`LessonProgressStatus.COMPLETED`), lưu thời gian xem dở (`lastPositionSeconds`), tính % tiến độ khóa học theo thời gian thực và tự động cấp trạng thái hoàn thành khóa học khi đạt 100%.

### 3. Khối Nghiệp vụ Quản lý Đề thi & Ngân hàng câu hỏi (Quiz & Exam Engine) - Đã hoàn thành 100%
- Ngân hàng câu hỏi (Question Bank): Quản lý theo Chủ đề (`QuestionTopic`) & Độ khó (`EASY`, `MEDIUM`, `HARD`). Hỗ trợ 5 dạng câu hỏi: Single Choice, Multiple Choice, True/False, Fill in Blank, Short Answer.
- Tạo đề thi (Exam Creation): Hỗ trợ đề cố định (`FIXED`) và đề ngẫu nhiên theo quy tắc (`RANDOM_RULE`).
- Cấu hình phòng thi (Exam Settings): Thời gian làm bài, mốc thời gian mở/đóng đề, số lần nộp bài tối đa, điểm đạt, xáo trộn câu hỏi & xáo trộn đáp án chống gian lận.

### 4. Khối Nghiệp vụ Làm bài & Chấm điểm (Taking Exam & Grading) - Đã hoàn thành 100%
- Luồng làm bài: Bắt đầu / tiếp tục lượt thi (`startOrResume`) với kiểm tra ghi danh, trạng thái `PUBLISHED`, khung giờ mở đề và số lần làm bài còn lại. Thứ tự câu hỏi & đáp án xáo trộn ổn định theo `attemptId` nên F5 hay vào lại không bị đảo lộn.
- Lưu nháp tự động (Auto-Save Draft) qua `PUT /api/attempts/{attemptId}/answers`, kèm đánh dấu câu hỏi (Bookmark/Flag) trong cùng payload.
- Hết giờ tự động nộp bài (Auto-submit): Hạn nộp = `min(startedAt + timeLimitMinutes, availableUntil)`, được kiểm tra mỗi lần chạm vào lượt thi nên không cần job nền.
- Chấm tự động: Trắc nghiệm 1 đáp án / Đúng-Sai chấm trọn điểm; Trắc nghiệm nhiều đáp án chấm **điểm từng phần** (`(số đúng − số sai) / tổng số đáp án đúng`, không âm); Điền vào chỗ trống so khớp `expectedAnswer` bỏ qua hoa thường và khoảng trắng thừa.
- Chấm thủ công: Câu tự luận (`SHORT_ANSWER`) giữ lượt thi ở trạng thái `SUBMITTED` cho tới khi giảng viên chấm điểm + để lại nhận xét qua `PATCH /api/answers/{answerId}/grade`, sau đó lượt thi chuyển sang `GRADED`.
- Xem lại kết quả: Bảng điểm chi tiết từng câu; đáp án đúng và lời giải chỉ hiển thị khi đề bật `showAnswersAfterSubmit`. DTO dành cho học viên đang làm bài không chứa trường đáp án đúng nên không thể lộ đề.

### 5. Khối Nghiệp vụ Thống kê & Bảng xếp hạng (Analytics & Leaderboard) - Đã hoàn thành 100%
- Bảng xếp hạng theo đề thi: `GET /api/assessments/{id}/leaderboard`. Xếp theo Điểm số giảm dần, bằng điểm thì ai nộp nhanh hơn (`submittedAt − startedAt`) đứng trên. Trùng cả điểm lẫn thời gian thì đồng hạng theo chuẩn competition ranking (1, 2, 2, 4).
- Bảng xếp hạng tổng hệ thống: `GET /api/leaderboard`, cộng dồn lượt thi mới nhất của từng đề mà học viên đã làm.
- Mỗi học viên chỉ tính **lượt thi mới nhất** của một đề; chỉ tính lượt đã `GRADED` để bài còn chờ chấm tự luận không làm sai lệch thứ hạng. Payload chỉ trả `userId` + `fullName`, **không lộ email**.
- Báo cáo cho giảng viên: `GET /api/assessments/{id}/report` (chỉ ADMIN/INSTRUCTOR sở hữu khóa học). Gồm điểm trung bình, cao nhất, thấp nhất, tỷ lệ đạt và số bài còn chờ chấm.
- Phổ điểm (Score Distribution) phân loại Giỏi (≥80%), Khá (65–79%), Trung bình (50–64%), Yếu (<50%).
- Phân tích câu hỏi: xếp theo tỷ lệ làm sai giảm dần để giảng viên thấy ngay câu nào khó nhất. Trả kèm `answeredCount` nên câu chưa ai làm hiện rõ là "chưa có dữ liệu" thay vì hiểu nhầm thành 0% sai.
- Index `idx_attempts_assessment_status_score (assessment_id, status, score)` phục vụ truy vấn xếp hạng.

### 6. Khối Nghiệp vụ Mở rộng (Thanh toán, Q&A Forum & Notifications) - Đã hoàn thành
- Thanh toán trực tuyến qua cổng PayOS: `POST /api/courses/{courseId}/checkout` tạo đơn và trả `checkoutUrl` (VietQR / thẻ), `GET /api/payments/me`, `GET /api/payments/{id}`, `POST /api/payments/{id}/cancel`, và webhook công khai `POST /api/payments/payos/webhook`.
- Khóa học có `price > 0` bị khóa: `POST /api/courses/{courseId}/enroll` trả lỗi "Course requires payment" cho tới khi có đơn `PAID`. Ghi danh (`Enrollment`) chỉ được cấp bởi webhook, nên người chưa trả tiền không thể vào học bằng cách tự gọi API enroll hay quay lại `returnUrl`.
- Webhook xác thực chữ ký HMAC-SHA256 (sắp key theo alphabet của object `data`) trước khi làm gì khác; sai chữ ký thì không đổi trạng thái. Chống gửi lặp: webhook thứ hai cho đơn đã `PAID` không tạo ghi danh hay thông báo trùng. Trả tiền thiếu bị đánh `FAILED` thay vì mở khóa học.
- Số tiền lấy từ `courses.price` phía server và chốt vào `payments.amount` lúc tạo đơn, nên client không tự khai giá và đổi giá về sau không viết lại doanh thu cũ.
- Báo cáo doanh thu cho Admin: `GET /api/reports/revenue?from=&to=` — tổng thu, số đơn, giá trị đơn trung bình và chi tiết theo từng khóa học. Chỉ `ROLE_ADMIN` (giảng viên bị 403).
- Khóa PayOS (`app.payos.client-id`, `app.payos.api-key`, `app.payos.checksum-key`) đặt trong `src/main/resources/application-local.properties` — file này đã được gitignore và được nạp qua `spring.config.import=optional:...`. Thiếu khóa thì app vẫn khởi động, chỉ checkout báo lỗi.
- Diễn đàn Hỏi - Đáp dưới mỗi bài học: `POST|GET /api/lessons/{lessonId}/comments`, `PATCH|DELETE /api/comments/{commentId}`. Phân luồng 1 cấp (hỏi → trả lời), không cho trả lời lồng nhau; xóa câu hỏi thì xóa luôn các trả lời.
- Quyền: học viên đã ghi danh và người quản lý khóa học được bình luận; chỉ tác giả được sửa; tác giả hoặc người quản lý khóa học được xóa.
- Thông báo trong ứng dụng: `GET /api/notifications/me` (kèm `?unreadOnly=true`), `GET /api/notifications/me/unread-count`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/me/read-all`.
- Sinh thông báo tự động cho 5 sự kiện: chấm xong bài tự luận (báo học viên), mở đề thi mới (báo toàn bộ học viên đang ghi danh), có người trả lời bình luận (báo tác giả), có câu hỏi mới trong bài học (báo giảng viên), và thanh toán thành công (báo người mua). Không bao giờ tự báo cho chính người vừa thao tác.
- Thông báo được ghi trong cùng transaction với hành động sinh ra nó, nên không thể tồn tại thông báo cho một thao tác đã rollback.
- Chưa làm: **gửi thông báo qua Email** (chưa có SMTP; hệ thống mới chỉ lưu và trả thông báo qua API). Nhắc "sắp hết hạn nộp bài" cũng chưa làm vì cần job nền định kỳ, trong khi Module 4 cố ý không dùng scheduler. Đối soát đơn treo (`PENDING` mà webhook không bao giờ tới) cũng chưa có vì cần scheduler.


---

## Công nghệ sử dụng

- Java 21+
- Spring Boot (Spring WebMVC, Spring Data JPA, Spring Security)
- MySQL 8.0 (Cơ sở dữ liệu chính)
- Flyway (Quản lý và tự động hóa cơ sở dữ liệu)
- H2 Database (Cơ sở dữ liệu in-memory phục vụ kiểm thử)
- JJWT (Xác thực và phân quyền qua JSON Web Token)
- Lombok (Giảm thiểu mã lặp)
- Maven (Quản lý phụ thuộc và xây dựng dự án)
- Graphify (Đồ thị tri thức mã nguồn hỗ trợ truy vấn kiến trúc dự án)

## Yêu cầu hệ thống

- JDK 21 trở lên
- MySQL Server 8.0 trở lên
- Maven 3.8+ (hoặc sử dụng wrapper ./mvnw)

## Cấu hình cơ sở dữ liệu và bảo mật

Tạo cơ sở dữ liệu MySQL:

```sql
CREATE DATABASE IF NOT EXISTS e_learning;
```

Thiết lập các biến môi trường cấu hình (khuyên dùng cho môi trường sản xuất):

- MYSQL_URL=jdbc:mysql://localhost:3306/e_learning?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
- MYSQL_USER=root
- MYSQL_PASSWORD=<mat_khau_mysql>
- JWT_SECRET=<chuoi_bi_mat_jwt_do_dai_toi_thieu_32_ky_tu>

Mặc định khi chạy môi trường phát triển local, các giá trị trên sẽ tự động rơi về thông số mặc định nếu biến môi trường chưa được thiết lập.

## Hướng dẫn chạy ứng dụng

1. Chạy bài kiểm thử tự động:
   ./mvnw test

2. Khởi chạy ứng dụng:
   ./mvnw spring-boot:run

Ứng dụng sẽ hoạt động tại cổng 8080 (http://localhost:8080).

## Tích hợp Graphify

Dự án sử dụng Graphify để tạo đồ thị tri thức mã nguồn (Knowledge Graph), giúp tối ưu hóa việc định vị file và hiểu kiến trúc dự án:

1. Xây dựng đồ thị mã nguồn:
   graphify .

2. Truy vấn mối quan hệ giữa các thành phần:
   graphify query "<cau_hoi_kien_truc>"

3. Cập nhật đồ thị sau khi thay đổi mã nguồn lớn:
   graphify --update

Tất cả dữ liệu đầu ra của Graphify được tự động loại trừ khỏi kho chứa Git thông qua tập tin .gitignore.

## Cấu trúc dự án

- src/main/java/com/example/learning_backend:
  - user: Quản lý người dùng và phân quyền.
  - course: Quản lý khóa học, phần học, bài học.
  - assessment: Quản lý bài kiểm tra, câu hỏi, lựa chọn đáp án.
  - enrollment: Quản lý đăng ký học và tiến độ bài học.
  - submission: Quản lý làm bài và chấm điểm.
  - analytics: Bảng xếp hạng và báo cáo thống kê đề thi.
  - payment: Tích hợp thanh toán PayOS và xử lý ghi danh tự động sau khi thanh toán.
  - forum: Hỏi đáp / bình luận dưới bài học.
  - notification: Thông báo trong ứng dụng.
  - auth: Xử lý xác thực và quản lý token.
  - common: Các thành phần dùng chung (BaseEntity, ExceptionHandler).
- src/main/resources/db/migration: Các tập tin Flyway SQL migration.
