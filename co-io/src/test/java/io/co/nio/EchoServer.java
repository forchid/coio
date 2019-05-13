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
        final SocketAddress endpoint = new InetSocketAddress("localhost", 9999);
        
        NioCoServerSocket.start(new Coroutine(){
            private static final long serialVersionUID = 1L;

            @Override
            public void run(Continuation co) throws Exception {
                final CoSocket sock = (CoSocket)co.getContext();
                System.out.println("Connected: " + sock);
                
                final CoInputStream in = sock.getInputStream();
                final byte[] buf = new byte[1024];
                int i = in.read(co, buf);
                sock.getOutputStream().write(co, buf, 0, i);
                
                sock.close();
                sock.getCoScheduler().shutdown();
            }
            
        }, endpoint);
        
        System.out.println("Bye");
    }

}
