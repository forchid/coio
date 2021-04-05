/*
 * Copyright (c) 2021, little-pan, All rights reserved.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;

/**
 * A socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoSocket implements CoChannel {
    
    protected static final int SO_TIMEOUT = Integer.getInteger("io.co.soTimeout", 0);

    private int soTimeout = SO_TIMEOUT;

    protected InetAddress address;
    protected int port;

    protected CoSocket() {

    }
    
    public int getSoTimeout(){
        return this.soTimeout;
    }
    
    public void setSoTimeout(int soTimeout) throws IllegalArgumentException {
        if(soTimeout < 0){
            throw new IllegalArgumentException("soTimeout " + soTimeout);
        }
        this.soTimeout = soTimeout;
    }

    public InetAddress getAddress() {
        return this.address;
    }

    public int getPort() {
        return this.port;
    }

    public void connect(Continuation co, int port) throws IOException {
        SocketAddress endpoint = new InetSocketAddress(port);
        connect(co, endpoint, this.soTimeout);
    }

    public void connect(Continuation co, String host, int port) throws IOException {
        connect(co, host, port, this.soTimeout);
    }

    public void connect(Continuation co, String host, int port, int timeout)
            throws IOException {
        SocketAddress endpoint = new InetSocketAddress(host, port);
        connect(co, endpoint, timeout);
    }

    public void connect(Continuation co, InetAddress address, int port)
            throws IOException {
        connect(co, address, port, this.soTimeout);
    }

    public void connect(Continuation co, InetAddress address, int port, int timeout)
            throws IOException {
        SocketAddress endpoint = new InetSocketAddress(address, port);
        connect(co, endpoint, timeout);
    }
    
    public void connect(Continuation co, SocketAddress endpoint) throws IOException {
        connect(co, endpoint, this.soTimeout);
    }
    
    public abstract void connect(Continuation co, SocketAddress endpoint, int timeout)
            throws IOException;
    
    public abstract CoInputStream getInputStream();
    
    public abstract CoOutputStream getOutputStream();

    public abstract boolean isConnected();
    
}
