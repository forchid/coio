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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A NIO implementation of CoServerSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoServerSocket extends CoServerSocket
        implements NioCoChannel<ServerSocketChannel> {

    protected static String NAME_PREFIX = "NioCoServer";

    protected String name = NAME_PREFIX;
    private int backlog;
    private ServerSocketChannel channel;
    private volatile boolean bound;
    private Queue<AcceptResult> acceptQueue;

    protected CoContext context;

    protected final NioScheduler scheduler;
    protected final boolean localScheduler;
    private int id = -1;
    
    public NioCoServerSocket() {
        this.scheduler = new NioScheduler();
        this.localScheduler = true;
    }

    public NioCoServerSocket(NioScheduler scheduler) {
        this.scheduler = scheduler;
        this.localScheduler = false;
    }

    @Override
    public int getBacklog() {
        return this.backlog;
    }

    @Override
    public boolean isBound() {
        return this.bound;
    }

    @Override
    public NioScheduler getScheduler(){
        return this.scheduler;
    }

    @Override
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        ServerSocketChannel ch = null;
        boolean failed = true;
        try {
            if (isBound()) {
                throw new IOException("The socket is already bound");
            }
            ch = openChannel();
            ch.bind(endpoint, backlog);
            this.channel = ch;
            this.backlog = backlog;
            this.name = NAME_PREFIX + "-" + getLocalPort();
            this.acceptQueue = new LinkedList<>();

            NioScheduler scheduler = getScheduler();
            scheduler.register(this);
            if (this.localScheduler) scheduler.setName(this.name);
            ch.register(scheduler.selector, SelectionKey.OP_ACCEPT, this);

            this.bound = true;
            failed = false;
        } finally {
            if (failed) {
                IoUtils.close(ch);
            }
        }
    }

    static ServerSocketChannel openChannel() throws IOException {
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            failed = false;
            return ssChan;
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
        }
    }
    
    @Override
    public int id() {
        return this.id;
    }
    
    @Override
    public void id(int id) throws IllegalStateException {
        if(this.id >= 0){
            throw new IllegalStateException("id had been set");
        }
        this.id = id;
    }

    @Override
    public ServerSocketChannel channel() {
        return this.channel;
    }

    @Override
    public boolean isOpen() {
        if (!isBound()) {
            return false;
        }

        return this.channel.isOpen();
    }

    @Override
    public NioCoSocket accept(Continuation co)
            throws IOException, IllegalStateException {
        if (!isBound()) {
            throw new IOException("The socket is unbound");
        }
        if (!isOpen()) {
            throw new ClosedChannelException();
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
        try {
            IoUtils.close(this.channel);
            Queue<AcceptResult> aq = this.acceptQueue;
            if (aq != null) {
                while (true) {
                    AcceptResult ar = aq.poll();
                    if (ar == null) break;
                    IoUtils.close(ar.socket);
                }
            }
            super.close();
        } finally {
            tryShutdownScheduler();
        }
        debug("%s closed", this);
    }
    
    @Override
    public InetAddress getInetAddress() {
        if (!isBound()) {
            return null;
        }

        ServerSocket socket = this.channel.socket();
        return socket.getInetAddress();
    }
    
    @Override
    public int getLocalPort() {
        if (!isBound()) {
            return -1;
        }

        ServerSocket socket = this.channel.socket();
        return socket.getLocalPort();
    }
    
    @Override
    public SocketAddress getLocalSocketAddress() {
        if (!isBound()) {
            return null;
        }

        ServerSocket socket = this.channel.socket();
        return socket.getLocalSocketAddress();
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

    AcceptResult suspend(Continuation co) throws IllegalStateException {
        final Object result;

        this.context = (CoContext)co.getContext();
        try {
            CoContext.suspend(co);
            result = this.context.detach();
        } finally {
            this.context = null;
        }

        return (AcceptResult)result;
    }

    CoContext getContext() {
        return this.context;
    }

    protected void onAccept(AcceptResult result) throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("The socket is closed");
        }
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
