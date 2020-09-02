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

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

import io.co.CoOutputStream;
import io.co.CoScheduler;
import io.co.CoSocket;
import io.co.util.IoUtils;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author little-pan
 * @since 2019-05-26
 *
 */
public class AcceptTest extends TestCase {

    public static void main(String[] args) throws Exception {
        AcceptTest test = new AcceptTest();
        test.testAccept();
    }

    public void testAccept() throws Exception {
        System.setProperty("io.co.debug", "true");
        int port = 9960;
        final NioCoServerSocket server = new NioCoServerSocket(port, ServerHandler.class);
        final NioCoScheduler scheduler = server.getScheduler();
        
        final CoSocket socket = new NioCoSocket(new ClientHandler(), scheduler);
        socket.connect(new InetSocketAddress(port));
        
        System.out.println("wait");
        scheduler.awaitTermination();
        socket.close();
        server.close();
        
        System.out.println("OK");
    }

    static class ClientHandler implements Coroutine {

        private static final long serialVersionUID = 1L;

        @Override
        public void run(Continuation co) throws Exception {
            final Object ctx = co.getContext();
            CoScheduler scheduler = null;
            CoSocket socket = null;
            try {
                if(ctx instanceof Throwable) {
                    System.err.print("Connection failed: ");
                    final Throwable cause = (Throwable)ctx;
                    cause.printStackTrace();
                    return;
                }
                String threadName = Thread.currentThread().getName();
                System.out.println(threadName + ": connected");
                socket = (CoSocket)ctx;
                scheduler = socket.getScheduler();
                CoOutputStream out = socket.getOutputStream();
                out.write(co, 1);
                out.flush(co);
                int i = socket.getInputStream().read(co);
                if (i != 1) throw new IOException("Echo error");
            } finally {
                IoUtils.close(socket);
                if (scheduler != null) scheduler.shutdown();
            }
        }

    }

    static class ServerHandler implements Coroutine {

        private static final long serialVersionUID = 1L;

        @Override
        public void run(Continuation co) throws Exception {
            final Object ctx = co.getContext();
            CoSocket socket = null;
            try {
                if(ctx instanceof Throwable) {
                    System.err.print("Connection failed: ");
                    final Throwable cause = (Throwable)ctx;
                    cause.printStackTrace();
                    return;
                }
                String threadName = Thread.currentThread().getName();
                System.out.println(threadName + ": accepted");
                socket = (CoSocket)ctx;
                int i = socket.getInputStream().read(co);
                if (i != 1) throw new IOException("Request error");
                CoOutputStream out = socket.getOutputStream();
                out.write(co, i);
                out.flush(co);
            } finally {
                IoUtils.close(socket);
            }
        }

    }

}
