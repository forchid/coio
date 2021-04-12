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

import java.util.concurrent.atomic.AtomicInteger;

import static io.co.util.LogUtils.*;

/**
 * A simple CoSocket demo.
 * 
 * @author little-pan
 * @since 2021-04-05
 *
 */
public class EchoClient {

    public static void main(String[] args) {
        int port = Integer.getInteger("io.co.port", 9999);
        final int conns, requests;
        if (args.length > 0) conns = Integer.decode(args[0]);
        else conns = 10000;
        if (args.length > 1) requests = Integer.decode(args[1]);
        else requests = 100;

        long ts = System.currentTimeMillis();
        Scheduler scheduler = new NioScheduler();
        AtomicInteger counter = new AtomicInteger(conns);
        for (int i = 0; i < conns; ++i) {
            CoSocket socket = new NioCoSocket(scheduler);
            Coroutine co = c -> {
                try {
                    debug("%s connect to localhost:%s", socket, port);
                    socket.connect(c, port);
                    debug("Connected: %s", socket);
                    byte[] b = new byte[512];

                    for (int j = 0; j < requests; ++j) {
                        socket.write(c, b);
                        socket.flush(c);
                        socket.readFully(c, b);
                        // Business time
                        scheduler.await(c, 1);
                    }
                } finally {
                    socket.close();
                    if (counter.addAndGet(1) >= conns) {
                        scheduler.shutdown();
                    }
                }
            };
            CoStarter.start(co, socket);
        }

        scheduler.run();
        long te = System.currentTimeMillis();
        info("Client: time %dms", te - ts);
    }

    static {
        System.setProperty("io.co.debug", "false");
        System.setProperty("io.co.soTimeout", "30000");
    }

}
