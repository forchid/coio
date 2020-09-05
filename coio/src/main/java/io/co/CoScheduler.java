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
package io.co;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;

import com.offbynull.coroutines.user.Continuation;
import static io.co.util.RuntimeUtils.*;

/**
 * The coroutine scheduler.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public interface CoScheduler {
    
    int INIT_CONNECTIONS = Integer.getInteger("io.co.initConnections", 1024);
    int MAX_CONNECTIONS  = Integer.getInteger("io.co.maxConnections",  102400);
    int CHILDREN_COUNT   = Integer.getInteger("io.co.scheduler.childrenCount", processors());
    String NAME          = System.getProperty("io.co.scheduler.name", "co-scheduler");
    boolean DAEMON       = Boolean.getBoolean("io.co.scheduler.daemon");
    
    String getName();

    boolean isDaemon();
    
    void start();
    
    void startAndServe();
    
    boolean isStarted();
    
    Future<Void> bind(CoServerSocket serverSocket, SocketAddress endpoint, int backlog)
        throws CoIOException;
    
    void connect(CoSocket socket, SocketAddress remote)
        throws IOException;
    
    void connect(CoSocket socket, SocketAddress remote, int timeout)
        throws IOException;
    
    void schedule(CoSocket socket, Runnable task, long delay);
    
    void schedule(CoSocket socket, Runnable task, long delay, long period);
    
    Future<Void> execute(Runnable task) throws CoIOException;
    
    <V> Future<V> execute(Runnable task, V value) throws CoIOException;
    
    void await(Continuation co, long millis);
    
    boolean inScheduler();

    default void ensureInScheduler() throws IllegalStateException {
        if (!inScheduler()) {
            String s = "The current thread not this scheduler";
            throw new IllegalStateException(s);
        }
    }
    
    void close(CoChannel socket);
    
    boolean isTerminated();
    
    boolean isShutdown();
    
    void awaitTermination() throws InterruptedException;
    
    boolean awaitTermination(long millis) throws InterruptedException;
    
    void shutdown();

}
