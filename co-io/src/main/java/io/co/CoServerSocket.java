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
import com.offbynull.coroutines.user.Coroutine;

/**
 * The server socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoServerSocket implements CoChannel {
    protected final Coroutine coAcceptor;
    protected final Coroutine coConnector;
    
    protected final CoScheduler coScheduler;
    
    protected CoServerSocket(Coroutine coAcceptor, Coroutine coConnector, CoScheduler coScheduler) {
        this.coAcceptor = coAcceptor;
        this.coConnector= coConnector;
        this.coScheduler= coScheduler;
    }
    
    public Coroutine getCoAcceptor(){
        return this.coAcceptor;
    }
    
    public Coroutine getCoConnector(){
        return this.coConnector;
    }
    
    public CoScheduler getCoScheduler(){
        return this.coScheduler;
    }
    
    @Override
    public abstract boolean isOpen();
    
    public void bind(SocketAddress endpoint) throws IOException {
        bind(endpoint, 150);
    }
    
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        this.coScheduler.bind(this, endpoint, backlog);
    }
    
    public CoSocket accept(Continuation co) {
        return this.coScheduler.accept(co, this);
    }
 
    @Override
    public void close(){
        this.coScheduler.close(this);
    }
    
}

