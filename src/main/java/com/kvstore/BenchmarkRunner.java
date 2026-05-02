package com.kvstore;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkRunner {

    static final String HOST = "localhost";
    static final int PORT = 6380;
    static final int TOTAL_REQUESTS = 10_000;
    static final int CONCURRENT_CLIENTS = 50;
    static final int REQUESTS_PER_CLIENT = TOTAL_REQUESTS / CONCURRENT_CLIENTS;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  KVStore Benchmark (persistent conns)");
        System.out.println("========================================");
        System.out.println("Total requests : " + TOTAL_REQUESTS);
        System.out.println("Clients        : " + CONCURRENT_CLIENTS);
        System.out.println("Req/client     : " + REQUESTS_PER_CLIENT);
        System.out.println("Target         : " + HOST + ":" + PORT);
        System.out.println("----------------------------------------\n");

        warmUp();

        System.out.println("Running SET benchmark...");
        BenchmarkResult setResult = run("SET");

        System.out.println("Running GET benchmark...");
        BenchmarkResult getResult = run("GET");

        printResults("SET", setResult);
        printResults("GET", getResult);

        System.out.println("\n========================================");
        System.out.println("Paste these numbers into your README!");
        System.out.println("========================================");
    }

    static void warmUp() throws Exception {
        System.out.println("Warming up (200 requests)...");
        // Use a single persistent connection for warmup
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            for (int i = 0; i < 200; i++) {
                sendOnSocket(out, in, "SET", "warmup", "val");
            }
        }
        System.out.println("Warm-up done.\n");
    }

    static BenchmarkResult run(String operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_CLIENTS);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_CLIENTS);

        long startTime = System.currentTimeMillis();

        for (int c = 0; c < CONCURRENT_CLIENTS; c++) {
            final int clientId = c;
            pool.submit(() -> {
                // Each client opens ONE connection and reuses it
                try (Socket socket = new Socket(HOST, PORT);
                     PrintWriter out = new PrintWriter(
                             socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(socket.getInputStream()))) {

                    for (int i = 0; i < REQUESTS_PER_CLIENT; i++) {
                        String key = "bench:" + clientId + ":" + i;
                        long t0 = System.nanoTime();

                        if (operation.equals("SET")) {
                            sendOnSocket(out, in, "SET", key, "value" + i);
                        } else {
                            sendOnSocket(out, in, "GET", key);
                        }

                        long t1 = System.nanoTime();
                        // Record in microseconds for more precision
                        latencies.add((t1 - t0) / 1000);
                    }

                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;
        pool.shutdown();

        return new BenchmarkResult(latencies, totalTime, errors.get());
    }

    // Send command on an EXISTING open socket — no connect/disconnect overhead
    static String sendOnSocket(PrintWriter out, BufferedReader in,
                                String... args) throws IOException {
        StringBuilder req = new StringBuilder();
        req.append("*").append(args.length).append("\r\n");
        for (String arg : args) {
            req.append("$").append(arg.length()).append("\r\n");
            req.append(arg).append("\r\n");
        }
        out.print(req);
        out.flush();
        return in.readLine();
    }

    static void printResults(String operation, BenchmarkResult r) {
        if (r.latencies.isEmpty()) {
            System.out.println("No results for " + operation);
            return;
        }

        Collections.sort(r.latencies);

        // Latencies are in microseconds — convert for display
        double avgUs  = r.latencies.stream()
                .mapToLong(Long::longValue).average().orElse(0);
        long p50Us  = percentile(r.latencies, 50);
        long p99Us  = percentile(r.latencies, 99);
        long p999Us = percentile(r.latencies, 99.9);
        long minUs  = r.latencies.get(0);
        long maxUs  = r.latencies.get(r.latencies.size() - 1);

        int successful = TOTAL_REQUESTS - r.errors;
        double throughput = successful / (r.totalTimeMs / 1000.0);

        System.out.println("\n--- " + operation + " Results ---");
        System.out.printf("Throughput   : %.0f ops/sec%n", throughput);
        System.out.printf("Avg latency  : %.2f ms%n",  avgUs  / 1000.0);
        System.out.printf("Min latency  : %.2f ms%n",  minUs  / 1000.0);
        System.out.printf("p50 latency  : %.2f ms%n",  p50Us  / 1000.0);
        System.out.printf("p99 latency  : %.2f ms%n",  p99Us  / 1000.0);
        System.out.printf("p999 latency : %.2f ms%n",  p999Us / 1000.0);
        System.out.printf("Max latency  : %.2f ms%n",  maxUs  / 1000.0);
        System.out.printf("Errors       : %d%n",       r.errors);
        System.out.printf("Total time   : %d ms%n",    r.totalTimeMs);
    }

    static long percentile(List<Long> sorted, double pct) {
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    static class BenchmarkResult {
        List<Long> latencies;
        long totalTimeMs;
        int errors;

        BenchmarkResult(List<Long> l, long t, int e) {
            latencies = l; totalTimeMs = t; errors = e;
        }
    }
}