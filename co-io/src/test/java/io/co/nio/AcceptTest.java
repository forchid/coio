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

import io.co.CoSocket;
import io.co.util.IoUtils;
import junit.framework.TestCase;

/**
 * @author little-pan
 * @since 2019-05-26
 *
 */
public class AcceptTest extends TestCase {

    public void testAccept() throws Exception {
        System.setProperty("io.co.debug", "true");
        final NioCoServerSocket ssock = new NioCoServerSocket(996);
        
        final NioCoScheduler sched = ssock.getScheduler();
        
        final Coroutine connector = new Coroutine() {
            private static final long serialVersionUID = -5685704838735047546L;

            @Override
            public void run(Continuation co) throws Exception {
                final Object ctx = co.getContext();
                try {
                    if(ctx instanceof Throwable) {
                        final Throwable cause = (Throwable)ctx;
                        cause.printStackTrace();
                        return;
                    }
                    System.out.println(Thread.currentThread().getName()+": accepted");
                    final CoSocket sock = (CoSocket)ctx;
                    IoUtils.close(sock);
                } finally {
                    sched.shutdown();
                }
            }
            
        };
        
        final CoSocket sock = new NioCoSocket(connector, sched);
        sock.connect(ssock.getLocalSocketAddress());
        
        System.out.println("wait");
        sched.awaitTermination();
        sock.close();
        ssock.close();
        
        System.out.println("OK");
    }
    
}
