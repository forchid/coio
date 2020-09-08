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
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import io.co.*;
import io.co.util.IoUtils;
import io.co.util.RuntimeUtils;
import junit.framework.TestCase;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ServerSocketTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ServerSocketTest().allTest();
    }

    public void allTest() throws Exception {
        testBind();
    }

    public void testBind() throws Exception {
        NioCoServerSocket serverSocket = null;
        NioScheduler scheduler;
        InetSocketAddress sa;
        int port;

        boolean linux = RuntimeUtils.isLinux();
        try {
            if (linux) {
                try {
                    port = 999;
                    serverSocket = new NioCoServerSocket(port, ShutdownSocketHandler.class);
                    serverSocket.awaitClosed();
                    fail("Bind should be failed for permission issue");
                } catch (IllegalStateException e) {
                    // ok
                }
            }

            port = 19999;
            serverSocket = new NioCoServerSocket(port, ShutdownSocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            new NioCoSocket(port, new ShutdownSocketHandler(), scheduler);
            serverSocket.awaitClosed();
            assertTrue(scheduler.isShutdown());

            serverSocket = new NioCoServerSocket(ShutdownSocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            if (linux) {
                try {
                    port = 999;
                    SocketAddress a = new InetSocketAddress(port);
                    CoServerSocket ss = serverSocket;
                    scheduler.execute(() -> {
                        Coroutine c = new Coroutine() {
                            @Override
                            public void run(Continuation co) throws Exception {
                                ss.bind(co, a);
                            }
                        };
                        CoroutineRunner runner = new CoroutineRunner(c);
                        runner.setContext(ss);
                        runner.execute();
                    });

                    serverSocket.awaitClosed();
                    fail("Bind should be failed for permission issue");
                } catch (IllegalStateException e) {
                    // ok
                }
            }
            serverSocket.close();
            assertTrue(scheduler.isShutdown());

            serverSocket = new NioCoServerSocket(ShutdownSocketHandler.class);
            scheduler = serverSocket.getScheduler();
            assertFalse(scheduler.isShutdown());
            port = 19999;
            final SocketAddress a = new InetSocketAddress(port);
            CoServerSocket ss = serverSocket;
            scheduler.execute(() -> {
                Coroutine c = new Coroutine() {
                    @Override
                    public void run(Continuation co) throws Exception {
                        ss.bind(co, a);
                    }
                };
                CoroutineRunner runner = new CoroutineRunner(c);
                runner.setContext(ss);
                runner.execute();
            });
            new NioCoSocket(port, new ShutdownSocketHandler(), scheduler);
            serverSocket.awaitClosed();
            assertTrue(scheduler.isShutdown());

        } finally {
            IoUtils.close(serverSocket);
        }
    }

    static class ShutdownSocketHandler extends Connector {

        @Override
        public void handleConnection(Continuation co, CoSocket socket) throws Exception {
            Scheduler scheduler = socket.getScheduler();
            socket.close();
            scheduler.shutdown();
        }

    }

}
