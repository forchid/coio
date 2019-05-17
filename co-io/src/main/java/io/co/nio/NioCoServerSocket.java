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
import io.co.CoServerSocket;
import io.co.util.IoUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * A NIO implementation of CoServerSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoServerSocket extends CoServerSocket implements NioCoChannel<ServerSocketChannel> {
    
    final ServerSocketChannel channel;
    final CoroutineRunner coRunner;
    private int id = -1;
    
    public NioCoServerSocket(Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        this(DefaultNioCoAcceptor.class, connectorClass, coScheduler);
    }
    
    public NioCoServerSocket(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        super(acceptorClass, connectorClass, coScheduler);
        
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            this.channel = ssChan;
            this.coRunner = new CoroutineRunner(coAcceptor);
            failed = false;
        } catch (final IOException cause){
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
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
    public boolean isOpen() {
        return this.channel.isOpen();
    }
    
    @Override
    public void close(){
        IoUtils.close(this.channel);
        super.close();
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint)
            throws CoIOException {
        startAndServe(connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint, int backlog)
            throws CoIOException {
        startAndServe(DefaultNioCoAcceptor.class, connectorClass, endpoint, backlog);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, SocketAddress endpoint) throws CoIOException {
        startAndServe(acceptorClass, connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, SocketAddress endpoint, int backlog)
                    throws CoIOException {
        final NioCoScheduler scheduler = new NioCoScheduler();
        NioCoServerSocket ssSocket = null;
        boolean failed = true;
        try {
            ssSocket = new NioCoServerSocket(acceptorClass, connectorClass, scheduler);
            ssSocket.bind(endpoint, backlog);
            // Boot itself
            scheduler.startAndServe();
            failed = false;
        } catch(final IOException cause){
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(ssSocket);
            }
            scheduler.shutdown();
        }
    }
    
}
