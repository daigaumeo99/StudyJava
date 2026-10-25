package org.example;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Runs without test libraries: java -cp <classes> org.example.ServerSmokeTest */
public final class ServerSmokeTest {
    public static void main(String[] args) throws Exception {
        try (HttpServer server = new HttpServer(0)) {
            Thread thread = new Thread(() -> {
                try { server.start(); }
                catch (IOException e) { throw new RuntimeException(e); }
            });
            thread.setDaemon(true);
            thread.start();
            String base = "http://127.0.0.1:" + server.port();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            for (String path : new String[]{"/", "/index.html", "/style.css", "/app.js", "/api/status", "/?demo=1"}) {
                HttpResponse<String> response = send(client, base + path, "GET", "");
                require(response.statusCode() == 200, "GET " + path);
                require(!response.body().isEmpty(), "Nonempty " + path);
            }
            HttpResponse<String> home = send(client, base, "GET", "");
            require(home.body().contains("Java thuần"), "UTF-8 HTML");
            require(home.headers().firstValue("content-length").orElseThrow()
                    .equals(String.valueOf(home.body().getBytes(StandardCharsets.UTF_8).length)), "UTF-8 byte length");
            HttpResponse<String> head = send(client, base, "HEAD", "");
            require(head.statusCode() == 200 && head.body().isEmpty(), "HEAD omits body");
            String message = "Xin chào \"Java\"\\\n\t";
            HttpResponse<String> echo = send(client, base + "/api/echo", "POST", message);
            require(echo.statusCode() == 200 && echo.body().equals("{\"message\":\"Xin chào \\\"Java\\\"\\\\\\u000a\\u0009\"}"), "JSON escaping and UTF-8 POST");
            require(send(client, base + "/missing", "GET", "").statusCode() == 404, "404");
            require(send(client, base + "/../build.gradle", "GET", "").statusCode() == 404, "No filesystem access");
            require(send(client, base + "/api/status", "POST", "").statusCode() == 405, "405");
            require(send(client, base + "/api/echo", "GET", "").headers().firstValue("allow").orElseThrow().equals("POST"), "Allow header");
            require(raw(server.port(), "GET / HTTP/1.1\r\nContent-Length: -1\r\n\r\n").startsWith("HTTP/1.1 400"), "Invalid length");
            require(raw(server.port(), "POST /api/echo HTTP/1.1\r\nContent-Length: 65537\r\n\r\n").startsWith("HTTP/1.1 413"), "Body limit");
            require(raw(server.port(), "POST /api/echo HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 501"), "Unsupported chunking");
            require(raw(server.port(), "GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n").startsWith("HTTP/1.1 400"), "Duplicate headers");
            require(raw(server.port(), "GET / HTTP/1.1\r\nX-Test: " + "a".repeat(8200) + "\r\n\r\n").startsWith("HTTP/1.1 431"), "Header limit");
            var requests = new ArrayList<CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 20; i++) requests.add(client.sendAsync(HttpRequest.newBuilder(URI.create(base + "/api/status"))
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()));
            for (var request : requests) require(request.join().statusCode() == 200, "Concurrent request");
            System.out.println("PASS: assets, UTF-8, JSON, HEAD, routing, limits, malformed requests, concurrency");
        }
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String raw(int port, String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
