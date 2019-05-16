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
        SocketAddress endpoint = new InetSocketAddress("localhost", 9999);
        
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
                final byte[] b = new byte[256];
                for(;;) {
                    int i = 0;
                    for(; i < b.length;) {
                        final int n = in.read(co, b, i, b.length-i);
                        if(n == -1) {
                            //System.out.println("Server: EOF");
                            return;
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
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.debug", "true");
        
        final int conns;
        if(args.length > 0){
            conns = Integer.parseInt(args[0]);
        }else{
            conns = 10000;
        }
        
        final long ts = System.currentTimeMillis();
        final SocketAddress remote = new InetSocketAddress("localhost", 9999);
        
        final NioCoScheduler scheduler = new NioCoScheduler(conns, conns, 0);
        final MutableInteger success = new MutableInteger();
        try {
            for(int i = 0; i < conns; ++i){
                final Coroutine connector = new Connector(i, success, scheduler);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote, 30000);
            }
            scheduler.startAndServe();
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
                    ((Throwable)ctx).printStackTrace();
                    return;
                }
                
                sock = (CoSocket)ctx;
                //System.out.println("Connected: " + sock);
                final long ts = System.currentTimeMillis();
                final CoInputStream in = sock.getInputStream();
                final CoOutputStream out = sock.getOutputStream();
                
                final byte[] b = new byte[256];
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
