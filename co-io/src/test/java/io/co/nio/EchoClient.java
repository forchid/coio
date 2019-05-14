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

import io.co.CoIOException;
import io.co.CoInputStream;
import io.co.CoOutputStream;
import io.co.CoSocket;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

/**
 * A simple CoSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class EchoClient {

    public static void main(String[] args) throws IOException {
        final SocketAddress remote = new InetSocketAddress("localhost", 9999);
        
        final NioCoScheduler scheduler = new NioCoScheduler();
        try {
            final int conns = 2000;
            for(int i = 0; i < conns; ++i){
                final Coroutine connector = new Connector(i);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote);
            }
            scheduler.start();
        }finally{
            scheduler.shutdown();
        }
        
        System.out.println("Bye");
    }
    
    static class Connector implements Coroutine {
        private static final long serialVersionUID = 1L;
        
        final int id;
        
        Connector(int id){
            this.id = id;
        }

        @Override
        public void run(Continuation co) throws Exception {
            final CoSocket sock = (CoSocket)co.getContext();
            if(sock == null){
                // Connect fail
                return;
            }
            //System.out.println("Connected: " + sock);
            
            final long ts = System.currentTimeMillis();
            final CoInputStream in = sock.getInputStream();
            final CoOutputStream out = sock.getOutputStream();
            
            final byte[] b = new byte[512];
            final int requests = 1000;
            for(int i = 0; i < requests; ++i) {
                try {
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
                } catch(final CoIOException e) {
                    System.err.println(String.format("Client-%05d: io error %s ", id, e));
                    break;
                }
            }
            System.out.println(String.format("Client-%05d: time %dms", id, (System.currentTimeMillis() - ts)));
            
            sock.close();
            sock.getCoScheduler().shutdown();
        }
        
    }

}
