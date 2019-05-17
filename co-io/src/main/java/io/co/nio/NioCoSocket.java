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

import io.co.CoIOException;
import io.co.CoInputStream;
import io.co.CoOutputStream;
import io.co.CoScheduler;
import io.co.CoSocket;
import io.co.TimeRunner;
import io.co.util.IoUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * The NIO implementation of CoSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoSocket extends CoSocket implements NioCoChannel<SocketChannel> {
    
    final SocketChannel channel;
    final CoInputStream in;
    final CoOutputStream out;
    private int id = -1;
    
    private TimeRunner connectionTimer;
    private TimeRunner readTimer;
    final CoroutineRunner coRunner;
    
    public NioCoSocket(Coroutine coConnector, SocketChannel channel, NioCoScheduler coScheduler) {
        super(coConnector, coScheduler);
        
        this.channel = channel;
        final Selector selector = coScheduler.selector;
        this.in = new NioCoInputStream(this, this.channel, selector);
        this.out= new NioCoOutputStream(this, this.channel,selector);
        this.coRunner = new CoroutineRunner(coConnector);
    }
    
    public NioCoSocket(Coroutine coConnector, NioCoScheduler coScheduler) {
        super(coConnector, coScheduler);
        
        SocketChannel chan = null;
        boolean failed = true;
        try {
            chan = SocketChannel.open();
            chan.configureBlocking(false);
            chan.socket().setKeepAlive(true);
            chan.socket().setTcpNoDelay(true);
            this.channel = chan;
            coScheduler.initialize();
            final Selector selector = coScheduler.selector;
            this.in = new NioCoInputStream(this, this.channel, selector);
            this.out= new NioCoOutputStream(this, this.channel,selector);
            this.coRunner = new CoroutineRunner(coConnector);
            failed = false;
        } catch (final IOException cause) {
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(chan);
            }
        }
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
    public void close(){
        IoUtils.close(getInputStream());
        IoUtils.close(getOutputStream());
        IoUtils.close(this.channel);
        super.close();
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        this.coScheduler.connect(this, endpoint, timeout);
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
        return this.channel + "";
    }
    
    public static void startAndServe(Coroutine coConnector, SocketAddress remote)
            throws CoIOException {
        startAndServe(coConnector, remote, 0);
    }
    
    public static void startAndServe(Coroutine coConnector, SocketAddress remote, int timeout)
            throws CoIOException {
        final int initConns = CoScheduler.INIT_CONNECTIONS;
        final int maxConns  = CoScheduler.MAX_CONNECTIONS;
        final NioCoScheduler scheduler = new NioCoScheduler(initConns, maxConns, 0);
        NioCoSocket socket = null;
        boolean failed = true;
        try {
            socket = new NioCoSocket(coConnector, scheduler);
            socket.connect(remote, timeout);
            // Boot itself
            scheduler.startAndServe();
            failed = false;
        } catch(final IOException cause){
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(socket);
            }
            scheduler.shutdown();
        }
    }
    
    protected NioConnectionTimer startConnectionTimer(final int timeout) {
        if(timeout > 0){
            final NioCoScheduler scheduler = (NioCoScheduler)getCoScheduler();
            final long runat = System.currentTimeMillis() + timeout;
            this.connectionTimer = new NioConnectionTimer(scheduler, this, runat);
            scheduler.schedule(this.connectionTimer);
            return (NioConnectionTimer)this.connectionTimer;
        }
        return null;
    }

    protected void cancelConnetionTimer() {
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
            final NioCoScheduler scheduler = (NioCoScheduler)getCoScheduler();
            final long runat = System.currentTimeMillis() + timeout;
            this.readTimer = new NioReadTimer(scheduler, this, runat);
            scheduler.schedule(this.readTimer);
            return (NioReadTimer)this.readTimer;
        }
        return null;
    }
    
}
