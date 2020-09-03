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

import io.co.CoInputStream;
import io.co.CoOutputStream;
import io.co.CoSocket;
import io.co.SocketHandler;
import io.co.util.IoUtils;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import com.offbynull.coroutines.user.Continuation;

import static io.co.nio.NioCoScheduler.*;

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
        final SocketAddress remote = new InetSocketAddress(host, PORT);
        
        // Parallel scheduler
        final NioCoScheduler[] schedulers = new NioCoScheduler[schedulerCount];
        final AtomicInteger []remains = new AtomicInteger[schedulerCount];
        for (int i = 0; i < schedulers.length; ++i) {
            final String name = "nio-"+ i;
            schedulers[i] = new NioCoScheduler(name, connectionCount, connectionCount, 0);
            schedulers[i].start();
            remains[i] = new AtomicInteger();
        }
        
        final AtomicInteger successCount = new AtomicInteger();
        try {
            for(int i = 0; i < connectionCount; ++i){
                final int j = i % schedulers.length;
                final NioCoScheduler scheduler = schedulers[j];
                final AtomicInteger remain = remains[j];
                final SocketHandler connector = new Connector(i, successCount, remain, scheduler);
                final CoSocket sock = new NioCoSocket(connector, scheduler);
                sock.connect(remote, 30000);
                remain.incrementAndGet();
            }
        } finally {
            for(final NioCoScheduler s : schedulers){
                s.awaitTermination();
            }
        }
        
        System.out.println(String.format("Bye: connectionCount = %s, successCount = %s, time = %sms",
                connectionCount, successCount, System.currentTimeMillis() - ts));
    }
    
    static class Connector implements SocketHandler {
        private static final long serialVersionUID = 1L;
        
        final NioCoScheduler scheduler;
        final AtomicInteger success;
        final AtomicInteger remain;
        final int id;
        
        Connector(int id, AtomicInteger success, AtomicInteger remain, NioCoScheduler scheduler){
            this.scheduler = scheduler;
            this.success   = success;
            this.remain    = remain;
            this.id = id;
        }

        @Override
        public void handle(Continuation co, CoSocket socket) throws Exception {
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

                    //System.out.println(String.format("written %d, reads %d ", written, reads));
                }
                success.incrementAndGet();
                System.out.println(String.format("[%s] Client-%05d: time %dms",
                     Thread.currentThread().getName(), id, (System.currentTimeMillis() - ts)));
            } finally {
                remain.decrementAndGet();
                IoUtils.close(socket);
                // Shutdown only when all connection completed
                if(remain.compareAndSet(0, 0)){
                    scheduler.shutdown();
                }
            }
        }
        
    }

}
