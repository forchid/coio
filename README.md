# coio
A high performance io framework based on coroutines

# Demos
## 1. Echo server
```java
public class EchoServer {

    static final int PORT = Integer.getInteger("io.co.port", 9999);
    static final CountDownLatch startLatch = new CountDownLatch(1);
    static volatile Thread serverThread;

    public static void main(String[] args) throws Exception {
        serverThread = Thread.currentThread();
        CoServerSocket server = new NioCoServerSocket(PORT, EchoHandler.class);
        startLatch.countDown();
        try {
            server.awaitClosed();
            server.close();
        } catch (InterruptedException e) {
            // Ignore
        } finally {
            server.getScheduler().shutdown();
            info("Bye");
        }
    }

    public static void shutdown() {
        Thread serverThread = EchoServer.serverThread;
        if (serverThread != null) serverThread.interrupt();
    }

    public static void await() throws InterruptedException {
        startLatch.await();
    }

    static class EchoHandler extends Connector {

        private static final long serialVersionUID = 1L;

        @Override
        public void handleConnection(Continuation co, CoSocket socket) {
            final CoInputStream in = socket.getInputStream();
            final CoOutputStream out = socket.getOutputStream();
            Scheduler scheduler = socket.getScheduler();
            try {
                final byte[] b = new byte[512];
                while (!scheduler.isShutdown()) {
                    int i = 0;
                    while (i < b.length) {
                        debug("read: offset %s", i);
                        final int n = in.read(co, b, i, b.length - i);
                        debug("read: bytes %s", n);
                        if(n == -1) {
                            return;
                        }
                        i += n;
                    }
                    out.write(co, b, 0, i);
                    out.flush(co);
                    debug("flush: bytes %s", i);
                    
                    // Business time
                    scheduler.await(co, 0L);
                }
            } finally {
                socket.close();
            }
        }
        
    }

    static {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.maxConnections", "10000");
        System.setProperty("io.co.debug", "false");
    }

}
```
## 2. Echo client
```java
public class EchoClient {

    static final int PORT = Integer.getInteger("io.co.port", 9999);

    public static void main(String[] args) throws Exception {
        System.setProperty("io.co.soTimeout", "30000");
        final String host = System.getProperty("io.co.host", "localhost");
        
        final int connectionCount, schedulerCount;
        if(args.length > 0){
            connectionCount = Integer.parseInt(args[0]);
        }else{
            connectionCount = 250;
        }
        schedulerCount = Math.min(4, connectionCount);
        
        final long ts = System.currentTimeMillis();
        
        // Parallel scheduler
        final NioScheduler[] schedulers = new NioScheduler[schedulerCount];
        final AtomicInteger []remains = new AtomicInteger[schedulerCount];
        for (int i = 0; i < schedulers.length; ++i) {
            final String name = "nio-"+ i;
            schedulers[i] = new NioScheduler(name, connectionCount, connectionCount, 0);
            schedulers[i].start();
            remains[i] = new AtomicInteger();
        }
        
        final AtomicInteger successCount = new AtomicInteger();
        try {
            for(int i = 0; i < connectionCount; ++i){
                final int j = i % schedulers.length;
                final NioScheduler scheduler = schedulers[j];
                final AtomicInteger remain = remains[j];
                final SocketHandler connector = new EchoHandler(i, successCount, remain, scheduler);
                new NioCoSocket(host, PORT, connector, scheduler);
                remain.incrementAndGet();
            }
        } finally {
            for(final NioScheduler s : schedulers){
                s.awaitTermination();
            }
        }
        
        info("Bye: connectionCount = %s, successCount = %s, time = %sms",
                connectionCount, successCount, System.currentTimeMillis() - ts);
    }
    
    static class EchoHandler extends Connector {
        private static final long serialVersionUID = 1L;
        
        final NioScheduler scheduler;
        final AtomicInteger success;
        final AtomicInteger remain;
        final int id;

        EchoHandler(int id, AtomicInteger success, AtomicInteger remain, NioScheduler scheduler){
            this.scheduler = scheduler;
            this.success   = success;
            this.remain    = remain;
            this.id = id;
        }

        @Override
        public void handleConnection(Continuation co, CoSocket socket) throws Exception {
            try {
                debug("Connected: %s", socket);
                final long ts = System.currentTimeMillis();
                final CoInputStream in = socket.getInputStream();
                final CoOutputStream out = socket.getOutputStream();
                
                final byte[] b = new byte[512];
                final int requests = 100;
                for(int i = 0; i < requests; ++i) {
                    out.write(co, b);
                    final int written = b.length;
                    out.flush(co);
                    
                    int reads = 0;
                    while (reads < written) {
                        final int n = in.read(co, b, reads, b.length - reads);
                        if(n == -1) {
                            throw new EOFException();
                        }
                        reads += n;
                    }
                }
                this.success.incrementAndGet();
                info("Client-%05d: time %dms", id, (System.currentTimeMillis() - ts));
            } finally {
                IoUtils.close(socket);
            }
            tryShutdown();
        }

        void tryShutdown() {
            this.remain.decrementAndGet();
            // Shutdown only when all connection completed
            if (this.remain.compareAndSet(0, 0)) {
                this.scheduler.shutdown();
            }
        }

        @Override
        public void exceptionCaught(Throwable cause) {
            try {
                super.exceptionCaught(cause);
            } finally {
                tryShutdown();
            }
        }
        
    }

}
```
