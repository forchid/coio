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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

import io.co.CoChannel;
import io.co.CoIOException;
import io.co.CoScheduler;
import io.co.CoServerSocket;
import io.co.CoSocket;
import io.co.util.IoUtils;

/**
 * The coroutine scheduler based on NIO.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoScheduler implements CoScheduler {
    
    private final Map<CoChannel, CoRunnerChannel> chans;
    protected Selector selector;
    protected volatile boolean shutdown;
    protected volatile boolean stopped;
    
    public NioCoScheduler(){
        this.chans = new HashMap<>();
    }
    
    protected void initialize() throws CoIOException {
        try {
            if(this.selector == null){
                this.selector = Selector.open();
            }
        } catch (final IOException cause){
            throw new CoIOException(cause);
        }
    }
    
    protected void schedule(){
        for(;;){
            final Selector selector = this.selector;
            try {
                // Shutdown handler
                if(this.shutdown){
                    for(final Iterator<Map.Entry<CoChannel, CoRunnerChannel>> i = chans.entrySet().iterator();
                        i.hasNext();){
                        final CoRunnerChannel runChan = i.next().getValue();
                        if(runChan.channel instanceof ServerSocketChannel){
                            IoUtils.close(runChan.channel);
                            i.remove();
                        }
                    }
                    if(this.chans.size() == 0){
                        this.stop();
                        // Exit normally
                        break;
                    }
                }
                
                // Do selection
                final int n = selector.select();
                if(n == 0){
                    continue;
                }
                final Set<SelectionKey> selKeys = selector.selectedKeys();
                for(Iterator<SelectionKey> i = selKeys.iterator(); i.hasNext(); i.remove()){
                    final SelectionKey key = i.next();
                    if(!key.isValid()){
                        continue;
                    }
                    if(key.isAcceptable()){
                        doAccept(key);
                        continue;
                    }
                    if(key.isConnectable()){
                        doConnect(key);
                        continue;
                    }
                    if(key.isReadable()){
                        doRead(key);
                    }
                    if(key.isWritable()){
                        doWrite(key);
                    }
                }
            } catch (final IOException cause){
                this.stop();
                throw new CoIOException("Select error", cause);
            }
        }
    }
    
    protected void stop(){
        IoUtils.close(this.selector);
        this.stopped = true;
    }
    
    protected void doWrite(final SelectionKey key) {
        final CoSocket socket = (CoSocket)key.attachment();
        final CoRunnerChannel corChan = this.chans.get(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        coRunner.execute();
    }
    
    protected void doRead(final SelectionKey key) {
        final CoSocket socket = (CoSocket)key.attachment();
        final CoRunnerChannel corChan = this.chans.get(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        coRunner.execute();
    }
    
    protected void doConnect(final SelectionKey key) {
        final SocketChannel chan = (SocketChannel)key.channel();
        boolean failed = true;
        try {
            final CoSocket socket = (CoSocket)key.attachment();
            final CoRunnerChannel corChan = this.chans.get(socket);
            final CoroutineRunner coRunner = corChan.coRunner;
            try {
                chan.finishConnect();
                coRunner.setContext(socket);
                coRunner.execute();
                failed = false;
            } catch (final IOException cause){
                coRunner.setContext(null);
                coRunner.execute();
            }
        } finally {
            if(failed){
                IoUtils.close(chan);
            }
        }
    }
    
    protected void doAccept(final SelectionKey key) throws IOException {
        final ServerSocketChannel ssChan = (ServerSocketChannel)key.channel();
        final CoServerSocket cosSocket = (CoServerSocket)key.attachment();
        final CoRunnerChannel ssRunChan = this.chans.get(cosSocket);
        NioCoSocket coSocket = null;
        SocketChannel chan = null;
        boolean failed = true;
        try {
            chan = ssChan.accept();
            chan.configureBlocking(false);
            // 1. Create coSocket
            final Coroutine coConnector = cosSocket.getCoConnector();
            final CoScheduler coScheduler = cosSocket.getCoScheduler();
            coSocket = new NioCoSocket(coConnector, this.selector, chan, coScheduler){
                @Override
                public void connect(SocketAddress endpoint) throws IOException {
                    // NOOP
                }

                @Override
                public void connect(SocketAddress endpoint, int timeout)throws IOException {
                    // NOOP
                }  
            };
            final CoRunnerChannel sRunChan = new CoRunnerChannel(coConnector, chan);
            this.chans.put(coSocket, sRunChan);
            // 2. Resume accept
            ssRunChan.coRunner.setContext(coSocket);
            ssRunChan.coRunner.execute();
            // 3. Start the new
            sRunChan.coRunner.setContext(coSocket);
            sRunChan.coRunner.execute();
            
            failed = false;
        } finally {
            if(failed){
                this.close(coSocket);
                IoUtils.close(chan);
            }
        }
    }
    
    @Override
    public void start() {
        initialize();
        schedule();
    }
    
    public CoSocket accept(Continuation co, CoServerSocket coServerSocket)
        throws CoIOException {
        try {
            final CoRunnerChannel corChan= this.chans.get(coServerSocket);
            final SelectableChannel chan = (SelectableChannel)corChan.channel;
            chan.register(this.selector, SelectionKey.OP_ACCEPT, coServerSocket);
        } catch (final IOException cause){
            throw new CoIOException(cause);
        }
        co.suspend();
        return (CoSocket)co.getContext();
    }
    
    public void bind(CoServerSocket coServerSocket, SocketAddress endpoint, int backlog)
        throws IOException {
        
        final NioCoServerSocket nioSocket = (NioCoServerSocket)coServerSocket;
        final ServerSocketChannel ssChan = nioSocket.channel;
        boolean failed = true;
        try {
            ssChan.bind(endpoint, backlog);
            final Coroutine coAcceptor = coServerSocket.getCoAcceptor();
            final CoRunnerChannel corChan = new CoRunnerChannel(coAcceptor, ssChan);
            chans.put(coServerSocket, corChan);
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(ssChan);
            }
        }
    }
    
    @Override
    public void connect(CoSocket coSocket, SocketAddress endpoint, int timeout) throws IOException {
        final NioCoSocket nioSocket = (NioCoSocket)coSocket;
        final SocketChannel chan = nioSocket.channel;
        boolean failed = true;
        try {
            final Coroutine connector = coSocket.getCoConnector();
            this.chans.put(nioSocket, new CoRunnerChannel(connector, chan));
            nioSocket.channel.connect(endpoint);
            failed = false;
        } finally {
            if(failed){
                this.close(coSocket);
                IoUtils.close(chan);
            }
        }
    }
    
    public void close(CoChannel coChannel) {
        if(coChannel == null){
            return;
        }
        final CoRunnerChannel corChan = this.chans.remove(coChannel);
        if(corChan == null){
            return;
        }
        IoUtils.close(corChan.channel);
    }
    
    @Override
    public void shutdown() {
        this.shutdown = true;
    }
    
    @Override
    public boolean isStopped(){
        return this.stopped;
    }

}
