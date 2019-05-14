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
import io.co.CoScheduler;
import io.co.CoServerSocket;
import io.co.util.IoUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

/**
 * A NIO implementation of CoServerSocket.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoServerSocket extends CoServerSocket implements NioCoChannel<ServerSocketChannel> {
    
    final ServerSocketChannel channel;
    final int id;
    
    public NioCoServerSocket(Coroutine coAcceptor, Coroutine coConnector, NioCoScheduler coScheduler) {
        super(coAcceptor, coConnector, coScheduler);
        
        ServerSocketChannel ssChan = null;
        boolean failed = true;
        try {
            ssChan = ServerSocketChannel.open();
            ssChan.configureBlocking(false);
            this.channel = ssChan;
            this.id = coScheduler.nextSlot();
            coScheduler.register(this, coAcceptor);
            failed = false;
        } catch (final IOException cause){
            throw new CoIOException(cause);
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
        }
    }
    
    public int id(){
        return this.id;
    }
    
    @Override
    public ServerSocketChannel channel(){
        return this.channel;
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }
    
    @Override
    public void close(){
        super.close();
        IoUtils.close(this.channel);
    }
    
    public static void start(Coroutine coConnector, SocketAddress endpoint)throws CoIOException {
        start(coConnector, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void start(Coroutine coConnector, SocketAddress endpoint, int backlog)
            throws CoIOException{
        start(new Coroutine(){
            private static final long serialVersionUID = 1608438566384500434L;

            @Override
            public void run(final Continuation co) throws Exception {
                final NioCoServerSocket ssSocket = (NioCoServerSocket)co.getContext();
                if(ssSocket != null){
                    final CoScheduler scheduler = ssSocket.getCoScheduler();
                    for(;!scheduler.isShutdown();){
                        ssSocket.accept(co);
                    }
                }
            }
        }, coConnector, endpoint, backlog);
    }
    
    public static void start(Coroutine coAcceptor, Coroutine coConnector, SocketAddress endpoint)
            throws CoIOException {
        start(coAcceptor, coConnector, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void start(Coroutine coAcceptor, Coroutine coConnector, SocketAddress endpoint, int backlog)
        throws CoIOException {
        final NioCoScheduler scheduler = new NioCoScheduler();
        NioCoServerSocket ssSocket = null;
        boolean failed = true;
        try {
            ssSocket = new NioCoServerSocket(coAcceptor, coConnector, scheduler);
            ssSocket.bind(endpoint, backlog);
            // Boot itself
            scheduler.start();
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
