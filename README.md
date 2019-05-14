# co-io
An IO library based on coroutines
# Demos
## 1. Echo server
```java
public class EchoServer {

    public static void main(String[] args) {
        System.setProperty("io.co.soTimeout", "8000");
        System.setProperty("io.co.maxConnections", "2500");
        SocketAddress endpoint = new InetSocketAddress("localhost", 9999);
        
        NioCoServerSocket.start(new Connector(), endpoint);
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
                        final int n = in.read(co, b, i, b.length-i);
                        if(n == -1) {
                            //System.out.println("Server: EOF");
                            break;
                        }
                        i += n;
                    }
                    //System.out.println("Server: rbytes "+i);
                    out.write(co, b, 0, i);
                    out.flush(co);
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

    public static void main(String[] args) throws Exception {
        System.setProperty("io.co.soTimeout", "3000");
        System.setProperty("io.co.debug", "false");
        
        final long ts = System.currentTimeMillis();
        final SocketAddress remote = new InetSocketAddress("localhost", 9999);
        
        final NioCoScheduler scheduler = new NioCoScheduler();
        final int conns = 3000;
        final MutableInteger success = new MutableInteger();
        try {
            for(int i = 0; i < conns; ++i){
                final Coroutine connector = new Connector(i, success, scheduler);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote, 6000);
                if(i % 100 == 0){
                    Thread.sleep(100L);
                }
            }
            scheduler.start();
        } finally {
            scheduler.shutdown();
        }
        
        System.out.println(String.format("Bye: conns = %s, success = %s, time = %sms",
              conns, success, System.currentTimeMillis() - ts));
    }
    
    static class Connector implements Coroutine {
        private static final long serialVersionUID = 1L;
        
        final NioCoScheduler scheduler;
        final MutableInteger success;
        final int id;
        
        Connector(int id, MutableInteger success, NioCoScheduler scheduler){
            this.scheduler = scheduler;
            this.success = success;
            this.id = id;
        }

        @Override
        public void run(Continuation co) throws Exception {
            CoSocket sock = null;
            try {
                final Object ctx = co.getContext();
                if(ctx instanceof Throwable){
                    // Connect fail
                    final Throwable cause = (Throwable)ctx;
                    cause.printStackTrace();
                    return;
                }
                
                sock = (CoSocket)ctx;
                //System.out.println("Connected: " + sock);
                final long ts = System.currentTimeMillis();
                final CoInputStream in = sock.getInputStream();
                final CoOutputStream out = sock.getOutputStream();
                
                final byte[] b = new byte[512];
                final int requests = 10;
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
                success.value++;
                System.out.println(String.format("Client-%05d: time %dms", 
                     id, (System.currentTimeMillis() - ts)));
            } finally {
                IoUtils.close(sock);
                scheduler.shutdown();
            }
        }
        
    }
    
    static class MutableInteger {
        int value;
        
        MutableInteger(){
            this(0);
        }
        
        MutableInteger(int value){
            this.value = value;
        }
        
        public String toString(){
            return value + "";
        }
    }

}
```
