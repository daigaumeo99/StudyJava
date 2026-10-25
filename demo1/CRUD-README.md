# Website CRUD Java

Toàn bộ dự án dùng Java Core/JDK 21, không có framework hoặc thư viện ngoài.
`Demo1Application` là entry point, khởi động `CrudWebServer` trên cổng 8080.

## Chạy bằng Gradle

```powershell
.\gradlew.bat run
```

Hoặc tạo JAR rồi chạy:

```powershell
.\gradlew.bat build
java -jar build/libs/demo1-0.0.1-SNAPSHOT.jar
```

## Biên dịch và chạy trực tiếp bằng JDK

```powershell
javac -encoding UTF-8 -d build/core-classes src/main/java/com/example/demo/*.java
java -cp build/core-classes com.example.demo.Demo1Application
```

Không cần Gradle để dùng cách này. Có thể thêm `8081` ở cuối lệnh chạy để đổi cổng.

## Chạy nhanh một file

Yêu cầu JDK 21 trở lên. Chạy từ thư mục gốc dự án:

```powershell
java src/main/java/com/example/demo/CrudWebServer.java
```

Nếu Java chưa được thêm vào PATH, dùng JDK có sẵn trên máy:

```powershell
& 'C:\Users\beo\.jdks\ms-21.0.12.1\bin\java.exe' src/main/java/com/example/demo/CrudWebServer.java
```

Mở http://localhost:8080. Dừng bằng Ctrl+C. Chạy riêng `CrudWebServer`, cổng 8080 cần trống.
Nếu cổng đang bận, có thể truyền cổng khác: `java src/main/java/com/example/demo/CrudWebServer.java 8081`.

- Xem danh sách: `GET /`
- Mở biểu mẫu sửa: `GET /?edit={id}`
- Thêm: `POST /products/create` (`name`, `price`)
- Sửa: `POST /products/update` (`id`, `name`, `price`)
- Xóa: `POST /products/delete` (`id`)

Các POST nhận `application/x-www-form-urlencoded`, thành công chuyển về danh sách bằng HTTP 303.

Server dùng `com.sun.net.httpserver.HttpServer`, `java.net.InetSocketAddress` và
`java.net.URLDecoder` của JDK, không cần Spring hoặc thư viện ngoài. Giao diện HTML/CSS
được render tại server. Dữ liệu lưu tại `data/products.properties` tính từ thư mục chạy,
được nạp lại khi khởi động. Trang chạy cục bộ trên `localhost`, chưa có đăng nhập.
