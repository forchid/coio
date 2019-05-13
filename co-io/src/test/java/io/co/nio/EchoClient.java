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

    public static void main(String[] args) {
        final SocketAddress remote = new InetSocketAddress("localhost", 9999);
        
        NioCoSocket.start(new Coroutine(){
            private static final long serialVersionUID = 1L;

            @Override
            public void run(Continuation co) throws Exception {
                final long ts = System.currentTimeMillis();
                final CoSocket sock = (CoSocket)co.getContext();
                //System.out.println("Connected: " + sock);
                
                final CoInputStream in = sock.getInputStream();
                final CoOutputStream out = sock.getOutputStream();
                
                final byte[] b = new byte[512];
                for(int i = 0; i < 100000; ++i) {
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
                        System.err.println("Client: io error: "+ e);
                        break;
                    }
                }
                System.out.println("Time: " + (System.currentTimeMillis() - ts) + "ms");
                
                sock.close();
                sock.getCoScheduler().shutdown();
            }
            
        }, remote);
        
        //System.out.println("Bye");
    }

}
