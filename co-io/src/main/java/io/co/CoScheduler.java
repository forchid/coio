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

import com.offbynull.coroutines.user.Continuation;

/**
 * The coroutine scheduler.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public interface CoScheduler {
    
    boolean DEBUG = Boolean.getBoolean("io.co.debug");
    
    int INIT_CONNECTIONS = Integer.getInteger("io.co.initConnections", 1024);
    int MAX_CONNECTIONS  = Integer.getInteger("io.co.maxConnections", 102400);
    int CHILD_COUNT      = Integer.getInteger("io.co.scheduler.childCount", 2);
    
    void start();
    
    void startAndServe();
    
    boolean isStarted();
    
    CoSocket accept(Continuation co, CoServerSocket coServerSocket)
        throws CoIOException;
    
    void bind(CoServerSocket coServerSocket, SocketAddress endpoint, int backlog)
        throws CoIOException;
    
    void connect(CoSocket coSocket, SocketAddress remote)
        throws IOException;
    
    void connect(CoSocket coSocket, SocketAddress remote, int timeout)
        throws IOException;
    
    void schedule(CoSocket coSocket, Runnable task, long delay);
    
    void schedule(CoSocket coSocket, Runnable task, long delay, long period);
    
    void execute(Runnable task) throws CoIOException;
    
    void wait(Continuation co, long millis);
    
    boolean inScheduler();
    
    void close(CoChannel coChannel);
    
    boolean isStopped();
    
    boolean isShutdown();
    
    void await() throws InterruptedException;
    
    void await(long millis) throws InterruptedException;
    
    void shutdown();
    
}
