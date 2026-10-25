package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Minimal HTTP server using only Java SE. One request per connection. */
public final class HttpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService workers = new ThreadPoolExecutor(4, 16, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));
    private final Instant startedAt = Instant.now();

    public HttpServer(int port) throws IOException {
        listener = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
    }

    public int port() { return listener.getLocalPort(); }

    public void start() throws IOException {
        System.out.println("WebCore: http://localhost:" + port());
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                try { workers.execute(() -> handle(socket)); }
                catch (RejectedExecutionException e) { socket.close(); }
            } catch (SocketException e) {
                if (!listener.isClosed()) throw e;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            boolean head = false;
            try {
                String first = line(in);
                if (first == null) return;
                String[] request = first.split(" ", -1);
                if (request.length != 3 || !request[2].matches("HTTP/1\\.[01]"))
                    throw new HttpError(400, "Malformed request");
                String method = request[0];
                head = method.equals("HEAD");
                Map<String, String> headers = new HashMap<>();
                int total = 0;
                while (true) {
                    String header = line(in);
                    if (header == null) throw new HttpError(400, "Incomplete headers");
                    total += header.length() + 2;
                    if (total > 16384) throw new HttpError(431, "Headers too large");
                    if (header.isEmpty()) break;
                    int colon = header.indexOf(':');
                    if (colon <= 0) throw new HttpError(400, "Malformed header");
                    String name = header.substring(0, colon).toLowerCase(Locale.ROOT);
                    if (!name.matches("[!#$%&'*+.^_`|~0-9a-z-]+") || headers.containsKey(name))
                        throw new HttpError(400, "Invalid or duplicate header");
                    headers.put(name, header.substring(colon + 1).trim());
                }
                if (headers.containsKey("transfer-encoding")) throw new HttpError(501, "Transfer encoding unsupported");
                int length = 0;
                if (headers.containsKey("content-length")) {
                    String value = headers.get("content-length");
                    if (!value.matches("[0-9]+")) throw new HttpError(400, "Invalid content length");
                    try { length = Integer.parseInt(value); }
                    catch (NumberFormatException e) { throw new HttpError(413, "Body too large"); }
                }
                if (length > 65536) throw new HttpError(413, "Body too large");
                byte[] body = in.readNBytes(length);
                if (body.length != length) throw new HttpError(400, "Incomplete body");
                URI uri;
                try { uri = URI.create(request[1]); }
                catch (IllegalArgumentException e) { throw new HttpError(400, "Invalid URI"); }
                if (!request[1].startsWith("/") || uri.getRawAuthority() != null || uri.getFragment() != null)
                    throw new HttpError(400, "Expected local path");
                String path = uri.getPath();
                String resource = asset(path);
                boolean echo = path.equals("/api/echo");
                if (resource == null && !echo && !path.equals("/api/status")) throw new HttpError(404, "Page not found");
                if ((echo && !method.equals("POST")) || (!echo && !method.equals("GET") && !head)) {
                    respond(out, 405, "text/plain", bytes("Method not allowed"), head,
                            "Allow: " + (echo ? "POST" : "GET, HEAD") + "\r\n");
                } else if (echo) {
                    respond(out, 200, "application/json", bytes("{\"message\":"
                            + json(new String(body, StandardCharsets.UTF_8)) + "}"), false, "");
                } else if (path.equals("/api/status")) {
                    respond(out, 200, "application/json", bytes("{\"status\":\"online\",\"server\":\"Java Core / java.net\",\"startedAt\":"
                            + json(startedAt.toString()) + ",\"time\":" + json(Instant.now().toString()) + "}"), head, "");
                } else {
                    try (InputStream file = HttpServer.class.getResourceAsStream("/web/" + resource)) {
                        if (file == null) throw new HttpError(404, "File not found");
                        String type = resource.endsWith(".css") ? "text/css" : resource.endsWith(".js") ? "text/javascript" : "text/html";
                        respond(out, 200, type, file.readAllBytes(), head, "");
                    }
                }
            } catch (HttpError e) {
                respond(out, e.status, "text/plain", bytes(e.getMessage()), head, "");
            } catch (SocketTimeoutException e) {
                respond(out, 408, "text/plain", bytes("Request timed out"), head, "");
            }
        } catch (IOException e) {
            if (!listener.isClosed()) System.err.println("Connection ended: " + e.getMessage());
        }
    }

    // Only explicitly registered files can be served.
    private static String asset(String path) {
        return switch (path) {
            case "/", "/index.html" -> "index.html";
            case "/style.css" -> "style.css";
            case "/app.js" -> "app.js";
            default -> null;
        };
    }

    private static String line(InputStream in) throws IOException, HttpError {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = in.read();
            if (current == -1) {
                if (buffer.size() == 0) return null;
                throw new HttpError(400, "Incomplete line");
            }
            if (previous == '\r' && current == '\n') {
                byte[] data = buffer.toByteArray();
                return new String(data, 0, data.length - 1, StandardCharsets.US_ASCII);
            }
            buffer.write(current);
            if (buffer.size() > 8192) throw new HttpError(431, "Line too long");
            previous = current;
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void respond(OutputStream out, int status, String type, byte[] body,
                                boolean head, String extra) throws IOException {
        String reason = switch (status) {
            case 200 -> "OK"; case 400 -> "Bad Request"; case 404 -> "Not Found";
            case 405 -> "Method Not Allowed"; case 408 -> "Request Timeout";
            case 413 -> "Content Too Large"; case 431 -> "Request Header Fields Too Large";
            case 501 -> "Not Implemented"; default -> "Internal Server Error";
        };
        out.write(bytes("HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + type
                + "; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n"
                + "X-Content-Type-Options: nosniff\r\nCache-Control: no-store\r\n" + extra + "\r\n"));
        if (!head) out.write(body);
        out.flush();
    }

    private static String json(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> { if (c < 32) result.append(String.format("\\u%04x", (int) c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }

    @Override public void close() {
        try { listener.close(); } catch (IOException ignored) { }
        workers.shutdown();
    }

    private static final class HttpError extends Exception {
        final int status;
        HttpError(int status, String message) { super(message); this.status = status; }
    }
}
