# WebCore — Java Core + java.net

Website mẫu tiếng Việt với HTTP server tự viết bằng `java.net.ServerSocket` và `Socket`. Không dùng Spring, Servlet hay thư viện ngoài khi chạy ứng dụng. Yêu cầu **JDK 17 trở lên**.

## Chạy nhanh trên Windows

```powershell
.\run.ps1
# Hoặc đổi cổng:
.\run.ps1 -Port 9090
```

Mở **http://localhost:8080**. Script biên dịch và chạy trực tiếp bằng JDK, không tải Gradle. Script tìm JDK từ `JAVA_HOME`, PATH hoặc thư mục `.jdks` của IntelliJ.

## Chạy bằng IntelliJ IDEA

Chọn Project SDK là JDK 17 trở lên, sau đó chạy `org.example.Main`. Mở **http://localhost:8080**. Dừng bằng nút Stop hoặc Ctrl+C trong terminal.

## Chạy bằng Gradle (PowerShell)

```powershell
.\gradlew.bat run
# Đổi cổng nếu cần:
.\gradlew.bat run --args="9090"
# Kiểm tra HTTP qua kết nối thật:
.\gradlew.bat smokeTest
```

Đặt `JAVA_HOME` trỏ đến JDK nếu terminal chưa nhận Java. Lần đầu dùng Gradle cần mạng để tải Gradle và các dependency kiểm thử trong cấu hình dự án.

## Chạy trực tiếp, không cần Gradle

```powershell
New-Item -ItemType Directory -Force build/manual | Out-Null
javac -encoding UTF-8 -d build/manual src/main/java/org/example/*.java
Copy-Item -Recurse -Force src/main/resources/web build/manual/web
java -cp build/manual org.example.Main
```

Kiểm thử trực tiếp sau khi biên dịch:

```powershell
javac -encoding UTF-8 -d build/manual src/main/java/org/example/*.java src/test/java/org/example/ServerSmokeTest.java
java -cp build/manual org.example.ServerSmokeTest
```

## Cấu trúc

- `Main.java`: chọn cổng, khởi động và đóng server.
- `HttpServer.java`: nhận TCP, đọc HTTP, định tuyến, trả response và quản lý thread pool.
- `src/main/resources/web/`: HTML, CSS, JavaScript của giao diện.
- `ServerSmokeTest.java`: kiểm tra file tĩnh, UTF-8, JSON escaping, HEAD, lỗi HTTP, giới hạn dữ liệu và nhiều kết nối.

## API

| Phương thức | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET / HEAD | `/`, `/index.html` | Trang chủ |
| GET / HEAD | `/style.css`, `/app.js` | Tài nguyên giao diện |
| GET / HEAD | `/api/status` | Trạng thái và thời gian server, JSON |
| POST | `/api/echo` | Nhận text UTF-8, trả `{"message":"..."}` |

Ví dụ PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/status
Invoke-RestMethod http://localhost:8080/api/echo -Method Post -ContentType 'text/plain; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('Xin chào Java!'))
```

Đây là server để học và phát triển cục bộ, chỉ lắng nghe tại `127.0.0.1`. Mỗi kết nối xử lý một request rồi đóng. Giới hạn body 64 KiB, tổng header 16 KiB, mỗi dòng 8 KiB và timeout đọc 5 giây. Không hỗ trợ HTTPS, keep-alive hay request body dạng chunked. API echo không lưu dữ liệu. Chỉ các file được đăng ký trong `asset()` được phục vụ.
