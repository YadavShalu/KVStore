# KVStore — In-Memory Key-Value Store

A Redis-like in-memory key-value store built from scratch in Java.

Supports **GET / SET / DEL**, **TTL expiry**, **Pub/Sub messaging**,  
**LRU eviction**, and **AOF persistence** over a raw TCP socket using  
the **RESP protocol** (compatible with Redis clients like `redis-cli`).

---

## 🏗️ Architecture

```
Client (TCP / redis-cli)
        │
        ▼
ServerSocket :6380
        │
        ▼
ClientHandler (one thread per client)
        │
        ├──▶ DataStore       (ConcurrentHashMap)     — O(1) GET/SET/DEL
        ├──▶ TTLManager      (PriorityQueue)         — min-heap expiry
        ├──▶ LRUCache        (LinkedHashMap)         — eviction policy
        ├──▶ PubSubManager   (CopyOnWriteArrayList)  — channels
        └──▶ AOFWriter       (FileWriter)            — persistence
```

---

## ⚡ Features

| Feature | Implementation | Complexity |
|--------|---------------|------------|
| GET / SET / DEL | ConcurrentHashMap | O(1) |
| TTL / EXPIRE | Min-heap (PriorityQueue) + background cleanup thread | O(log n) |
| LRU Eviction | LinkedHashMap (`accessOrder=true`) | O(1) |
| Pub/Sub | Observer pattern (CopyOnWriteArrayList) | O(n subscribers) |
| Persistence | Append-Only File (AOF), replay on restart | O(n commands) |
| Protocol | RESP over raw TCP (redis-cli compatible) | — |
| Concurrency | One thread per client, synchronized heap access | — |
|CLI | Built-in interactive console client | — |

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Clone
```bash
git clone https://github.com/YadavShalu/KVStore.git
cd kvstore
```

### Build
```bash
mvn package
```

### Run
```bash
java -jar target/kvstore-1.0-SNAPSHOT.jar
```

Server starts on **port 6380** by default.

---

## Connecting to the Server

### Option 1 - KVStore CLI (Built-in)

```bash
java -cp target/kvstore-1.0-SNAPSHOT.jar com.kvstore.CLI
```

Connect to a remote server:
```bash
java -cp target/kvstore-1.0-SNAPSHOT.jar com.kvstore.CLI -h YOUR_SERVER_IP -p 6380
```

CLI options:
| Flag | Default | Description |
|---|---|---|
| `-h <host>` | localhost | Server host |
| `-p <port>` | 6380 | Server port |
| `--help` | — | Show help |
| `--version` | — | Show version |


### Option 2 - Connect using redis-cli
```bash
redis-cli -p 6380
```

## CLI Demo

| |/ /\ \   / / | |                  / | |    |   |
| ' /  \ _/ /|  | | | ___  _ __ ___ | |    | |      | |
|  <    \   / | || / _ | '/ _ | |    | |      | |
| . \    | |  |_____|____/||  _/ |||__|  ||
||_\   |_|
KVStore CLI v1.0.0 — Type 'help' for commands, 'exit' to quit
Connected to localhost:6380
localhost:6380> PING
PONG
localhost:6380> SET name Alice
OK
localhost:6380> GET name
"Alice"
localhost:6380> EXPIRE name 10
(integer) 1
localhost:6380> DEL name
(integer) 1
localhost:6380> GET name
(nil)
localhost:6380> SET message "hello world"
OK
localhost:6380> GET message
"hello world"
localhost:6380> exit
Bye!

---

## 🧪 Supported Commands

| Command | Example | Response |
|--------|--------|----------|
| PING | `PING` | `+PONG` |
| SET | `SET name Alice` | `+OK` |
| GET | `GET name` | `+Alice` |
| DEL | `DEL name` | `:1` |
| EXPIRE | `EXPIRE name 30` | `+OK` |
| SUBSCRIBE | `SUBSCRIBE news` | blocks |
| PUBLISH | `PUBLISH news "hello"` | `:1` |

### Example Session
```text
127.0.0.1:6380> PING
+PONG
127.0.0.1:6380> SET name Alice
+OK
127.0.0.1:6380> GET name
+Alice
127.0.0.1:6380> EXPIRE name 10
+OK
127.0.0.1:6380> DEL name
:1
127.0.0.1:6380> GET name
$-1
```

---

## 🧪 Running Tests

```bash
mvn test
```

### Test Coverage

| Test | Type | Description |
|------|------|------------|
| testSetAndGet | Unit | Basic SET and GET |
| testGetNonExistentKey | Unit | Missing key returns null |
| testDelete | Unit | DEL removes key |
| testOverwrite | Unit | SET overwrites value |
| testTTLExpiry | Unit | TTL expiration |
| testMultipleKeys | Unit | Multiple keys handling |
| testTCPPing | Integration | PING over TCP |
| testTCPSetAndGet | Integration | SET/GET over TCP |
| testTCPDelete | Integration | DEL over TCP |
| testTCPUnknownCommand | Integration | Error handling |
| testTCPMultipleClients | Integration | Concurrent clients |
| testPubSub | Integration | Message delivery |
| testAOFLogsAndReplays | Integration | Persistence across restart |

**Total:** 13 tests · 0 failures

---

## 📊 Benchmark Results

Run on **AWS EC2 t2.micro** (1 vCPU, 1GB RAM, Ubuntu 22.04)  
Configuration: **50 concurrent clients · 10,000 requests · persistent connections**

| Metric | SET | GET |
|--------|-----|-----|
| Throughput | 11,198 ops/sec | 33,898 ops/sec |
| Avg latency | 3.45 ms | 0.98 ms |
| Min latency | 0.04 ms | 0.03 ms |
| p50 latency | 1.43 ms | 0.58 ms |
| p99 latency | 22.92 ms | 6.07 ms |
| p999 latency | 145.94 ms | 83.31 ms |
| Max latency | 169.34 ms | 95.86 ms |
| Errors | 0 / 10,000 | 0 / 10,000 |

### Notes
- **GET is faster**: purely in-memory (`ConcurrentHashMap`)
- **SET is slower**: includes disk I/O (AOF write)
- **p999 spikes**: due to burstable CPU limits on t2.micro

### Reproduce
```bash
java -cp kvstore-1.0-SNAPSHOT.jar com.kvstore.BenchmarkRunner
```

---

## 📁 Project Structure

```
src/
├── main/java/com/kvstore/
│   ├── Server.java 
│   ├── ClientHandler.java  
|   ├── CLI.java  
│   ├── BenchmarkRunner.java
│   ├── store/
│   │   ├── DataStore.java
│   │   ├── TTLManager.java
│   │   └── LRUCache.java
│   ├── pubsub/
│   │   └── PubSubManager.java
│   └── persistence/
│       └── AOFWriter.java
└── test/java/com/kvstore/
    └── KVStoreTest.java
```

---

## 🧠 Design Decisions

**One thread per client**  
Simple and easy to debug. Trades scalability for simplicity.

**ConcurrentHashMap**  
Allows concurrent reads/writes without global locking.

**AOF persistence**  
Safer than snapshots — minimal data loss on crash.

**LinkedHashMap for LRU**  
Built-in ordering with `accessOrder=true` enables efficient eviction.

---

## 🚀 Deployment

Deployed on **AWS EC2 (t3.micro, Ubuntu 22.04)**  
Managed via **systemd service**.

```bash
# Check status
sudo systemctl status kvstore

# View logs
journalctl -u kvstore -f

# Restart
sudo systemctl restart kvstore
```

