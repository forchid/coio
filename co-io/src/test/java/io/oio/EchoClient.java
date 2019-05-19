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
package io.oio;

import io.co.util.IoUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple Socket demo.
 * 
 * @author little-pan
 * @since 2019-05-16
 *
 */
public class EchoClient {
    static final int soTimeout = 30000;
    static final boolean debug = Boolean.getBoolean("io.co.debug");

    public static void main(String[] args) throws Exception {
        
        final long ts = System.currentTimeMillis();
        final String host = System.getProperty("io.co.host", "localhost");
        final SocketAddress remote = new InetSocketAddress(host, 9999);
        
        final int conns, threads;
        if(args.length > 0){
            conns = Integer.parseInt(args[0]);
        }else{
            conns = 250;
        }
        threads = conns;
        
        final ExecutorService executors = Executors.newFixedThreadPool(threads);
        final AtomicInteger success = new AtomicInteger();
        try {
            for(int i = 0; i < conns; ++i){
                final Runnable connector = new Connector(i, success, remote);
                executors.execute(connector);
            }
        } finally {
            executors.shutdown();
            executors.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        
        System.out.println(String.format("Bye: conns = %s, success = %s, time = %sms",
              conns, success, System.currentTimeMillis() - ts));
    }
    
    static class Connector implements Runnable {
        
        final AtomicInteger success;
        final int id;
        final SocketAddress remote;
        
        Connector(int id, AtomicInteger success, SocketAddress remote){
            this.success = success;
            this.id = id;
            this.remote = remote;
        }

        @Override
        public void run() {
            Socket sock = null;
            try {
                sock = new Socket();
                sock.connect(remote);
                sock.setSoTimeout(soTimeout);
                
                //System.out.println("Connected: " + sock);
                final long ts = System.currentTimeMillis();
                final BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
                final BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
                
                final byte[] b = new byte[512];
                final int requests = 100;
                for(int i = 0; i < requests; ++i) {
                    final int wbytes = b.length;
                    out.write(b);
                    out.flush();
                    
                    int rbytes = 0;
                    for(; rbytes < wbytes;) {
                        final int n = in.read(b, rbytes, b.length - rbytes);
                        if(n == -1) {
                            throw new EOFException();
                        }
                        rbytes += n;
                    }
                    
                    //System.out.println(String.format("wbytes %d, rbytes %d ", wbytes, rbytes));
                }
                success.incrementAndGet();
                System.out.println(String.format("Client-%05d: time %dms", 
                     id, (System.currentTimeMillis() - ts)));
            } catch(final IOException e){
                if(debug) {
                    e.printStackTrace();
                }
            } finally {
                IoUtils.close(sock);
            }
        }
        
    }

}
