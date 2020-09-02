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

import io.co.nio.NioCoSocket;

/**
 * A socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoSocket implements CoChannel {
    
    protected static final int SO_TIMEOUT = Integer.getInteger("io.co.soTimeout", 0);
    
    protected final Coroutine coConnector;
    protected final CoScheduler coScheduler;
    private int soTimeout = SO_TIMEOUT;
    
    protected CoSocket(Coroutine coConnector, CoScheduler coScheduler) {
        this.coConnector = coConnector;
        this.coScheduler = coScheduler;
    }
    
    public Coroutine getConnector(){
        return this.coConnector;
    }
    
    public CoScheduler getScheduler(){
        return this.coScheduler;
    }
    
    public int getSoTimeout(){
        return this.soTimeout;
    }
    
    public void setSoTimeout(int soTimeout){
        if(soTimeout < 0){
            throw new IllegalArgumentException("soTimeout " + soTimeout);
        }
        this.soTimeout = soTimeout;
    }
    
    public abstract void connect(SocketAddress endpoint) throws IOException;
    
    public abstract void connect(SocketAddress endpoint, int timeout) throws IOException;
    
    public abstract CoInputStream getInputStream();
    
    public abstract CoOutputStream getOutputStream();
    
    public abstract boolean isConnected();
    
    @Override
    public void close() {
        this.coScheduler.close(this);
    }
    
    public static void startAndServe(Coroutine coConnector, SocketAddress remote)
            throws CoIOException {
        NioCoSocket.startAndServe(coConnector, remote);
    }
    
    public static void startAndServe(Coroutine coConnector, SocketAddress remote, int timeout)
            throws CoIOException {
        NioCoSocket.startAndServe(coConnector, remote, timeout);
    }
    
}
