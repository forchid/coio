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

import java.io.IOError;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * The NIO implementation of CoSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoSocket extends CoSocket implements NioCoChannel<SocketChannel> {

    private final NioScheduler scheduler;
    private final boolean localScheduler;
    private final SocketChannel channel;
    private final CoInputStream in;
    private final CoOutputStream out;
    private int id = -1;
    
    private NioCoTimer connectionTimer;
    private NioCoTimer readTimer;
    private CoContext context;

    public NioCoSocket() throws IOError {
        this(new NioScheduler(), true);
    }

    public NioCoSocket(SocketChannel channel) {
        this(channel, new NioScheduler(), true);
    }

    public NioCoSocket(Scheduler scheduler) throws IOError {
        this(scheduler, false);
    }

    protected NioCoSocket(Scheduler scheduler, boolean localScheduler) throws IOError {
        NioScheduler nioScheduler = (NioScheduler)scheduler;
        final Selector selector = nioScheduler.selector;
        SocketChannel ch = null;
        boolean failed = true;

        try {
            ch = SocketChannel.open();
            ch.configureBlocking(false);
            ch.socket().setTcpNoDelay(true);
            this.in = new NioCoInputStream(this, ch, selector);
            this.out = new NioCoOutputStream(this, ch, selector);
            this.channel = ch;
            this.scheduler = nioScheduler;
            this.localScheduler = localScheduler;
            failed = false;
        } catch (IOException e) {
            throw new IOError(e);
        } finally {
            if(failed){
                IoUtils.close(ch);
                if (localScheduler) scheduler.shutdown();
            }
        }
    }

    public NioCoSocket(SocketChannel channel, Scheduler scheduler) {
        this(channel, scheduler, false);
    }

    protected NioCoSocket(SocketChannel channel, Scheduler scheduler, boolean localScheduler) {
        NioScheduler nioScheduler = (NioScheduler)scheduler;
        final Selector selector = nioScheduler.selector;
        boolean failed = true;

        try {
            this.channel = channel;
            this.scheduler = nioScheduler;
            this.localScheduler = localScheduler;
            this.in = new NioCoInputStream(this, this.channel, selector);
            this.out = new NioCoOutputStream(this, this.channel, selector);
            failed = false;
        } finally {
            if (failed && localScheduler) scheduler.shutdown();
        }
    }
    
    @Override
    public NioScheduler getScheduler(){
        return this.scheduler;
    }
    
    @Override
    public int id(){
        return this.id;
    }
    
    @Override
    public void id(int id){
        if(this.id >= 0){
            throw new IllegalStateException("id had been set");
        }
        this.id = id;
    }
    
    @Override
    public SocketChannel channel(){
        return this.channel;
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }
    
    @Override
    public boolean isConnected() {
        return this.channel.isConnected();
    }
    
    @Override
    public void close() {
        IoUtils.close(getInputStream());
        IoUtils.close(getOutputStream());
        IoUtils.close(this.channel);
        super.close();
        if (this.localScheduler) this.scheduler.shutdown();
    }

    @Override
    public void connect(Continuation co, SocketAddress endpoint, int timeout)
            throws IOException {
        NioScheduler scheduler = getScheduler();
        boolean failed = true;
        try {
            SocketChannel ch = channel();
            scheduler.register(this);
            ch.register(scheduler.selector, SelectionKey.OP_CONNECT, this);
            ch.connect(endpoint);
            startConnectionTimer(co, timeout);

            InetSocketAddress sa = (InetSocketAddress)endpoint;
            super.address = sa.getAddress();
            super.port = sa.getPort();
            suspend(co);

            failed = false;
        } finally {
            if (failed) {
                scheduler.close(this);
            }
        }
    }

    @Override
    public int available(Continuation co) throws IOException {
        return this.in.available(co);
    }

    @Override
    public int read(Continuation co) throws IOException {
        return this.in.read(co);
    }

    @Override
    public int read(Continuation co, byte[] b) throws IOException {
        return this.in.read(co, b);
    }

    @Override
    public int read(Continuation co, byte[] b, int off, int len) throws IOException {
        return this.in.read(co, b, off, len);
    }

    @Override
    public int readFully(Continuation co, byte[] b) throws IOException {
        return this.in.readFully(co, b);
    }

    @Override
    public int readFully(Continuation co, byte[] b, int off, int len) throws IOException {
        return this.in.readFully(co, b, off, len);
    }

    @Override
    public long skip(Continuation co, long n) throws IOException {
        return this.in.skip(co, n);
    }

    protected Object suspend(Continuation co) throws IOException {
        final Object result;

        beforeSuspend(co);
        try {
            co.suspend();
        } finally {
            result = afterSuspend(co);
        }

        return result;
    }

    protected void beforeSuspend(Continuation co) {
        Scheduler scheduler = getScheduler();
        scheduler.ensureInScheduler();
        this.context = (CoContext) co.getContext();
    }

    protected Object afterSuspend(Continuation co) throws IOException {
        try {
            CoContext ctx = (CoContext) co.getContext();
            Object attachment = ctx.detach();

            if (attachment instanceof IOException) {
                throw (IOException) attachment;
            }

            return attachment;
        } finally {
            this.context = null;
        }
    }

    @Override
    public CoInputStream getInputStream() {
        return this.in;
    }

    @Override
    public void write(Continuation co, int b) throws IOException {
        this.out.write(co, b);
    }

    @Override
    public void write(Continuation co, byte[] b) throws IOException {
        this.out.write(co, b);
    }

    @Override
    public void write(Continuation co, byte[] b, int off, int len) throws IOException {
        this.out.write(co, b, off, len);
    }

    @Override
    public void flush(Continuation co) throws IOException {
        this.out.flush(co);
    }

    @Override
    public CoOutputStream getOutputStream() {
        return this.out;
    }
    
    @Override
    public String toString(){
        final String clazz =  this.getClass().getSimpleName();
        try {
            if(this.isOpen()) {
                return String.format("%s[id=%d#%d, local=%s, remote=%s]",
                        clazz, this.id, this.hashCode(), this.channel.getLocalAddress(),
                        this.channel.getRemoteAddress());
            }
        } catch (final IOException e) {
            // ignore
        }
        return String.format("%s[id=%d#%d]", clazz, this.id, this.hashCode());
    }

    protected CoContext getContext() {
        return this.context;
    }
    
    protected void startConnectionTimer(Continuation co, int timeout) {
        if(timeout > 0){
            final NioScheduler scheduler = getScheduler();
            CoContext context = (CoContext)co.getContext();
            this.connectionTimer = new NioConnectionTimer(context, scheduler, timeout);
            scheduler.schedule(this.connectionTimer);
        }
    }

    protected void cancelConnectionTimer() {
        if(this.connectionTimer != null){
            this.connectionTimer.cancel();
            this.connectionTimer = null;
        }
    }
    
    protected void cancelReadTimer() {
        if (this.readTimer != null) {
            this.readTimer.cancel();
            this.readTimer = null;
        }
    }

    protected void startReadTimer(Continuation co) {
        final int timeout = getSoTimeout();

        if (timeout > 0) {
            final NioScheduler scheduler = getScheduler();
            CoContext context = (CoContext)co.getContext();
            this.readTimer = new NioReadTimer(context, scheduler, timeout);
            scheduler.schedule(this.readTimer);
        }
    }
    
}
