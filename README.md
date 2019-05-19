# co-io
An IO library based on coroutines
# Demos
## 1. Echo server
```java
public class EchoServer {

    public static void main(String[] args) {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.maxConnections", "10000");
        System.setProperty("io.co.scheduler.childCount", "2");
        System.setProperty("io.co.debug", "false");
        final String host = System.getProperty("io.co.host", "localhost");
        SocketAddress endpoint = new InetSocketAddress(host, 9999);
        
        CoServerSocket.startAndServe(Connector.class, endpoint);
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
                    for(; i < b.length;) {
                        debug("read: offset %s", i);
                        final int n = in.read(co, b, i, b.length-i);
                        debug("read: bytes %s", n);
                        if(n == -1) {
                            //System.out.println("Server: EOF");
                            return;
                        }
                        i += n;
                    }
                    //System.out.println("Server: rbytes "+i);
                    out.write(co, b, 0, i);
                    out.flush(co);
                    debug("flush: bytes %s", i);
                    
                    // Business time
                    sock.getCoScheduler().await(co, 0L);
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
        
        final int conns, schedCount;
        if(args.length > 0){
            conns = Integer.parseInt(args[0]);
        }else{
            conns = 250;
        }
        schedCount = Math.min(2, conns);
        
        final long ts = System.currentTimeMillis();
        final SocketAddress remote = new InetSocketAddress(host, 9999);
        
        // Parallel scheduler
        final NioCoScheduler[] schedulers = new NioCoScheduler[schedCount];
        final Thread[] schedThreads = new Thread[schedulers.length];
        for(int i = 0; i < schedulers.length; ++i){
            final int j = i;
            final NioCoScheduler sched = schedulers[j] = new NioCoScheduler(conns, conns, 0);
            final Thread t = schedThreads[j] = new Thread(){
                @Override
                public void run(){
                    setName("Scheduler-"+j);
                    sched.startAndServe();
                }
            };
            t.start();
        }
        
        final AtomicInteger success = new AtomicInteger();
        try {
            for(int i = 0; i < conns; ++i){
                final NioCoScheduler scheduler = schedulers[i%schedulers.length];
                final Coroutine connector = new Connector(i, success, scheduler);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote, 30000);
            }
        } finally {
            for(final Thread t : schedThreads){
                t.join();
            }
        }
        
        System.out.println(String.format("Bye: conns = %s, success = %s, time = %sms",
              conns, success, System.currentTimeMillis() - ts));
    }
    
    static class Connector implements Coroutine {
        private static final long serialVersionUID = 1L;
        
        final NioCoScheduler scheduler;
        final AtomicInteger success;
        final int id;
        
        Connector(int id, AtomicInteger success, NioCoScheduler scheduler){
            this.scheduler = scheduler;
            this.success   = success;
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
                    final int wbytes = b.length;
                    out.flush(co);
                    
                    int rbytes = 0;
                    for(; rbytes < wbytes;) {
                        final int n = in.read(co, b, rbytes, b.length - rbytes);
                        if(n == -1) {
                            throw new EOFException();
                        }
                        rbytes += n;
                    }
                    
                    //System.out.println(String.format("wbytes %d, rbytes %d ", wbytes, rbytes));
                }
                success.incrementAndGet();
                System.out.println(String.format("[%s]Client-%05d: time %dms", 
                     Thread.currentThread().getName(), id, (System.currentTimeMillis() - ts)));
            } finally {
                IoUtils.close(sock);
                scheduler.shutdown();
            }
        }
        
    }
}
```
