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
import java.util.concurrent.Future;

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
    protected static final int PORT_UNDEFINED  = -1;

    protected final CoScheduler scheduler;

    protected final Class<? extends ServerSocketHandler> acceptorClass;
    protected final ServerSocketHandler coAcceptor;

    protected final Class<? extends SocketHandler> connectorClass;
    
    protected CoServerSocket(Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             CoScheduler scheduler) {

        if (scheduler == null) throw new NullPointerException();

        this.scheduler = scheduler;
        this.acceptorClass = acceptorClass;
        this.connectorClass = connectorClass;
        this.coAcceptor = ReflectUtils.newObject(acceptorClass);
    }
    
    protected CoServerSocket(int port, Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             CoScheduler coScheduler) {
        this(port, BACKLOG_DEFAULT, null, acceptorClass, connectorClass, coScheduler);
    }
    
    protected CoServerSocket(int port, int backlog,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             CoScheduler coScheduler) {
        this(port, backlog, null, acceptorClass, connectorClass, coScheduler);
    }
    
    protected CoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             CoScheduler scheduler) {

        if (scheduler == null) throw new NullPointerException();
        this.scheduler = scheduler;
        if((port < 0 && PORT_UNDEFINED != port) || port > 65535) {
            throw new IllegalArgumentException("port " + port);
        }

        this.acceptorClass  = acceptorClass;
        this.connectorClass = connectorClass;
        this.coAcceptor     = ReflectUtils.newObject(acceptorClass);
    }
    
    public Coroutine getAcceptor(){
        return this.coAcceptor;
    }
    
    public Class<? extends SocketHandler> getConnectorClass(){
        return this.connectorClass;
    }

    @Override
    public CoScheduler getScheduler() {
        return this.scheduler;
    }

    @Override
    public abstract boolean isOpen();
    
    public boolean isClosed() {
        return !isOpen();
    }
    
    public Future<Void> bind(SocketAddress endpoint) throws CoIOException {
        return bind(endpoint, BACKLOG_DEFAULT);
    }
    
    public Future<Void> bind(SocketAddress endpoint, int backlog) throws CoIOException {
        CoScheduler scheduler = getScheduler();
        return scheduler.bind(this, endpoint, backlog);
    }
    
    public CoSocket accept(Continuation co) {
        CoScheduler scheduler = getScheduler();
        return scheduler.accept(co, this);
    }
    
    public abstract InetAddress getInetAddress();
    
    public abstract int getLocalPort();
    
    public abstract SocketAddress getLocalSocketAddress() throws CoIOException ;
 
    @Override
    public void close() {
        CoScheduler scheduler = getScheduler();
        scheduler.close(this);
    }
    
    public static void startAndServe(Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint)
            throws CoIOException {
        NioCoServerSocket.startAndServe(connectorClass, endpoint);
    }
    
    public static void startAndServe(Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint, int backlog)
            throws CoIOException {
        NioCoServerSocket.startAndServe(connectorClass, endpoint, backlog);
    }
    
    public static void startAndServe(Class<? extends ServerSocketHandler> acceptorClass,
                                     Coroutine coConnector, SocketAddress endpoint)
            throws CoIOException {
        NioCoServerSocket.startAndServe(acceptorClass, coConnector, endpoint);
    }
    
    public static void startAndServe(Class<? extends ServerSocketHandler> acceptorClass,
                                     Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint,
            int backlog) throws CoIOException {
        NioCoServerSocket.startAndServe(acceptorClass, connectorClass, endpoint, backlog);
    }
    
}

