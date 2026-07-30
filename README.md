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

## Yêu cầu hệ thống

- JDK 21 trở lên
- MySQL Server 8.0 trở lên
- Maven 3.8+ (hoặc sử dụng wrapper ./mvnw)

## Cấu hình cơ sở dữ liệu

Tạo cơ sở dữ liệu MySQL:

CREATE DATABASE IF NOT EXISTS e_learning;

Cập nhật thông tin kết nối tại file src/main/resources/application.properties nếu cần:

spring.datasource.url=jdbc:mysql://localhost:3306/e_learning?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=123456

## Hướng dẫn chạy ứng dụng

1. Chạy bài kiểm thử tự động:
   ./mvnw test

2. Khởi chạy ứng dụng:
   ./mvnw spring-boot:run

Ứng dụng sẽ hoạt động tại cổng 8080 (http://localhost:8080).

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
