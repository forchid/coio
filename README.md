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
        CoStarter.start(serverCo, server);
    }

    static void handleConn(CoSocket socket) {
        Scheduler scheduler = socket.getScheduler();
        Coroutine connCo = c -> {
            try {
                CoInputStream in = socket.getInputStream();
                CoOutputStream out = socket.getOutputStream();
                byte[] b = new byte[512];

                while (true) {
                    int i = 0;
                    while (i < b.length) {
                        debug("read: offset %s", i);
                        int len = b.length - i;
                        int n = in.read(c, b, i, len);
                        debug(" read: bytes %s", n);
                        if (n == -1) {
                            return;
                        }
                        i += n;
                    }
                    out.write(c, b, 0, i);
                    out.flush(c);
                    debug("flush: bytes %s", i);
                    // Business time
                    scheduler.await(c, 0);
                }
            } finally {
                socket.close();
            }
        };
        CoStarter.start(connCo, socket);
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
        System.setProperty("io.co.debug", "false");
        int port = Integer.getInteger("io.co.port", 9999);
        final int conns, requests;
        if (args.length > 0) conns = Integer.decode(args[0]);
        else conns = 100;
        if (args.length > 1) requests = Integer.decode(args[1]);
        else requests = 10000;

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
                    CoInputStream in = socket.getInputStream();
                    CoOutputStream out = socket.getOutputStream();
                    byte[] b = new byte[512];

                    for (int j = 0; j < requests; ++j) {
                        out.write(c, b);
                        int written = b.length;
                        out.flush(c);

                        int reads = 0;
                        while (reads < written) {
                            int len = b.length - reads;
                            int n = in.read(c, b, reads, len);
                            if (n == -1) {
                                throw new EOFException();
                            }
                            reads += n;
                        }
                        // Business time
                        scheduler.await(c, 0);
                    }
                } finally {
                    socket.close();
                    if (counter.addAndGet(1) >= conns) {
                        scheduler.shutdown();
                    }
                }
            };
            CoStarter.start(co, socket);
        }

        scheduler.run();
        long te = System.currentTimeMillis();
        info("Client: time %dms", te - ts);
    }

}
```
