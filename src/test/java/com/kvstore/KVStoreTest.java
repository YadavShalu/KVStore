package com.kvstore;

import com.kvstore.store.DataStore;
import com.kvstore.store.TTLManager;
import com.kvstore.persistence.AOFWriter;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KVStoreTest {

    // -------------------------------------------------------
    // Helper — sends RESP command over real TCP socket
    // -------------------------------------------------------
    private String sendCommand(String host, int port, String... args)
            throws IOException {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

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
    }

    // -------------------------------------------------------
    // Unit Tests — DataStore directly
    // -------------------------------------------------------
    @Test @Order(1)
    void testSetAndGet() {
        DataStore.set("name", "Alice");
        assertEquals("+Alice", DataStore.get("name"));
        System.out.println("PASS: SET and GET");
    }

    @Test @Order(2)
    void testGetNonExistentKey() {
        assertEquals("$-1", DataStore.get("ghost"));
        System.out.println("PASS: GET non-existent key returns nil");
    }

    @Test @Order(3)
    void testDelete() {
        DataStore.set("city", "Delhi");
        DataStore.del("city");
        assertEquals("$-1", DataStore.get("city"));
        System.out.println("PASS: DEL removes key");
    }

    @Test @Order(4)
    void testOverwrite() {
        DataStore.set("name", "Alice");
        DataStore.set("name", "Bob");
        assertEquals("+Bob", DataStore.get("name"));
        System.out.println("PASS: SET overwrites existing key");
    }

    @Test @Order(5)
    void testTTLExpiry() throws InterruptedException {
        DataStore.set("temp", "value");
        TTLManager.setExpiry("temp", 1);
        assertEquals("+value", DataStore.get("temp"));
        new CountDownLatch(1).await(2, TimeUnit.SECONDS);
        assertEquals("$-1", DataStore.get("temp"));
        System.out.println("PASS: TTL expiry works");
    }

    @Test @Order(6)
    void testMultipleKeys() {
        DataStore.set("k1", "v1");
        DataStore.set("k2", "v2");
        DataStore.set("k3", "v3");
        assertEquals("+v1", DataStore.get("k1"));
        assertEquals("+v2", DataStore.get("k2"));
        assertEquals("+v3", DataStore.get("k3"));
        System.out.println("PASS: Multiple keys stored correctly");
    }

    // -------------------------------------------------------
    // TCP Integration Tests — real socket connection
    // -------------------------------------------------------
    static Thread serverThread;
    static int testPort = 6399; // separate port just for tests
    static CountDownLatch serverReady = new CountDownLatch(1);

    @BeforeAll
    static void startServer() throws InterruptedException {
        // Start server in background thread for TCP tests
        serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(testPort)) {
                serverReady.countDown(); // signal server is ready
                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = ss.accept();
                    new Thread(new ClientHandler(client)).start();
                }
            } catch (IOException e) {
                // server stopped
            }
        });
        serverThread.setDaemon(true); // dies when tests finish
        serverThread.start();
        serverReady.await(); // wait for server to be ready
        System.out.println("Test server started on port " + testPort);
    }

    @Test @Order(7)
    void testTCPPing() throws IOException {
        String response = sendCommand("localhost", testPort, "PING");
        assertEquals("+PONG", response);
        System.out.println("PASS: TCP PING → " + response);
    }

    @Test @Order(8)
    void testTCPSetAndGet() throws IOException {
        sendCommand("localhost", testPort, "SET", "tcpkey", "tcpvalue");
        String response = sendCommand("localhost", testPort, "GET", "tcpkey");
        assertEquals("+tcpvalue", response);
        System.out.println("PASS: TCP SET + GET → " + response);
    }

    @Test @Order(9)
    void testTCPDelete() throws IOException {
        sendCommand("localhost", testPort, "SET", "delkey", "temp");
        sendCommand("localhost", testPort, "DEL", "delkey");
        String response = sendCommand("localhost", testPort, "GET", "delkey");
        assertEquals("$-1", response);
        System.out.println("PASS: TCP DEL → " + response);
    }

    @Test @Order(10)
    void testTCPUnknownCommand() throws IOException {
        String response = sendCommand("localhost", testPort, "BLAH", "test");
        assertTrue(response.startsWith("-ERR"));
        System.out.println("PASS: TCP unknown command → " + response);
    }

    @Test @Order(11)
    void testTCPMultipleClients() throws Exception {
        // 3 clients connecting simultaneously
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Future<String> c1 = pool.submit(() ->
                sendCommand("localhost", testPort, "SET", "client1", "AAA"));
        Future<String> c2 = pool.submit(() ->
                sendCommand("localhost", testPort, "SET", "client2", "BBB"));
        Future<String> c3 = pool.submit(() ->
                sendCommand("localhost", testPort, "SET", "client3", "CCC"));

        assertEquals("+OK", c1.get());
        assertEquals("+OK", c2.get());
        assertEquals("+OK", c3.get());
        pool.shutdown();
        System.out.println("PASS: Multiple simultaneous TCP clients");
    }

    // -------------------------------------------------------
    // Pub/Sub Test
    // -------------------------------------------------------
    private String readRESPMessage(BufferedReader in) throws IOException {
    String line = in.readLine();

    // Expect array
    if (line == null || !line.startsWith("*")) return null;

    int count = Integer.parseInt(line.substring(1));

    String[] parts = new String[count];

    for (int i = 0; i < count; i++) {
        String lenLine = in.readLine(); // $len
        int len = Integer.parseInt(lenLine.substring(1));

        char[] buffer = new char[len];
        in.read(buffer, 0, len);

        parts[i] = new String(buffer);

        in.readLine(); // consume \r\n
    }

    // For pub/sub → message is always last element
    return parts[count - 1];
}
    @Test @Order(12)
    void testPubSub() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        // Subscriber thread — connects and waits for a message
        Thread subscriber = new Thread(() -> {
            try (Socket socket = new Socket("localhost", testPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                // Send SUBSCRIBE command
                out.print("*2\r\n$9\r\nSUBSCRIBE\r\n$4\r\nnews\r\n");
                out.flush();

                // Read incoming message (blocks until publisher sends)
                socket.setSoTimeout(5000); // 5 second timeout
                String msg = readRESPMessage(in);
                if (msg != null) {
                    received.offer(msg);
                }
            } catch (IOException e) {
                received.offer("ERROR: " + e.getMessage());
            }
        });

        subscriber.setDaemon(true);
        subscriber.start();
         new CountDownLatch(1).await(1, TimeUnit.SECONDS); // let subscriber connect first

        // Publisher sends a message
        sendCommand("localhost", testPort, "PUBLISH", "news", "HelloWorld");

        // Wait up to 3 seconds for subscriber to receive it
        String message = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(message, "Subscriber never received a message");
        assertEquals("HelloWorld", message);
        System.out.println("PASS: Pub/Sub → subscriber received: " + message);
    }

    // -------------------------------------------------------
    // AOF Persistence Test
    // -------------------------------------------------------
    @Test @Order(13)
    void testAOFLogsAndReplays() throws IOException {
        // Clean slate — delete old AOF file
        File aof = new File("appendonly.aof");
        if (aof.exists()) aof.delete();

        // Write some keys (AOFWriter.log is called inside DataStore.set)
        DataStore.set("persist1", "AAA");
        DataStore.set("persist2", "BBB");
        DataStore.set("persist3", "CCC");

        // Verify AOF file was created
        assertTrue(aof.exists(), "AOF file should exist after SET commands");

        // Simulate restart — clear in-memory store
        DataStore.del("persist1");
        DataStore.del("persist2");
        DataStore.del("persist3");
        assertEquals("$-1", DataStore.get("persist1")); // gone from memory

        // Replay AOF — restore state
        AOFWriter.replay();

        // Keys should be back
        assertEquals("+AAA", DataStore.get("persist1"));
        assertEquals("+BBB", DataStore.get("persist2"));
        assertEquals("+CCC", DataStore.get("persist3"));

        System.out.println("PASS: AOF logged and replayed successfully");

        // Cleanup
        aof.delete();
    }
}