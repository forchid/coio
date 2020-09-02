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
package io.co.nio;

import io.co.*;
import io.co.util.IoUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * A NIO implementation of CoServerSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoServerSocket extends CoServerSocket implements NioCoChannel<ServerSocketChannel> {
    
    protected ServerSocketChannel channel;
    protected CoroutineRunner coRunner;

    protected final boolean autoShutdownScheduler;
    private int id = -1;
    
    public NioCoServerSocket(Class<? extends SocketHandler> connectorClass) {
        this(DefaultAcceptor.class, connectorClass);
    }
    
    public NioCoServerSocket(int port,  Class<? extends SocketHandler> connectorClass) {
        this(port, BACKLOG_DEFAULT, null, DefaultAcceptor.class, connectorClass);
    }
    
    public NioCoServerSocket(int port, int backlog,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, backlog, null, DefaultAcceptor.class, connectorClass);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, backlog, bindAddress, DefaultAcceptor.class, connectorClass);
    }
    
    public NioCoServerSocket(Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass) {
        this(PORT_UNDEFINED, BACKLOG_DEFAULT, null, acceptorClass, connectorClass);
    }
    
    public NioCoServerSocket(Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             NioCoScheduler coScheduler) {
        this(PORT_UNDEFINED, BACKLOG_DEFAULT, null, acceptorClass, connectorClass, coScheduler);
    }
    
    public NioCoServerSocket(int port,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, BACKLOG_DEFAULT, null, acceptorClass, connectorClass);
    }
    
    public NioCoServerSocket(int port,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             NioCoScheduler coScheduler) {
        this(port, BACKLOG_DEFAULT, null, acceptorClass, connectorClass, coScheduler);
    }
    
    public NioCoServerSocket(int port, int backlog,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             NioCoScheduler coScheduler) {
        this(port, backlog, null, acceptorClass, connectorClass, coScheduler);
    }

    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, backlog, bindAddress, acceptorClass, connectorClass, newScheduler(port), true);
    }

    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             NioCoScheduler coScheduler) {
        this(port, backlog, bindAddress, acceptorClass, connectorClass, coScheduler, false);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends ServerSocketHandler> acceptorClass,
                             Class<? extends SocketHandler> connectorClass,
                             NioCoScheduler coScheduler, boolean autoShutdownScheduler) {
        super(port, backlog, bindAddress, acceptorClass, connectorClass, coScheduler);
        this.autoShutdownScheduler = autoShutdownScheduler;
        initialize(port, backlog, bindAddress);
    }
    
    private void initialize(int port, int backlog, InetAddress bindAddress) throws CoIOException {
        // 1. Initialize server socket
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            this.channel = ssChan;
            this.coRunner = new CoroutineRunner(this.coAcceptor);
            failed = false;
        } catch (final IOException cause){
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
        }
        
        // 2. Try to bind
        if(port != PORT_UNDEFINED) {
            try {
                failed = true;
                SocketAddress sa = new InetSocketAddress(bindAddress, port);
                Future<?> bf = bind(sa, backlog);
                bf.get();
                failed = false;
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CoIOException(e + "");
            } catch(ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof CoIOException) {
                    throw (CoIOException)cause;
                } else if (cause instanceof IOException) {
                    throw new CoIOException("Bind failed", (IOException)cause);
                } else {
                    throw new IllegalStateException("Bind failed", cause);
                }
            } finally {
                if (failed) {
                    IoUtils.close(ssChan);
                    tryShutdownScheduler();
                }
            }
        }
    }

    public boolean isAutoShutdownScheduler() {
        return this.autoShutdownScheduler;
    }

    protected void tryShutdownScheduler() {
        if (isAutoShutdownScheduler()) {
            CoScheduler scheduler = getScheduler();
            scheduler.shutdown();
        }
    }
    
    @Override
    public int id(){
        return this.id;
    }
    
    @Override
    public NioCoServerSocket id(int id){
        if(this.id >= 0){
            throw new IllegalStateException("id had been set");
        }
        this.id = id;
        return this;
    }
    
    @Override
    public ServerSocketChannel channel(){
        return this.channel;
    }
    
    @Override
    public CoroutineRunner coRunner() {
        return this.coRunner;
    }
    
    @Override
    public NioCoScheduler getScheduler(){
        return (NioCoScheduler)super.getScheduler();
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }
    
    @Override
    public void close() {
        IoUtils.close(this.channel);
        super.close();
        tryShutdownScheduler();
    }
    
    @Override
    public InetAddress getInetAddress() {
        return this.channel.socket().getInetAddress();
    }
    
    @Override
    public int getLocalPort() {
        return this.channel.socket().getLocalPort();
    }
    
    @Override
    public SocketAddress getLocalSocketAddress() throws CoIOException {
        try {
            return this.channel.getLocalAddress();
        } catch (final IOException e) {
            throw new CoIOException(e);
        }
    }

    public static NioCoScheduler newScheduler(int port) {
        NioCoScheduler scheduler = null;
        boolean failed = true;
        try {
            final String name;
            if (port > 0) {
                name = "nio-" + port;
            } else {
                name = "nio-server";
            }
            scheduler = new NioCoScheduler(name);
            scheduler.start();
            failed = false;

            return scheduler;
        } finally {
            if(failed && scheduler != null) scheduler.shutdown();
        }
    }
    
    public static void startAndServe(Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint) throws CoIOException {
        startAndServe(connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint, int backlog)
            throws CoIOException {
        startAndServe(DefaultAcceptor.class, connectorClass, endpoint, backlog);
    }
    
    public static void startAndServe(Class<? extends ServerSocketHandler> acceptorClass,
                                     Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint) throws CoIOException {
        startAndServe(acceptorClass, connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends ServerSocketHandler> acceptorClass,
                                     Class<? extends SocketHandler> connectorClass,
                                     SocketAddress endpoint, int backlog)
            throws CoIOException {
        final NioCoScheduler scheduler = new NioCoScheduler("nio-" + endpoint);
        NioCoServerSocket ssSocket = null;
        boolean failed = true;
        try {
            ssSocket = new NioCoServerSocket(acceptorClass, connectorClass, scheduler);
            ssSocket.bind(endpoint, backlog).get();
            // Boot itself
            scheduler.startAndServe();
            failed = false;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoIOException(e + "");
        } catch(ExecutionException e) {
            throw new CoIOException(e.getCause() + "");
        } finally {
            if(failed){
                IoUtils.close(ssSocket);
            }
            scheduler.shutdown();
        }
    }
    
}