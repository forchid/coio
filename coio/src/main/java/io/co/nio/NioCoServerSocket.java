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
package io.co.nio;

import com.offbynull.coroutines.user.Continuation;
import io.co.*;
import io.co.util.IoUtils;
import static io.co.util.LogUtils.*;

import java.io.IOError;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.Queue;

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
    protected final ServerSocketChannel channel;
    protected CoContext context;
    private final Queue<AcceptResult> acceptQueue = new LinkedList<>();

    private final NioScheduler scheduler;
    protected final boolean localScheduler;
    private int id = -1;
    
    public NioCoServerSocket() throws IOError {
        this(new NioScheduler(), true);
    }

    public NioCoServerSocket(NioScheduler scheduler) throws IOError {
        this(scheduler, false);
    }
    
    protected NioCoServerSocket(NioScheduler scheduler, boolean localScheduler)
        throws IOError {
        // Init server socket
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            this.scheduler = scheduler;
            this.localScheduler = localScheduler;
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            this.channel = ssChan;
            failed = false;
        } catch (IOException e) {
            throw new IOError(e);
        } finally {
            if(failed){
                IoUtils.close(ssChan);
                tryShutdownScheduler();
            }
        }
    }

    @Override
    public NioScheduler getScheduler(){
        return this.scheduler;
    }

    @Override
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
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
            if (this.localScheduler) scheduler.setName(this.name);
            ssChan.register(scheduler.selector, SelectionKey.OP_ACCEPT, this);
            this.bound = true;
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(this);
            }
        }
    }
    
    @Override
    public int id(){
        return this.id;
    }
    
    @Override
    public NioCoServerSocket id(int id) throws IllegalStateException {
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
        return this.context.coRunner();
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
    public boolean isOpen() {
        return this.channel.isOpen();
    }

    @Override
    public NioCoSocket accept(Continuation co) throws IOException, IllegalStateException {
        if (!isBound()) {
            throw new IllegalStateException("unbound");
        }

        AcceptResult result = this.acceptQueue.poll();
        if (result == null) {
            result = suspend(co);
        }
        NioCoSocket socket = result.socket;
        if (socket == null) {
            throw result.error;
        }

        return socket;
    }

    @Override
    public void close() {
        debug("%s close..", this);
        IoUtils.close(this.channel);
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


    protected boolean isLocalScheduler() {
        return this.localScheduler;
    }

    protected void tryShutdownScheduler() {
        if (isLocalScheduler()) {
            Scheduler scheduler = getScheduler();
            scheduler.shutdown();
        }
    }

    AcceptResult suspend(Continuation co) {
        final AcceptResult result;

        beforeSuspend(co);
        try {
            co.suspend();
        } finally {
            result = afterSuspend(co);
        }

        return result;
    }

    void beforeSuspend(Continuation co) {
        Scheduler scheduler = getScheduler();
        scheduler.ensureInScheduler();
        this.context = (CoContext)co.getContext();
    }

    AcceptResult afterSuspend(Continuation co) {
        try {
            CoContext ctx = (CoContext)co.getContext();
            return (AcceptResult)ctx.detach();
        } finally {
            this.context = null;
        }
    }

    CoContext getContext() {
        return this.context;
    }

    public void onAccept(AcceptResult result) {
        this.acceptQueue.offer(result);
    }

    static class AcceptResult {
        final NioCoSocket socket;
        final IOException error;

        public AcceptResult(NioCoSocket socket) throws NullPointerException {
            this(socket, null);
        }

        public AcceptResult(IOException error) throws NullPointerException {
            this(null, error);
        }

        public AcceptResult(NioCoSocket socket, IOException error)
            throws NullPointerException {
            if (socket == null && error == null) {
                throw new NullPointerException();
            }
            this.socket = socket;
            this.error  = error;
        }
    }
    
}
