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

import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class EchoPerfTest extends TestCase {

    public static void main(String[] args) throws Exception {
        EchoPerfTest test = new EchoPerfTest();
        test.testPerf();
    }

    public void testPerf() throws Exception {
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        int conns = 1000, requestsPerConn = 1000;
        System.setProperty("io.co.initConnections", conns + "");
        System.setProperty("io.co.maxConnections", (conns + 1) + "");

        CountDownLatch startLatch = new CountDownLatch(1);
        Thread server = new Thread(() -> {
            try {
                startLatch.countDown();
                EchoServer.main(new String[]{});
            } catch (Throwable e) {
                causeRef.set(e);
                throw new AssertionError(e);
            }
        }, "echo-server");
        server.start();

        Thread client = new Thread(() -> {
            try {
                startLatch.await();
                String[] args = { conns + "", requestsPerConn + "" };
                EchoClient.main(args);
            } catch (Throwable e) {
                causeRef.set(e);
                throw new AssertionError(e);
            }
        }, "echo-client");
        client.start();

        client.join();
        EchoServer.shutdown();
        server.join();

        // Check
        if (causeRef.get() != null) {
            throw new AssertionError(causeRef.get());
        }
    }

}
