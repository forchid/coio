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

import io.co.*;
import io.co.util.IoUtils;

import java.io.EOFException;
import java.util.concurrent.atomic.AtomicInteger;

import com.offbynull.coroutines.user.Continuation;

import static io.co.util.LogUtils.*;

/**
 * A simple CoSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class EchoClient {

    static final int PORT = Integer.getInteger("io.co.port", 9999);

    public static void main(String[] args) throws Exception {
        System.setProperty("io.co.soTimeout", "30000");
        final String host = System.getProperty("io.co.host", "localhost");
        
        final int connectionCount, schedulerCount;
        if(args.length > 0){
            connectionCount = Integer.parseInt(args[0]);
        }else{
            connectionCount = 250;
        }
        schedulerCount = Math.min(4, connectionCount);
        
        final long ts = System.currentTimeMillis();
        
        // Parallel scheduler
        final NioScheduler[] schedulers = new NioScheduler[schedulerCount];
        final AtomicInteger []remains = new AtomicInteger[schedulerCount];
        for (int i = 0; i < schedulers.length; ++i) {
            final String name = "nio-"+ i;
            schedulers[i] = new NioScheduler(name, connectionCount, connectionCount, 0);
            schedulers[i].start();
            remains[i] = new AtomicInteger();
        }
        
        final AtomicInteger successCount = new AtomicInteger();
        try {
            for(int i = 0; i < connectionCount; ++i){
                final int j = i % schedulers.length;
                final NioScheduler scheduler = schedulers[j];
                final AtomicInteger remain = remains[j];
                final SocketHandler connector = new EchoHandler(i, successCount, remain, scheduler);
                new NioCoSocket(host, PORT, connector, scheduler);
                remain.incrementAndGet();
            }
        } finally {
            for(final NioScheduler s : schedulers){
                s.awaitTermination();
            }
        }
        
        info("Bye: connectionCount = %s, successCount = %s, time = %sms",
                connectionCount, successCount, System.currentTimeMillis() - ts);
    }
    
    static class EchoHandler extends Connector {
        private static final long serialVersionUID = 1L;
        
        final NioScheduler scheduler;
        final AtomicInteger success;
        final AtomicInteger remain;
        final int id;

        EchoHandler(int id, AtomicInteger success, AtomicInteger remain, NioScheduler scheduler){
            this.scheduler = scheduler;
            this.success   = success;
            this.remain    = remain;
            this.id = id;
        }

        @Override
        public void handleConnection(Continuation co, CoSocket socket) throws Exception {
            try {
                debug("Connected: %s", socket);
                final long ts = System.currentTimeMillis();
                final CoInputStream in = socket.getInputStream();
                final CoOutputStream out = socket.getOutputStream();
                
                final byte[] b = new byte[512];
                final int requests = 100;
                for(int i = 0; i < requests; ++i) {
                    out.write(co, b);
                    final int written = b.length;
                    out.flush(co);
                    
                    int reads = 0;
                    while (reads < written) {
                        final int n = in.read(co, b, reads, b.length - reads);
                        if(n == -1) {
                            throw new EOFException();
                        }
                        reads += n;
                    }
                }
                this.success.incrementAndGet();
                info("Client-%05d: time %dms", id, (System.currentTimeMillis() - ts));
            } finally {
                IoUtils.close(socket);
            }
            tryShutdown();
        }

        void tryShutdown() {
            this.remain.decrementAndGet();
            // Shutdown only when all connection completed
            if (this.remain.compareAndSet(0, 0)) {
                this.scheduler.shutdown();
            }
        }

        @Override
        public void exceptionCaught(Throwable cause) {
            try {
                super.exceptionCaught(cause);
            } finally {
                tryShutdown();
            }
        }
        
    }

}
