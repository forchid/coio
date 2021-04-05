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
 * The server socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoServerSocket implements CoChannel {
    
    protected static final int BACKLOG_DEFAULT = 150;

    protected int port;
    protected InetAddress bindAddress;
    protected int backlog = BACKLOG_DEFAULT;
    protected volatile boolean bound;
    
    protected CoServerSocket() {

    }

    public int getPort() {
        return port;
    }

    public InetAddress getBindAddress() {
        return bindAddress;
    }

    public int getBacklog() {
        return backlog;
    }

    public boolean isBound() {
        return this.bound;
    }

    public abstract SocketAddress getLocalAddress() throws CoIOException;

    @Override
    public abstract boolean isOpen();

    public boolean isClosed() {
        return !isOpen();
    }

    public abstract CoSocket accept(Continuation co)
            throws IOException, IllegalStateException;

    public void bind(int port) throws IOException {
        bind(port, this.backlog);
    }

    public void bind(int port, int backlog) throws IOException {
        SocketAddress endpoint = new InetSocketAddress(port);
        bind(endpoint, backlog);
    }

    public void bind(String host, int port) throws IOException {
        bind(host, port, this.backlog);
    }

    public void bind(String host, int port, int backlog) throws IOException {
        SocketAddress endpoint = new InetSocketAddress(host, port);
        bind(endpoint, backlog);
    }
    
    public void bind(SocketAddress endpoint) throws IOException {
        bind(endpoint, this.backlog);
    }
    
    public abstract void bind(SocketAddress endpoint, int backlog)
            throws IOException;
    
    public abstract InetAddress getInetAddress();
    
    public abstract int getLocalPort();
    
    public abstract SocketAddress getLocalSocketAddress() throws IOException;

    @Override
    public String toString() {
        return "co-server";
    }

}
