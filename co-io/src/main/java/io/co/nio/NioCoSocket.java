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
public class NioCoSocket extends CoSocket {
    
    final SocketChannel channel;
    final CoInputStream in;
    final CoOutputStream out;
    final Selector selector;
    
    public NioCoSocket(Coroutine coConnector, Selector selector, SocketChannel channel, 
            CoScheduler coScheduler) {
        super(coConnector, coScheduler);
        this.channel = channel;
        this.in = new NioCoInputStream(this, this.channel, selector);
        this.out= new NioCoOutputStream(this, this.channel,selector);
        this.selector = null;
    }
    
    public NioCoSocket(Coroutine coConnector) {
        super(coConnector, new NioCoScheduler());
        
        SocketChannel chan = null;
        boolean failed = true;
        try {
            final NioCoScheduler scheduler = (NioCoScheduler)super.getCoScheduler();
            this.selector = scheduler.selector = Selector.open();
            chan = SocketChannel.open();
            chan.configureBlocking(false);
            this.channel = chan;
            this.in = new NioCoInputStream(this, this.channel, this.selector);
            this.out= new NioCoOutputStream(this, this.channel,this.selector);
            failed = false;
        } catch (final IOException cause) {
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(chan);
                IoUtils.close(this.selector);
            }
        }
    }
    
    public NioCoSocket(Coroutine coConnector, Selector selector){
        this(coConnector, selector, new NioCoScheduler());
    }
    
    public NioCoSocket(Coroutine coConnector, Selector selector, CoScheduler coScheduler) {
        super(coConnector, coScheduler);
        
        SocketChannel chan = null;
        boolean failed = true;
        try {
            chan = SocketChannel.open();
            chan.configureBlocking(false);
            this.channel = chan;
            this.in = new NioCoInputStream(this, this.channel, selector);
            this.out= new NioCoOutputStream(this, this.channel,selector);
            this.selector = null;
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
    public boolean isOpen() {
        return channel.isOpen();
    }
    
    @Override
    public void close() {
        IoUtils.close(getInputStream());
        IoUtils.close(getOutputStream());
        this.coScheduler.close(this);
        IoUtils.close(this.selector);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        this.coScheduler.connect(this, endpoint, timeout);
        // Boot itself
        if(this.selector != null){
            this.coScheduler.start();
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

}
