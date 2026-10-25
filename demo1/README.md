# demo1 — Thực hành Java Core

Dự án gồm website quản lý sản phẩm và các ví dụ sao chép file bằng Java IO/NIO. Mã nguồn sử dụng thư viện có sẵn trong JDK, không cần Spring hoặc thư viện bên ngoài.

## Yêu cầu

- JDK 21, với `java` và `javac` có trong `PATH`.
- Gradle Wrapper có sẵn trong dự án; lần chạy đầu cần mạng để tải Gradle.

Các lệnh bên dưới dùng PowerShell và được chạy từ thư mục `demo1`:

```powershell
cd demo1
java -version
javac -version
```

## Chạy website CRUD

```powershell
.\gradlew.bat run
```

Mở <http://localhost:8080> để xem, thêm, sửa và xóa sản phẩm. Nhấn `Ctrl+C` để dừng server.

Đổi cổng khi cần:

```powershell
.\gradlew.bat run --args="8081"
```

Ứng dụng lưu dữ liệu tại `data/products.properties`, tính từ thư mục chạy, và nạp lại khi khởi động. Server chỉ lắng nghe trên `localhost` và chưa có đăng nhập.

### Chạy trực tiếp bằng JDK

Nếu không dùng Gradle, biên dịch và chạy bằng các lệnh sau:

```powershell
New-Item -ItemType Directory -Force build/core-classes | Out-Null
javac -encoding UTF-8 -d build/core-classes src/main/java/com/example/demo/*.java
java -cp build/core-classes com.example.demo.Demo1Application
```

Để đổi cổng, thêm số cổng vào cuối lệnh `java`, ví dụ `8081`.

### Đóng gói JAR

```powershell
.\gradlew.bat build
java -jar build/libs/demo1-0.0.1-SNAPSHOT.jar
```

Chi tiết các đường dẫn HTTP và cách chạy riêng server: [CRUD-README.md](CRUD-README.md).

## Ví dụ sao chép file bằng IO và NIO

| Lớp | Cách thực hiện | File đích mặc định |
| --- | --- | --- |
| `NioFileDemo` | `FileChannel` và `ByteBuffer.allocateDirect` | `data_1gb_copy.txt` |
| `BufferedIoFileDemo` | `BufferedInputStream` và `BufferedOutputStream` | `data_1gb_io_copy.txt` |

Cả hai dùng buffer 1 MiB, báo số byte đã chép, thời gian đọc, ghi, đồng bộ xuống đĩa và tốc độ sao chép. Thời gian thực tế phụ thuộc thiết bị lưu trữ và bộ nhớ đệm của hệ điều hành.

Sau khi biên dịch bằng lệnh `javac` ở trên, có thể thử với file nhỏ:

```powershell
Set-Content -Encoding UTF8 sample-input.txt "Thuc hanh sao chep file Java"
java -cp build/core-classes com.example.demo.NioFileDemo sample-input.txt sample-nio-copy.txt
java -cp build/core-classes com.example.demo.BufferedIoFileDemo sample-input.txt sample-io-copy.txt
```

Hai tham số lần lượt là file nguồn và file đích. File nguồn phải tồn tại; file đích phải chưa tồn tại. Khi chạy lại, chọn tên file đích mới.

Nếu không truyền tham số, cả hai đọc `data_1gb.txt` trong thư mục chạy. Các file `data_1gb*.txt` được Git bỏ qua và không có trong repository; bạn cần tự chuẩn bị file nguồn để thử với dữ liệu lớn.

## Cấu trúc chính

```text
demo1/
├── build.gradle
├── CRUD-README.md
├── data/products.properties
├── gradle/wrapper/
└── src/main/java/com/example/demo/
    ├── Demo1Application.java   # Entry point của website
    ├── CrudWebServer.java      # HTTP server, giao diện và lưu dữ liệu
    ├── NioFileDemo.java        # Sao chép file bằng NIO
    └── BufferedIoFileDemo.java # Sao chép file bằng buffered IO
```
