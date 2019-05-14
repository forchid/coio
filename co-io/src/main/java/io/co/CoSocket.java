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

import com.offbynull.coroutines.user.Coroutine;

/**
 * A socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoSocket implements CoChannel {
    
    protected final Coroutine coConnector;
    protected final CoScheduler coScheduler;
    
    protected CoSocket(Coroutine coConnector, CoScheduler coScheduler) {
        this.coConnector = coConnector;
        this.coScheduler = coScheduler;
    }
    
    public Coroutine getCoConnector(){
        return this.coConnector;
    }
    
    public CoScheduler getCoScheduler(){
        return this.coScheduler;
    }
    
    public abstract void connect(SocketAddress endpoint) throws IOException;
    
    public abstract void connect(SocketAddress endpoint, int timeout) throws IOException;
    
    public abstract CoInputStream getInputStream();
    
    public abstract CoOutputStream getOutputStream();
    
    @Override
    public void close() {
        this.coScheduler.close(this);
    }
    
}
