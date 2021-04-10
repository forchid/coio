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
package io.co.nio;

import com.offbynull.coroutines.user.Coroutine;
import io.co.*;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author little-pan
 * @since 2019-05-26
 *
 */
public class AcceptTest extends TestCase {
    static {
        System.setProperty("io.co.debug", "true");
    }

    public static void main(String[] args) {
        new AcceptTest().testAccept();
    }

    public void testAccept() {
        int port = 9960;
        for (int i = 0; i < 10; ++i) {
            try (CoServerSocket server = new NioCoServerSocket()) {
                Scheduler scheduler = server.getScheduler();
                server.bind(port);
                int conn = 250;
                for (int j = 0; j < conn; ++j) {
                    startClient(port, scheduler);
                }
                startServer(server, conn);
                scheduler.run();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }

    static void startServer(CoServerSocket server, int conn) {
        Coroutine serverCo = s -> {
            Scheduler scheduler = server.getScheduler();
            AtomicInteger counter = new AtomicInteger();

            for (int k = 0; k < conn; ++k) {
                CoSocket socket = server.accept(s);
                Coroutine connCo = h -> {
                    CoInputStream in = socket.getInputStream();
                    int i = in.read(h);
                    if (i != 1) throw new AssertionError("server: " + i + " neq 1");
                    CoOutputStream out = socket.getOutputStream();
                    out.write(h, i);
                    out.flush(h);
                    i++;
                    out.write(h, i);
                    out.flush(h);
                    int j = in.read(h);
                    if (i != j) throw new AssertionError("server: " + i + " neq " + j);
                    socket.close();
                    if (counter.addAndGet(1) == conn) {
                        scheduler.shutdown();
                    }
                };
                CoStarter.start(connCo);
            }
            server.close();
        };
        CoStarter.start(serverCo, server);
    }

    static void startClient(int port, Scheduler scheduler) {
        CoSocket socket = new NioCoSocket(scheduler);
        Coroutine clientCo = c -> {
            try {
                socket.connect(c, port);
                int i = 1;
                CoOutputStream out = socket.getOutputStream();
                out.write(c, i);
                out.flush(c);
                CoInputStream in = socket.getInputStream();
                int j = in.read(c);
                if (i != j) {
                    throw new AssertionError("client: " + i + " neq " + j);
                }
                j = in.read(c);
                i++;
                if (i != j) {
                    throw new AssertionError("client: " + i + " neq " + j);
                }
                out.write(c, i);
                out.flush(c);
            } finally {
                socket.close();
            }
        };
        CoStarter.start(clientCo, socket);
    }

}
