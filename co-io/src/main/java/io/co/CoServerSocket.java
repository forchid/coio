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

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

import io.co.nio.NioCoServerSocket;
import io.co.util.ReflectUtils;

/**
 * The server socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoServerSocket implements CoChannel {
    
    protected static final int BACKLOG_DEFAULT = 150;
    
    protected final Class<? extends Coroutine> acceptorClass;
    protected final Coroutine coAcceptor;
    
    protected final Class<? extends Coroutine> connectorClass;
    
    protected CoScheduler coScheduler;
    
    protected CoServerSocket(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass) {
        this(acceptorClass, connectorClass, null);
    }
    
    protected CoServerSocket(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, CoScheduler coScheduler) {
        this.acceptorClass  = acceptorClass;
        this.connectorClass = connectorClass;
        this.coAcceptor     = ReflectUtils.newObject(acceptorClass);
        
        this.coScheduler    = coScheduler;
    }
    
    protected CoServerSocket(int port, Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass) {
        this(port, acceptorClass, connectorClass, null);
    }
    
    protected CoServerSocket(int port, Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, CoScheduler coScheduler) {
        this(port, BACKLOG_DEFAULT, null, acceptorClass, connectorClass, coScheduler);
    }
    
    protected CoServerSocket(int port, int backlog,
            Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, CoScheduler coScheduler) {
        this(port, backlog, null, acceptorClass, connectorClass, coScheduler);
    }
    
    protected CoServerSocket(int port, int backlog, InetAddress bindAddr, 
            Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, CoScheduler coScheduler) {
        if(port < 0 || port > 65535) {
            throw new IllegalArgumentException("port " + port);
        }
        
        this.acceptorClass  = acceptorClass;
        this.connectorClass = connectorClass;
        this.coScheduler    = coScheduler;
        this.coAcceptor     = ReflectUtils.newObject(acceptorClass);
    }
    
    public Coroutine getAcceptor(){
        return this.coAcceptor;
    }
    
    public Class<? extends Coroutine> getConnectorClass(){
        return this.connectorClass;
    }
    
    public CoScheduler getScheduler(){
        return this.coScheduler;
    }
    
    @Override
    public abstract boolean isOpen();
    
    public boolean isClosed() {
        return !isOpen();
    }
    
    public void bind(SocketAddress endpoint) {
        bind(endpoint, BACKLOG_DEFAULT);
    }
    
    public void bind(SocketAddress endpoint, int backlog) {
        this.coScheduler.bind(this, endpoint, backlog);
    }
    
    public CoSocket accept(Continuation co) {
        return this.coScheduler.accept(co, this);
    }
    
    public abstract InetAddress getInetAddress();
    
    public abstract int getLocalPort();
    
    public abstract SocketAddress getLocalSocketAddress() throws CoIOException ;
 
    @Override
    public void close(){
        this.coScheduler.close(this);
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint)
            throws CoIOException {
        NioCoServerSocket.startAndServe(connectorClass, endpoint);
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint, int backlog)
            throws CoIOException {
        NioCoServerSocket.startAndServe(connectorClass, endpoint, backlog);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Coroutine coConnector, SocketAddress endpoint)
            throws CoIOException {
        NioCoServerSocket.startAndServe(acceptorClass, coConnector, endpoint);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, SocketAddress endpoint,
            int backlog) throws CoIOException {
        NioCoServerSocket.startAndServe(acceptorClass, connectorClass, endpoint, backlog);
    }
    
}

