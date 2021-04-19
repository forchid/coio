# coio
A high performance io framework based on coroutines

# Demos
## 1. Echo server
```java
public class EchoServer {

    static volatile Scheduler scheduler;

    public static void main(String[] args) {
        int port = Integer.getInteger("io.co.port", 9999);

        try (CoServerSocket server = new NioCoServerSocket()) {
            scheduler = server.getScheduler();
            server.bind(port);
            startServer(server);
            scheduler.run();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static void startServer(CoServerSocket server) {
        Scheduler scheduler = server.getScheduler();
        Coroutine serverCo = s -> {
            while (!scheduler.isShutdown()) {
                CoSocket socket = server.accept(s);
                handleConn(socket);
            }
            server.close();
        };
        scheduler.fork(serverCo, server);
    }

    static void handleConn(CoSocket socket) {
        Scheduler scheduler = socket.getScheduler();
        Coroutine connCo = c -> {
            try {
                byte[] b = new byte[512];

                while (true) {
                    int n = socket.readFully(c, b);
                    socket.write(c, b, 0, n);
                    socket.flush(c);
                    debug("flush: bytes %s", n);
                    // Business time
                    scheduler.await(c, 100);
                }
            } catch (EOFException e) {
                // Ignore
            } finally {
                socket.close();
            }
        };
        scheduler.fork(connCo, socket);
    }

    public static void shutdown() {
        scheduler.shutdown();
    }

    static {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.debug", "false");
    }

}
```
## 2. Echo client
```java
public class EchoClient {

    public static void main(String[] args) {
        int port = Integer.getInteger("io.co.port", 9999);
        final int conns, requests;
        if (args.length > 0) conns = Integer.decode(args[0]);
        else conns = 10000;
        if (args.length > 1) requests = Integer.decode(args[1]);
        else requests = 100;

        long ts = System.currentTimeMillis();
        Scheduler scheduler = new NioScheduler();
        AtomicInteger counter = new AtomicInteger(conns);
        for (int i = 0; i < conns; ++i) {
            CoSocket socket = new NioCoSocket(scheduler);
            Coroutine co = c -> {
                try {
                    debug("%s connect to localhost:%s", socket, port);
                    socket.connect(c, port);
                    debug("Connected: %s", socket);
                    byte[] b = new byte[512];

                    for (int j = 0; j < requests; ++j) {
                        socket.write(c, b);
                        socket.flush(c);
                        socket.readFully(c, b);
                        // Business time
                        scheduler.await(c, 1);
                    }
                } finally {
                    socket.close();
                    if (counter.addAndGet(1) >= conns) {
                        scheduler.shutdown();
                    }
                }
            };
            scheduler.fork(co, socket);
        }

        scheduler.run();
        long te = System.currentTimeMillis();
        info("Client: time %dms", te - ts);
    }

    static {
        System.setProperty("io.co.debug", "false");
        System.setProperty("io.co.soTimeout", "30000");
    }

}
```
