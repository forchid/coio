/*
 * Copyright (c) 2020, little-pan, All rights reserved.
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
import io.co.*;
import io.co.util.IoUtils;
import io.co.util.RuntimeUtils;
import junit.framework.TestCase;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ServerSocketTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ServerSocketTest().allTest();
    }

    public void allTest() throws Exception {
        testBind();
    }

    public void testBind() throws Exception {
        CoServerSocket serverSocket = null;
        CoScheduler scheduler;
        int port;

        boolean linux = RuntimeUtils.isLinux();
        try {
            if (linux) {
                try {
                    port = 999;
                    serverSocket = new NioCoServerSocket(port, DummySocketHandler.class);
                    fail("Bind should be failed for permission issue");
                } catch (CoIOException e) {
                    // ok
                }
            }

            port = 19999;
            serverSocket = new NioCoServerSocket(port, DummySocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            serverSocket.close();
            assertTrue(scheduler.isShutdown());

            serverSocket = new NioCoServerSocket(DummySocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            if (linux) {
                try {
                    port = 999;
                    Future<?> bf = serverSocket.bind(new InetSocketAddress(port));
                    bf.get();
                    fail("Bind should be failed for permission issue");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof CoIOException)) throw new AssertionError(cause);
                    // ok
                }
            }
            serverSocket.close();
            assertTrue(scheduler.isShutdown());

            serverSocket = new NioCoServerSocket(DummySocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            port = 19999;
            Future<?> bf = serverSocket.bind(new InetSocketAddress(port));
            bf.get();
            serverSocket.close();
            assertTrue(scheduler.isShutdown());

        } finally {
            IoUtils.close(serverSocket);
        }
    }

    static class DummySocketHandler implements SocketHandler {

        @Override
        public void handle(Continuation co, CoSocket socket) throws Exception {
            socket.close();
        }

    }

}
