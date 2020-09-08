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

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

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

    protected InetAddress address;
    protected int port = PORT_UNDEFINED;

    protected final Scheduler scheduler;
    protected final SocketHandler coConnector;
    private int soTimeout = SO_TIMEOUT;

    protected CoSocket(InetAddress address, int port,
                       SocketHandler connector, Scheduler scheduler) {

        this(connector, scheduler);
        checkPort(port);
        this.address = address;
        this.port = port;
    }

    protected CoSocket(String host, int port, SocketHandler connector, Scheduler scheduler)
            throws CoIOException {

        this(connector, scheduler);
        checkPort(port);
        try {
            this.address = InetAddress.getByName(host);
            this.port = port;
        } catch (UnknownHostException e) {
            throw new CoIOException(e);
        }
    }

    protected CoSocket(SocketHandler connector, Scheduler scheduler) {
        this.scheduler = scheduler;
        this.coConnector = connector;
    }

    static void checkPort(int port) throws IllegalArgumentException {
        if((port < 0 && PORT_UNDEFINED != port) || port > 65535) {
            throw new IllegalArgumentException("port " + port);
        }
    }
    
    public SocketHandler getConnector(){
        return this.coConnector;
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

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
    
    public void connect(Continuation co, SocketAddress endpoint) throws CoIOException {
        connect(co, endpoint, 0);
    }
    
    public abstract void connect(Continuation co, SocketAddress endpoint, int timeout)
            throws CoIOException;
    
    public abstract CoInputStream getInputStream();
    
    public abstract CoOutputStream getOutputStream();
    
    public abstract boolean isConnected();

    @Override
    public Scheduler getScheduler() {
        return this.scheduler;
    }
    
    @Override
    public void close() {
        this.scheduler.close(this);
    }
    
}
