package com.example.demo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Copies a large file with buffered Java IO and reports timings comparable to NIO. */
public final class BufferedIoFileDemo {

    private static final int BUFFER_SIZE = 1024 * 1024;

    private BufferedIoFileDemo() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: BufferedIoFileDemo [source] [destination]");
        }
        File source = new File(args.length > 0 ? args[0] : "data_1gb.txt");
        File destination = new File(args.length > 1 ? args[1] : "data_1gb_io_copy.txt");
        CopyTiming timing = copyWithTiming(source, destination);

        System.out.printf("Read from: %s%n", source.getAbsolutePath());
        System.out.printf("Written to: %s%n", destination.getAbsolutePath());
        System.out.printf("Copied: %,d bytes; buffer: %,d bytes%n", timing.bytes(), BUFFER_SIZE);
        System.out.printf("Read:                 %10.3f ms%n", timing.readNanos() / 1_000_000.0);
        System.out.printf("Write (before sync):  %10.3f ms%n", timing.writeNanos() / 1_000_000.0);
        System.out.printf("Sync (FileDescriptor):%10.3f ms%n", timing.syncNanos() / 1_000_000.0);
        System.out.printf("Write including sync: %10.3f ms%n",
                (timing.writeNanos() + timing.syncNanos()) / 1_000_000.0);
        System.out.printf("Total:                %10.3f ms%n", timing.totalNanos() / 1_000_000.0);
        System.out.printf("Copy throughput:      %10.2f MB/s%n",
                timing.totalNanos() == 0 ? 0.0 : timing.bytes() * 1000.0 / timing.totalNanos());
    }

    public record CopyTiming(long bytes, long readNanos, long writeNanos,
                             long syncNanos, long totalNanos) {
    }

    public static CopyTiming copyWithTiming(File source, File destination) throws IOException {
        long start = System.nanoTime();
        long copied = 0;
        long readNanos = 0;
        long writeNanos = 0;
        long syncNanos;

        try (BufferedInputStream input = new BufferedInputStream(
                     new FileInputStream(source), BUFFER_SIZE);
             FileOutputStream fileOutput = createNewOutput(destination);
             BufferedOutputStream output = new BufferedOutputStream(fileOutput, BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                long readStart = System.nanoTime();
                int bytesRead = input.read(buffer);
                readNanos += System.nanoTime() - readStart;
                if (bytesRead == -1) {
                    break;
                }

                long writeStart = System.nanoTime();
                output.write(buffer, 0, bytesRead);
                writeNanos += System.nanoTime() - writeStart;
                copied += bytesRead;
            }

            // Include flushing the Java buffer in write time before syncing to disk.
            long flushStart = System.nanoTime();
            output.flush();
            writeNanos += System.nanoTime() - flushStart;

            long syncStart = System.nanoTime();
            fileOutput.getFD().sync();
            syncNanos = System.nanoTime() - syncStart;
        }

        return new CopyTiming(copied, readNanos, writeNanos, syncNanos,
                System.nanoTime() - start);
    }

    private static FileOutputStream createNewOutput(File destination) throws IOException {
        if (!destination.createNewFile()) {
            throw new IOException("Destination already exists: " + destination);
        }
        return new FileOutputStream(destination);
    }
}
