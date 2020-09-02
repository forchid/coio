# coio
A high performance io framework based on coroutines

# Demos
## 1. Echo server
```java
public class EchoServer {

    public static void main(String[] args) throws Exception {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.maxConnections", "10000");
        //System.setProperty("io.co.scheduler.childrenCount", "2");
        System.setProperty("io.co.debug", "false");
        final String host = System.getProperty("io.co.host", "localhost");
        final int port = Integer.getInteger("io.co.port", 9999);
        final InetAddress bindAddress = InetAddress.getByName(host);
        
        final CoServerSocket server =
                new NioCoServerSocket(port, 150, bindAddress, Connector.class);
        server.getScheduler().awaitTermination();
        server.close();
        
        System.out.println("Bye");
    }

    static class Connector implements Coroutine {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(Continuation co) throws Exception {
            final CoSocket sock = (CoSocket)co.getContext();
            //System.out.println("Connected: " + sock);
            
            final CoInputStream in = sock.getInputStream();
            final CoOutputStream out = sock.getOutputStream();
            
            try {
                final byte[] b = new byte[512];
                for(;;) {
                    int i = 0;
                    while (i < b.length) {
                        debug("read: offset %s", i);
                        final int n = in.read(co, b, i, b.length-i);
                        debug("read: bytes %s", n);
                        if(n == -1) {
                            //System.out.println("Server: EOF");
                            return;
                        }
                        i += n;
                    }
                    out.write(co, b, 0, i);
                    out.flush(co);
                    debug("flush: bytes %s", i);
                    
                    // Business time
                    sock.getScheduler().await(co, 0L);
                }
            } finally {
                sock.close();
                //sock.getCoScheduler().shutdown();
            }
        }
        
    }

}
```
## 2. Echo client
```java
public class EchoClient {
    
    static final boolean debug = Boolean.getBoolean("io.co.debug");

    public static void main(String[] args) throws Exception {
        System.setProperty("io.co.soTimeout", "30000");
        final String host = System.getProperty("io.co.host", "localhost");
        
        final int connectionCount, schedulerCount;
        if(args.length > 0){
            connectionCount = Integer.parseInt(args[0]);
        }else{
            connectionCount = 250;
        }
        schedulerCount = Math.min(2, connectionCount);
        
        final long ts = System.currentTimeMillis();
        final SocketAddress remote = new InetSocketAddress(host, 9999);
        
        // Parallel scheduler
        final NioCoScheduler[] schedulers = new NioCoScheduler[schedulerCount];
        final AtomicInteger []remains = new AtomicInteger[schedulerCount];
        for (int i = 0; i < schedulers.length; ++i) {
            final String name = "nio-"+ i;
            schedulers[i] = new NioCoScheduler(name, connectionCount, connectionCount, 0);
            schedulers[i].start();
            remains[i] = new AtomicInteger();
        }
        
        final AtomicInteger successCount = new AtomicInteger();
        try {
            for(int i = 0; i < connectionCount; ++i){
                final int j = i % schedulers.length;
                final NioCoScheduler scheduler = schedulers[j];
                final AtomicInteger remain = remains[j];
                final Coroutine connector = new Connector(i, successCount, remain, scheduler);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote, 30000);
                remain.incrementAndGet();
            }
        } finally {
            for(final NioCoScheduler s : schedulers){
                s.awaitTermination();
            }
        }
        
        System.out.println(String.format("Bye: connectionCount = %s, successCount = %s, time = %sms",
                connectionCount, successCount, System.currentTimeMillis() - ts));
    }
    
    static class Connector implements Coroutine {
        private static final long serialVersionUID = 1L;
        
        final NioCoScheduler scheduler;
        final AtomicInteger success;
        final AtomicInteger remain;
        final int id;
        
        Connector(int id, AtomicInteger success, AtomicInteger remain, NioCoScheduler scheduler){
            this.scheduler = scheduler;
            this.success   = success;
            this.remain    = remain;
            this.id = id;
        }

        @Override
        public void run(Continuation co) throws Exception {
            CoSocket sock = null;
            try {
                final Object ctx = co.getContext();
                if(ctx instanceof Throwable){
                    // Connect fail
                    if(debug) {
                        final Throwable cause = (Throwable)ctx; 
                        cause.printStackTrace();
                    }
                    return;
                }
                
                sock = (CoSocket)ctx;
                debug("Connected: %s", sock);
                final long ts = System.currentTimeMillis();
                final CoInputStream in = sock.getInputStream();
                final CoOutputStream out = sock.getOutputStream();
                
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

                    //System.out.println(String.format("written %d, reads %d ", written, reads));
                }
                success.incrementAndGet();
                System.out.println(String.format("[%s]Client-%05d: time %dms", 
                     Thread.currentThread().getName(), id, (System.currentTimeMillis() - ts)));
            } finally {
                remain.decrementAndGet();
                IoUtils.close(sock);
                // Shutdown only when all connection completed
                if(remain.compareAndSet(0, 0)){
                    scheduler.shutdown();
                }
            }
        }
        
    }

}
```
