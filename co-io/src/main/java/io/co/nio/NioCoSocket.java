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
import io.co.CoSocket;
import io.co.util.IoUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.offbynull.coroutines.user.Coroutine;

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
    final int id;
    
    public NioCoSocket(Coroutine coConnector, SocketChannel channel, NioCoScheduler coScheduler) {
        super(coConnector, coScheduler);
        
        this.channel = channel;
        coScheduler.initialize();
        final Selector selector = coScheduler.selector;
        this.in = new NioCoInputStream(this, this.channel, selector);
        this.out= new NioCoOutputStream(this, this.channel,selector);
        this.id = coScheduler.nextSlot();
        coScheduler.register(this, coConnector);
    }
    
    public NioCoSocket(Coroutine coConnector, NioCoScheduler coScheduler) {
        super(coConnector, coScheduler);
        
        SocketChannel chan = null;
        boolean failed = true;
        try {
            chan = SocketChannel.open();
            chan.configureBlocking(false);
            this.channel = chan;
            coScheduler.initialize();
            final Selector selector = coScheduler.selector;
            this.in = new NioCoInputStream(this, this.channel, selector);
            this.out= new NioCoOutputStream(this, this.channel,selector);
            this.id = coScheduler.nextSlot();
            coScheduler.register(this, coConnector);
            failed = false;
        } catch (final IOException cause) {
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(chan);
            }
        }
    }
    
    public int id(){
        return this.id;
    }
    
    @Override
    public SocketChannel channel(){
        return this.channel;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }
    
    @Override
    public void close(){
        super.close();
        IoUtils.close(getInputStream());
        IoUtils.close(getOutputStream());
        IoUtils.close(this.channel);
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
    
    public static void start(Coroutine coConnector, SocketAddress remote) throws CoIOException {
        start(coConnector, remote, 0);
    }
    
    public static void start(Coroutine coConnector, SocketAddress remote, int timeout)
            throws CoIOException {
        final NioCoScheduler scheduler = new NioCoScheduler();
        NioCoSocket socket = null;
        boolean failed = true;
        try {
            socket = new NioCoSocket(coConnector, scheduler);
            socket.connect(remote, timeout);
            // Boot itself
            scheduler.start();
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

}
