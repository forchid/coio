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

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * The NIO implementation of CoSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoSocket extends CoSocket implements NioCoChannel<SocketChannel> {
    
    private SocketChannel channel;
    private CoInputStream in;
    private CoOutputStream out;
    private int id = -1;
    
    private NioCoTimer connectionTimer;
    private NioCoTimer readTimer;
    private CoroutineRunner coRunner;
    
    NioCoSocket(SocketHandler connector, NioScheduler scheduler,
                       SocketChannel channel) {
        super(connector, scheduler);
        
        this.channel = channel;
        Selector selector = scheduler.selector;
        this.in = new NioCoInputStream(this, this.channel, selector);
        this.out= new NioCoOutputStream(this, this.channel,selector);
        this.coRunner = new CoroutineRunner(connector);
    }

    public NioCoSocket(SocketHandler connector, NioScheduler scheduler) {
        this((InetAddress)null, PORT_UNDEFINED, connector, scheduler);
    }

    public NioCoSocket(int port, SocketHandler connector, NioScheduler scheduler) {
        this((InetAddress)null, port, connector, scheduler);
    }

    public NioCoSocket(String host, int port, SocketHandler connector, NioScheduler scheduler) {
        super(host, port, connector, scheduler);
        initialize(connector, scheduler);
    }
    
    public NioCoSocket(InetAddress address, int port, SocketHandler connector,
                       NioScheduler scheduler) {

        super(address, port, connector, scheduler);
        initialize(connector, scheduler);
    }

    private void initialize(SocketHandler connector, final NioScheduler scheduler) {
        SocketChannel chan = null;
        boolean failed = true;
        try {
            chan = SocketChannel.open();
            chan.configureBlocking(false);
            chan.socket().setTcpNoDelay(true);
            this.channel = chan;
            final Selector selector = scheduler.selector;
            this.in = new NioCoInputStream(this, this.channel, selector);
            this.out= new NioCoOutputStream(this, this.channel,selector);
            this.coRunner = new CoroutineRunner(connector);
            failed = false;
        } catch (final IOException cause) {
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(chan);
            }
        }

        // Coroutine start and wait for connection
        scheduler.execute(() -> {
            this.coRunner.setContext(this);
            scheduler.resume(this);
        });
    }
    
    @Override
    public NioScheduler getScheduler(){
        return (NioScheduler)super.getScheduler();
    }
    
    @Override
    public int id(){
        return this.id;
    }
    
    @Override
    public NioCoSocket id(int id){
        if(this.id >= 0){
            throw new IllegalStateException("id had been set");
        }
        this.id = id;
        return this;
    }
    
    @Override
    public SocketChannel channel(){
        return this.channel;
    }
    
    @Override
    public CoroutineRunner coRunner() {
        return this.coRunner;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
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
    }

    @Override
    public void connect(Continuation co, SocketAddress endpoint, int timeout)
            throws CoIOException {

        NioScheduler scheduler = getScheduler();
        scheduler.ensureInScheduler();

        boolean failed = true;
        try {
            SocketChannel chan = channel();
            scheduler.register(this);
            chan.register(scheduler.selector, SelectionKey.OP_CONNECT, this);
            chan.connect(endpoint);
            startConnectionTimer(timeout);
            failed = false;
        } catch(final IOException e) {
            throw new CoIOException("Connection failed", e);
        } finally {
            if (failed) {
                scheduler.close(this);
            }
        }
    }

    @Override
    public CoInputStream getInputStream() {
        return this.in;
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
                return String.format("%s[id=%d#%d, local=%s, remote=%s]", clazz, this.id, this.hashCode(),
                    this.channel.getLocalAddress(), this.channel.getRemoteAddress());
            }
        } catch (final IOException e) {
            // ignore
        }
        return String.format("%s[id=%d#%d]", clazz, this.id, this.hashCode());
    }
    
    protected NioConnectionTimer startConnectionTimer(final int timeout) {
        if(timeout > 0){
            final NioScheduler scheduler = getScheduler();
            this.connectionTimer = new NioConnectionTimer(this, timeout);
            scheduler.schedule(this.connectionTimer);
            return (NioConnectionTimer)this.connectionTimer;
        }
        return null;
    }

    protected void cancelConnectionTimer() {
        if(this.connectionTimer != null){
            this.connectionTimer.cancel();
            this.connectionTimer = null;
        }
    }
    
    protected void cancelReadTimer() {
        if(this.readTimer != null){
            this.readTimer.cancel();
            this.readTimer = null;
        }
    }

    protected NioReadTimer startReadTimer() {
        final int timeout = getSoTimeout();
        if(timeout > 0){
            final NioScheduler scheduler = getScheduler();
            this.readTimer = new NioReadTimer(this, timeout);
            scheduler.schedule(this.readTimer);
            return (NioReadTimer)this.readTimer;
        }
        return null;
    }
    
}
