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
import io.co.CoSocket;
import io.co.CoStarter;
import io.co.util.RuntimeUtils;
import static io.co.util.LogUtils.*;
import junit.framework.TestCase;

import java.io.IOException;

public class ServerSocketTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ServerSocketTest().allTest();
    }

    public void allTest() throws Exception {
        testBind();
    }

    public void testBind() throws Exception {
        boolean linux = RuntimeUtils.isLinux();

        if (linux) {
            NioCoServerSocket server = new NioCoServerSocket();
            try {
                int port = 999;
                server.bind(port);
                fail("Bind should be failed for permission issue");
            } catch (IOException e) {
                // ok
                server.close();
            }
        }

        int port = 19999;
        NioCoServerSocket server = new NioCoServerSocket();
        NioScheduler scheduler = server.getScheduler();
        assertFalse(scheduler.isShutdown());

        Coroutine so = c -> {
            CoSocket socket = server.accept(c);
            info("%s accepted", socket);
            socket.close();
            server.close();
            scheduler.shutdown();
        };
        server.bind(port);
        CoStarter.start(so, server);

        CoSocket client = new NioCoSocket(scheduler);
        Coroutine co = c -> {
            client.connect(c, port);
            info("%s connect to %s", client, port);
            client.close();
            server.close();
        };
        CoStarter.start(co, client);

        scheduler.run();
        assertTrue(scheduler.isShutdown());
    }

}
