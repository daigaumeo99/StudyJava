package com.example.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/** Standalone CRUD website using the JDK HTTP server, without Spring. */
public final class CrudWebServer {
    private static final Path DATA = Path.of("data", "products.properties");
    private final TreeMap<Long, Product> products = new TreeMap<>();
    private long nextId = 1;

    private record Product(long id, String name, BigDecimal price) { }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: CrudWebServer [port]");
        }
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        CrudWebServer app = new CrudWebServer();
        app.load();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", app::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.close();
        }));
        server.start();
        System.out.println("CRUD website: http://localhost:" + port);
        System.out.println("Data file: " + DATA.toAbsolutePath());
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                    Map<String, String> query = decode(exchange.getRequestURI().getRawQuery());
                    Product editing = query.containsKey("edit") ? find(query.get("edit")) : null;
                    respond(exchange, 200, page(editing, ""));
                    return;
                }
                if (!path.equals("/products/create") && !path.equals("/products/update")
                        && !path.equals("/products/delete")) {
                    respond(exchange, 404, page(null, "Không tìm thấy trang."));
                    return;
                }
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    respond(exchange, 405, page(null, "Vui lòng dùng biểu mẫu để thao tác."));
                    return;
                }
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null || !type.split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/x-www-form-urlencoded")) {
                    respond(exchange, 415, page(null, "Định dạng biểu mẫu không hợp lệ."));
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(16_385);
                if (body.length > 16_384) {
                    respond(exchange, 413, page(null, "Biểu mẫu quá lớn."));
                    return;
                }
                Map<String, String> form = decode(new String(body, StandardCharsets.UTF_8));
                // Save the new state before publishing it in memory.
                TreeMap<Long, Product> updated = new TreeMap<>(products);
                if (path.equals("/products/delete")) {
                    updated.remove(find(form.get("id")).id());
                } else {
                    long id = path.equals("/products/create") ? nextId : find(form.get("id")).id();
                    String name = form.getOrDefault("name", "").strip();
                    if (name.isEmpty() || name.length() > 120) {
                        throw new IllegalArgumentException("Tên sản phẩm cần từ 1 đến 120 ký tự.");
                    }
                    BigDecimal price;
                    try {
                        price = new BigDecimal(form.getOrDefault("price", ""));
                        if (price.signum() < 0 || price.compareTo(new BigDecimal("1000000000000")) > 0
                                || price.stripTrailingZeros().scale() > 2) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Giá cần từ 0 đến 1.000.000.000.000, tối đa 2 số thập phân.");
                    }
                    updated.put(id, new Product(id, name, price));
                }
                long updatedNextId = path.equals("/products/create") ? nextId + 1 : nextId;
                save(updated, updatedNextId);
                products.clear();
                products.putAll(updated);
                nextId = updatedNextId;
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, page(null, e.getMessage()));
            } catch (IOException e) {
                e.printStackTrace();
                respond(exchange, 500, page(null, "Không thể đọc hoặc lưu dữ liệu. Vui lòng thử lại."));
            }
        }
    }

    private Product find(String rawId) {
        try {
            Product product = products.get(Long.parseLong(rawId));
            if (product != null) {
                return product;
            }
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Sản phẩm không tồn tại.");
    }

    private void load() throws IOException {
        if (!Files.exists(DATA)) {
            return;
        }
        Properties stored = new Properties();
        try (InputStream input = Files.newInputStream(DATA)) {
            stored.load(input);
        }
        for (String key : stored.stringPropertyNames()) {
            if (key.endsWith(".name")) {
                long id = Long.parseLong(key.substring(0, key.length() - 5));
                products.put(id, new Product(id, stored.getProperty(key),
                        new BigDecimal(stored.getProperty(id + ".price"))));
                nextId = Math.max(nextId, id + 1);
            }
        }
        nextId = Math.max(nextId, Long.parseLong(stored.getProperty("nextId", "1")));
    }

    private void save(Map<Long, Product> updated, long updatedNextId) throws IOException {
        Properties stored = new Properties();
        stored.setProperty("nextId", Long.toString(updatedNextId));
        for (Product product : updated.values()) {
            stored.setProperty(product.id() + ".name", product.name());
            stored.setProperty(product.id() + ".price", product.price().toPlainString());
        }
        Files.createDirectories(DATA.getParent());
        Path temporary = Files.createTempFile(DATA.getParent(), "products-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                stored.store(output, "CRUD products");
            }
            Files.move(temporary, DATA, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<String, String> decode(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String pair : encoded.split("&")) {
                String[] parts = pair.split("=", 2);
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String page(Product editing, String error) {
        StringBuilder rows = new StringBuilder();
        for (Product product : products.values()) {
            rows.append("""
                    <tr><td>#%d</td><td class="product-name">%s</td><td>%s ₫</td>
                    <td><div class="actions"><a class="edit" href="/?edit=%d">Sửa</a>
                    <form method="post" action="/products/delete">
                    <input type="hidden" name="id" value="%d">
                    <button class="delete" type="submit">Xóa</button></form></div></td></tr>
                    """.formatted(product.id(), escape(product.name()),
                    String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,.2f", product.price()),
                    product.id(), product.id()));
        }
        if (products.isEmpty()) {
            rows.append("<tr><td class=\"empty\" colspan=\"4\">Chưa có sản phẩm. Thêm sản phẩm đầu tiên ở biểu mẫu bên cạnh.</td></tr>");
        }
        return """
                <!doctype html>
                <html lang="vi"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Quản lý sản phẩm</title>
                <style>
                *{box-sizing:border-box}body{margin:0;background:#f4f6fa;color:#18253b;font:16px/1.6 system-ui,sans-serif}
                main{max-width:1160px;margin:auto;padding:48px 24px}.eyebrow{color:#4667d7;font-weight:700;font-size:13px;letter-spacing:2px}
                h1{font-size:34px;letter-spacing:-1px;margin:8px 0}p{color:#65748b;margin-top:0}
                .layout{display:grid;grid-template-columns:320px minmax(0,1fr);gap:24px;align-items:start;margin-top:32px}
                .card{background:white;border:1px solid #e1e7ef;border-radius:16px;padding:24px;box-shadow:0 4px 20px #172b4d05}
                h2{font-size:19px;margin:0 0 20px}label{display:block;font-size:14px;font-weight:600;margin:16px 0 6px}
                input{width:100%%;font:inherit;border:1px solid #cbd5e1;border-radius:8px;padding:10px 12px}
                input:focus{outline:2px solid #6484f5;outline-offset:2px}button,a{font:inherit}
                button{cursor:pointer;border:0;border-radius:8px;padding:10px 16px;font-weight:600}
                .primary{background:#385bd8;color:white;width:100%%;margin-top:24px}.primary:hover{background:#2847b8}
                .cancel{display:block;text-align:center;margin-top:12px;color:#52627c}a{color:#385bd8;text-decoration:none}a:hover{text-decoration:underline}
                .table-wrap{overflow:auto}table{border-collapse:collapse;width:100%%;text-align:left;white-space:nowrap}
                th{font-size:12px;color:#718096;text-transform:uppercase;letter-spacing:1px;background:#f8fafc}
                th,td{padding:14px 12px;border-bottom:1px solid #edf0f5}.product-name{font-weight:600;white-space:normal;overflow-wrap:anywhere;min-width:130px}
                .actions{display:flex;align-items:center;gap:16px}.actions form{margin:0}.delete{background:#fff0f0;color:#ba3545;padding:6px 12px}
                .empty{padding:36px 12px;color:#718096;white-space:normal;text-align:center}.error{padding:14px 18px;background:#fff0f0;color:#a62d3e;border-radius:10px;margin-top:20px}
                .count{font-size:13px;color:#65748b;font-weight:400;margin-left:10px}footer{color:#7c889c;font-size:13px;margin-top:24px}
                @media(max-width:760px){main{padding:28px 16px}.layout{grid-template-columns:1fr}h1{font-size:28px}.card{padding:20px}}
                </style></head><body><main>
                <div class="eyebrow">PRODUCT MANAGER</div><h1>Quản lý sản phẩm</h1>
                <p>Thêm sản phẩm mới, cập nhật thông tin và quản lý danh sách của bạn.</p>
                %s
                <div class="layout"><section class="card"><h2>%s</h2>
                <form method="post" action="/products/%s">
                %s
                <label for="name">Tên sản phẩm</label>
                <input id="name" name="name" required maxlength="120" placeholder="Ví dụ: Bàn phím cơ" value="%s">
                <label for="price">Giá (VNĐ)</label>
                <input id="price" name="price" type="number" required min="0" max="1000000000000" step="0.01" placeholder="0" value="%s">
                <button class="primary" type="submit">%s</button></form>%s</section>
                <section class="card"><h2>Danh sách sản phẩm <span class="count">%d sản phẩm</span></h2>
                <div class="table-wrap"><table><thead><tr><th>ID</th><th>Tên sản phẩm</th><th>Giá</th><th>Thao tác</th></tr></thead>
                <tbody>%s</tbody></table></div></section></div>
                <footer>Dữ liệu được lưu tự động sau mỗi thay đổi.</footer>
                </main></body></html>
                """.formatted(error.isEmpty() ? "" : "<div class=\"error\" role=\"alert\">" + escape(error) + "</div>",
                editing == null ? "Thêm sản phẩm" : "Sửa sản phẩm #" + editing.id(),
                editing == null ? "create" : "update",
                editing == null ? "" : "<input type=\"hidden\" name=\"id\" value=\"" + editing.id() + "\">",
                editing == null ? "" : escape(editing.name()),
                editing == null ? "" : editing.price().toPlainString(),
                editing == null ? "Thêm sản phẩm" : "Lưu thay đổi",
                editing == null ? "" : "<a class=\"cancel\" href=\"/\">Hủy chỉnh sửa</a>",
                products.size(), rows);
    }
}
