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
    
    void start();
    
    CoSocket accept(Continuation co, CoServerSocket coServerSocket)
        throws CoIOException;
    
    void bind(CoServerSocket coServerSocket, SocketAddress endpoint, int backlog)
        throws IOException;
    
    void connect(CoSocket coSocket, SocketAddress endpoint, int timeout)
        throws IOException;
    
    void close(CoChannel coChannel);
    
    boolean isStopped();
    
    boolean isShutdown();
    
    void shutdown();
}
