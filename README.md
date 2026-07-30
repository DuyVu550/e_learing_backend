# E-Learning Backend

Hệ thống quản lý học tập trực tuyến (E-Learning Backend) được xây dựng trên nền tảng Java 21 và Spring Boot.

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

CREATE DATABASE IF NOT EXISTS e_learning;

Thiết lập các biến môi trường cấu hình (khuyên dùng cho môi trường sản xuất):

MYSQL_URL=jdbc:mysql://localhost:3306/e_learning?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
MYSQL_USER=root
MYSQL_PASSWORD=<mật_khẩu_mysql>
JWT_SECRET=<chuỗi_bí_mật_jwt_độ_dài_tối_thiểu_32_ký_tự>

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
   graphify query "<câu hỏi kiến trúc>"

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
  - auth: Xử lý xác thực và quản lý token.
  - common: Các thành phần dùng chung (BaseEntity, ExceptionHandler).
- src/main/resources/db/migration: Các tập tin Flyway SQL migration.
