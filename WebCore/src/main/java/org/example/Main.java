package org.example;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8081 : Integer.parseInt(args[0]);
        HttpServer server = new HttpServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }
}
