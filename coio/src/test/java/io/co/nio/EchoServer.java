/*
 * Copyright (c) 2019, little-pan, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package io.co.nio;

import io.co.CoInputStream;
import io.co.CoOutputStream;
import io.co.CoServerSocket;
import io.co.CoSocket;

import java.net.InetAddress;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import static io.co.nio.NioCoScheduler.*;

/**
 * A simple CoServerSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
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
                    //System.out.println("Server: rbytes "+i);
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
