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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.offbynull.coroutines.user.Continuation;

import static io.co.util.LogUtils.*;

/**
 * The server socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoServerSocket implements CoChannel {
    
    protected static final int BACKLOG_DEFAULT = 150;

    protected final CountDownLatch closeLatch;
    protected int port;
    protected InetAddress bindAddress;
    protected final int backlog;
    protected volatile boolean bound;
    protected volatile Throwable cause;

    protected final Scheduler scheduler;
    protected final ServerSocketHandler acceptor;
    protected final Class<? extends SocketHandler> connectorClass;
    
    protected CoServerSocket(Class<? extends SocketHandler> connectorClass,
                             Scheduler scheduler, ServerSocketHandler acceptor) {
        this(PORT_UNDEFINED, null, BACKLOG_DEFAULT, connectorClass, scheduler, acceptor);
    }

    protected CoServerSocket(int port, Class<? extends SocketHandler> connectorClass,
                             Scheduler scheduler, ServerSocketHandler acceptor) {
        this(port, null, BACKLOG_DEFAULT, connectorClass, scheduler, acceptor);
    }

    protected CoServerSocket(int port, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass,
                             Scheduler scheduler, ServerSocketHandler acceptor) {
        this(port, bindAddress, BACKLOG_DEFAULT, connectorClass, scheduler, acceptor);
    }

    protected CoServerSocket(int port, int backlog,
                             Class<? extends SocketHandler> connectorClass,
                             Scheduler scheduler, ServerSocketHandler acceptor) {
        this(port, null, backlog, connectorClass, scheduler, acceptor);
    }
    
    protected CoServerSocket(int port, InetAddress bindAddress, int backlog,
                             Class<? extends SocketHandler> connectorClass,
                             Scheduler scheduler, ServerSocketHandler acceptor) {

        if (scheduler == null) throw new NullPointerException();
        this.scheduler = scheduler;
        if((port < 0 && PORT_UNDEFINED != port) || port > 65535) {
            throw new IllegalArgumentException("port " + port);
        }

        this.port = port;
        this.bindAddress = bindAddress;
        this.backlog = backlog;
        this.connectorClass = connectorClass;
        this.acceptor = acceptor;

        this.closeLatch = new CountDownLatch(1);
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

    public void awaitClosed() throws InterruptedException, IllegalStateException {
        this.closeLatch.await();
        checkCause();
    }

    public boolean awaitClosed(long timeout, TimeUnit timeUnit)
            throws InterruptedException, IllegalStateException {
        boolean result = this.closeLatch.await(timeout, timeUnit);
        checkCause();
        return result;
    }

    protected void checkCause() throws IllegalStateException {
        Throwable cause = this.cause;
        if (cause != null) throw new IllegalStateException(cause);
    }

    public boolean isBound() {
        return this.bound;
    }

    public abstract SocketAddress getLocalAddress() throws CoIOException;
    
    public Class<? extends SocketHandler> getConnectorClass(){
        return this.connectorClass;
    }

    @Override
    public Scheduler getScheduler() {
        return this.scheduler;
    }

    @Override
    public abstract boolean isOpen();

    public boolean isClosed() {
        return !isOpen();
    }
    
    public void bind(Continuation co, SocketAddress endpoint) throws CoIOException {
        bind(co, endpoint, BACKLOG_DEFAULT);
    }
    
    public abstract void bind(Continuation co, SocketAddress endpoint, int backlog)
            throws CoIOException;

    protected void checkCoContext(Continuation co) throws IllegalStateException {
        if (co.getContext() != this) {
            String s = "The continuation context not this server socket";
            throw new IllegalStateException(s);
        }
        Scheduler scheduler = getScheduler();
        scheduler.ensureInScheduler();
    }
    
    public void accept(Continuation co) throws IllegalStateException {
        checkCoContext(co);

        debug("accept() ->");
        co.suspend();
        debug("accept() <-");
    }
    
    public abstract InetAddress getInetAddress();
    
    public abstract int getLocalPort();
    
    public abstract SocketAddress getLocalSocketAddress() throws CoIOException ;
 
    @Override
    public void close() {
        Scheduler scheduler = getScheduler();
        scheduler.close(this);
        this.closeLatch.countDown();
    }

    @Override
    public String toString() {
        return "co-server";
    }
    
}

