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

import com.offbynull.coroutines.user.Coroutine;
import io.co.*;
import static io.co.util.LogUtils.*;

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
        CoStarter.start(serverCo, server);
    }

    static void handleConn(CoSocket socket) {
        Scheduler scheduler = socket.getScheduler();
        Coroutine connCo = c -> {
            try {
                String prefix = "server-" + socket.id();
                CoInputStream in = socket.getInputStream();
                CoOutputStream out = socket.getOutputStream();
                byte[] b = new byte[512];

                while (true) {
                    int i = 0;
                    while (i < b.length) {
                        debug(prefix + " read: offset %s", i);
                        int len = b.length - i;
                        int n = in.read(c, b, i, len);
                        debug(prefix + " read: bytes %s", n);
                        if (n == -1) {
                            return;
                        }
                        i += n;
                    }
                    out.write(c, b, 0, i);
                    out.flush(c);
                    debug(prefix + " flush: bytes %s", i);
                    // Business time
                    scheduler.await(c, 0);
                }
            } finally {
                socket.close();
            }
        };
        CoStarter.start(connCo, socket);
    }

    public static void shutdown() {
        scheduler.shutdown();
    }

    static {
        System.setProperty("io.co.soTimeout", "30000");
        System.setProperty("io.co.debug", "false");
    }

}
