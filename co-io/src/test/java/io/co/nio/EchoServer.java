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
import io.co.CoSocket;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

/**
 * A simple CoServerSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
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
