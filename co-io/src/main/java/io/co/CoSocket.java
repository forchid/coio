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

import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

/**
 * A socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoSocket implements AutoCloseable {
    
    protected Coroutine coroutine;
    
    protected CoSocket(Coroutine coroutine) {
        this.coroutine = coroutine;
    }
    
    public abstract void connect(Continuation co, SocketAddress endpoint) 
        throws CoIOException;
    
    public abstract void connect(Continuation co, SocketAddress endpoint, int timeout) 
        throws CoIOException;
    
    public abstract CoInputStream getInputStream();
    
    public abstract CoOutputStream getOutputStream();
    
    public abstract void close();
    
}
