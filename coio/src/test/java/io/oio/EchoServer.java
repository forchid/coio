/*
 * Copyright (c) 2021, little-pan, All rights reserved.
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
package io.oio;

import io.co.util.IoUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple CoServerSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class EchoServer {
    static final int soTimeout = 30000;

    public static void main(String[] args) throws Exception {
        final String host = System.getProperty("io.co.host", "localhost");
        SocketAddress endpoint = new InetSocketAddress(host, 9999);
        
        final int threads = 1000;
        final ExecutorService executors = Executors.newFixedThreadPool(threads);
        try {
            run(executors, endpoint);
            System.out.println("Bye");
        } finally{
            executors.shutdown();
        }
    }
    
    static void run(ExecutorService executors, SocketAddress endpoint)
            throws IOException {
        final ServerSocket sSock = new ServerSocket();
        try {
            sSock.bind(endpoint, 150);
            for(;;){
                final Socket sock = sSock.accept();
                executors.execute(new Connector(sock));
            }
        } finally {
            IoUtils.close(sSock);
        }
    }

    static class Connector implements Runnable {
        final Socket sock;
        
        Connector(Socket sock) {
            this.sock = sock;
        }
        
        @Override
        public void run() {
            //System.out.println("Connected: " + sock);
            
            final BufferedInputStream in;
            final BufferedOutputStream out;
            try {
                sock.setSoTimeout(soTimeout);
                
                in  = new BufferedInputStream(sock.getInputStream());
                out = new BufferedOutputStream(sock.getOutputStream());
                
                final byte[] b = new byte[512];
                for(;;) {
                    int i = 0;
                    while (i < b.length) {
                        final int n = in.read(b, i, b.length-i);
                        if(n == -1) {
                            //System.out.println("Server: EOF");
                            return;
                        }
                        i += n;
                    }
                    //System.out.println("Server: rbytes "+i);
                    out.write(b, 0, i);
                    out.flush();
                    
                    // Business time
                    Thread.sleep(100);
                }
            } catch (final Exception e){
                // ignore
            } finally {
                IoUtils.close(sock);
            }
        }
        
    }
}
