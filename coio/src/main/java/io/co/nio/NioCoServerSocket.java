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

import com.offbynull.coroutines.user.Continuation;
import io.co.*;
import io.co.util.IoUtils;
import static io.co.util.LogUtils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * A NIO implementation of CoServerSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoServerSocket extends CoServerSocket implements NioCoChannel<ServerSocketChannel> {

    protected static String NAME_PREFIX = "nio-server";

    protected String name = NAME_PREFIX;
    protected ServerSocketChannel channel;
    protected CoroutineRunner coRunner;

    protected final boolean autoShutdownScheduler;
    private int id = -1;
    
    public NioCoServerSocket(Class<? extends SocketHandler> connectorClass) {
        this(connectorClass, new Acceptor());
    }
    
    public NioCoServerSocket(int port,  Class<? extends SocketHandler> connectorClass) {
        this(port, BACKLOG_DEFAULT, null, connectorClass, new Acceptor());
    }
    
    public NioCoServerSocket(int port, int backlog,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, backlog, null, connectorClass, new Acceptor());
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass) {
        this(port, backlog, bindAddress, connectorClass, new Acceptor());
    }
    
    public NioCoServerSocket(Class<? extends SocketHandler> connectorClass,
                             ServerSocketHandler acceptor) {
        this(PORT_UNDEFINED, BACKLOG_DEFAULT, null, connectorClass, acceptor);
    }
    
    public NioCoServerSocket(Class<? extends SocketHandler> connectorClass,
                             NioScheduler scheduler, ServerSocketHandler acceptor) {
        this(PORT_UNDEFINED, BACKLOG_DEFAULT, null, connectorClass, scheduler, acceptor);
    }
    
    public NioCoServerSocket(int port, Class<? extends SocketHandler> connectorClass,
                             ServerSocketHandler acceptor) {
        this(port, BACKLOG_DEFAULT, null, connectorClass, acceptor);
    }
    
    public NioCoServerSocket(int port, Class<? extends SocketHandler> connectorClass,
                             NioScheduler scheduler, ServerSocketHandler acceptor) {
        this(port, BACKLOG_DEFAULT, null, connectorClass, scheduler, acceptor);
    }
    
    public NioCoServerSocket(int port, int backlog, Class<? extends SocketHandler> connectorClass,
                             NioScheduler scheduler, ServerSocketHandler acceptor) {
        this(port, backlog, null, connectorClass, scheduler, acceptor);
    }

    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass,
                             ServerSocketHandler acceptor) {
        this(port, backlog, bindAddress, connectorClass, newScheduler(port), acceptor, true);
    }

    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass,
                             NioScheduler scheduler, ServerSocketHandler acceptor) {
        this(port, backlog, bindAddress, connectorClass, scheduler, acceptor, false);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddress,
                             Class<? extends SocketHandler> connectorClass,
                             NioScheduler scheduler, ServerSocketHandler acceptor,
                             boolean autoShutdownScheduler) {
        super(port, bindAddress, backlog, connectorClass, scheduler, acceptor);
        this.autoShutdownScheduler = autoShutdownScheduler;
        initialize(port);
    }
    
    private void initialize(int port) throws CoIOException {
        if (PORT_UNDEFINED != port) {
            this.name = NAME_PREFIX + "-" + port;
        }

        // Initialize server socket
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            this.channel = ssChan;
            // - Start acceptor
            NioScheduler scheduler = getScheduler();
            scheduler.execute(() -> {
                this.coRunner = new CoroutineRunner(super.acceptor);
                this.coRunner.setContext(this);
                scheduler.resume(this);
            });
            failed = false;
        } catch (final IOException cause) {
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
        }
    }

    @Override
    public void bind(Continuation co, SocketAddress endpoint, int backlog) throws CoIOException {
        checkCoContext(co);

        int unsetPort = getPort();
        boolean failed = true;
        try {
            NioScheduler scheduler = getScheduler();
            scheduler.register(this);
            ServerSocketChannel ssChan = this.channel;
            ssChan.bind(endpoint, backlog);
            InetSocketAddress sa = (InetSocketAddress)getLocalAddress();
            this.bindAddress = sa.getAddress();
            this.port = sa.getPort();
            this.name = NAME_PREFIX + "-" + this.port;
            ssChan.register(scheduler.selector, SelectionKey.OP_ACCEPT, this);
            if (PORT_UNDEFINED == unsetPort) {
                info("Bound then signal accept");
                scheduler.resume(this);
            }
            this.bound = true;
            failed = false;
        } catch(final IOException e) {
            this.cause = e;
            throw new CoIOException(e);
        } finally {
            if(failed){
                IoUtils.close(this);
            }
        }
    }

    public boolean isAutoShutdownScheduler() {
        return this.autoShutdownScheduler;
    }

    protected void tryShutdownScheduler() {
        if (isAutoShutdownScheduler()) {
            Scheduler scheduler = getScheduler();
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
    public SocketAddress getLocalAddress() throws CoIOException {
        try {
            return this.channel.getLocalAddress();
        } catch (IOException e) {
            throw new CoIOException(e);
        }
    }

    @Override
    public NioScheduler getScheduler(){
        return (NioScheduler)super.getScheduler();
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }

    @Override
    public void close() {
        debug("%s close..", this);
        IoUtils.close(this.channel);

        Object context = this.coRunner.getContext();
        if (context instanceof Throwable) {
            this.cause = (Throwable)context;
        }
        super.close();

        tryShutdownScheduler();
        debug("%s closed", this);
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

    @Override
    public String toString() {
        return this.name;
    }

    static NioScheduler newScheduler(int port) {
        NioScheduler scheduler = null;
        boolean failed = true;
        try {
            final String name;
            if (port > 0) {
                name = "nio-" + port;
            } else {
                name = "nio-server";
            }
            scheduler = new NioScheduler(name);
            scheduler.start();
            failed = false;

            return scheduler;
        } finally {
            if(failed && scheduler != null) scheduler.shutdown();
        }
    }
    
}
