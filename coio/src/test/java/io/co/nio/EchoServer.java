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
import static io.co.util.LogUtils.*;

import java.io.EOFException;
import java.io.IOException;

/**
 * A simple CoServerSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class EchoServer {

    static volatile Scheduler scheduler;

    public static void main(String[] args) {
        int port = Integer.getInteger("io.co.port", 9999);

        try (CoServerSocket server = new NioCoServerSocket()) {
            scheduler = server.getScheduler();
            server.bind(port);
            startServer(server);
            scheduler.run();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static void startServer(CoServerSocket server) {
        Scheduler scheduler = server.getScheduler();
        Coroutine serverCo = s -> {
            while (!scheduler.isShutdown()) {
                CoSocket socket = server.accept(s);
                handleConn(socket);
            }
            server.close();
        };
        scheduler.fork(serverCo, server);
    }

    static void handleConn(CoSocket socket) {
        Scheduler scheduler = socket.getScheduler();
        Coroutine connCo = c -> {
            try {
                byte[] b = new byte[512];

                while (true) {
                    int n = socket.readFully(c, b);
                    socket.write(c, b, 0, n);
                    socket.flush(c);
                    debug("flush: bytes %s", n);
                    // Business time
                    scheduler.await(c, 100);
                }
            } catch (EOFException e) {
                // Ignore
            } finally {
                socket.close();
            }
        };
        scheduler.fork(connCo, socket);
    }

    public static void shutdown() {
        scheduler.shutdown();
    }

    static {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.debug", "false");
    }

}
