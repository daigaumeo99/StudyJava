package com.example.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Copies a large text file using a fixed-size NIO buffer. */
public final class NioFileDemo {

    private static final int BUFFER_SIZE = 1024 * 1024;

    private NioFileDemo() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: NioFileDemo [source] [destination]");
        }

        Path source = Path.of(args.length > 0 ? args[0] : "data_1gb.txt");
        Path destination = Path.of(args.length > 1 ? args[1] : "data_1gb_copy.txt");
        CopyTiming timing = copyWithTiming(source, destination);

        System.out.printf("Read from: %s%n", source.toAbsolutePath());
        System.out.printf("Written to: %s%n", destination.toAbsolutePath());
        System.out.printf("Copied: %,d bytes; buffer: %,d bytes%n", timing.bytes(), BUFFER_SIZE);
        System.out.printf("Read:                 %10.3f ms%n", timing.readNanos() / 1_000_000.0);
        System.out.printf("Write (before sync):  %10.3f ms%n", timing.writeNanos() / 1_000_000.0);
        System.out.printf("Sync (force):         %10.3f ms%n", timing.syncNanos() / 1_000_000.0);
        System.out.printf("Write including sync: %10.3f ms%n",
                (timing.writeNanos() + timing.syncNanos()) / 1_000_000.0);
        System.out.printf("Total:                %10.3f ms%n", timing.totalNanos() / 1_000_000.0);
        System.out.printf("Copy throughput:      %10.2f MB/s%n",
                timing.totalNanos() == 0 ? 0.0 : timing.bytes() * 1000.0 / timing.totalNanos());
    }

    public static long copy(Path source, Path destination) throws IOException {
        return copyWithTiming(source, destination).bytes();
    }

    public record CopyTiming(long bytes, long readNanos, long writeNanos,
                             long syncNanos, long totalNanos) {
    }

    public static CopyTiming copyWithTiming(Path source, Path destination) throws IOException {
        long start = System.nanoTime();
        long copied = 0;
        long readNanos = 0;
        long writeNanos = 0;
        long syncNanos;
        // CREATE_NEW prevents overwriting an existing file, including the source.
        try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(destination,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            while (true) {
                long readStart = System.nanoTime();
                int bytesRead = input.read(buffer);
                readNanos += System.nanoTime() - readStart;
                if (bytesRead == -1) {
                    break;
                }
                buffer.flip();
                // A single write may consume only part of the buffer.
                long writeStart = System.nanoTime();
                while (buffer.hasRemaining()) {
                    copied += output.write(buffer);
                }
                writeNanos += System.nanoTime() - writeStart;
                buffer.clear();
            }

            long syncStart = System.nanoTime();
            output.force(true);
            syncNanos = System.nanoTime() - syncStart;
        }
        // Total includes opening/closing channels, buffer allocation and loop overhead.
        return new CopyTiming(copied, readNanos, writeNanos, syncNanos,
                System.nanoTime() - start);
    }
}
