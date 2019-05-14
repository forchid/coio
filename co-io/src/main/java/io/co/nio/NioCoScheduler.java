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

import java.io.*;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.text.*;
import java.util.*;
import java.util.Map.*;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineException;
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
                    for(Iterator<Entry<CoChannel, CoRunnerChannel>> i = chans.entrySet().iterator();
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
                try {
                    if(selector.select() == 0){
                        continue;
                    }
                } catch(final ClosedSelectorException cause){
                    debug("Selector closed", cause);
                    this.stop();
                    break;
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
                    if(key.isValid() && key.isWritable()){
                        doWrite(key);
                    }
                }
            } catch (final CoroutineException e){
                debug("Uncaught exception in coroutine", e.getCause());
            } catch (final IOException cause){
                this.stop();
                throw new CoIOException("Scheduler fatal", cause);
            }
        }
    }
    
    protected static void debug(final String message, final Throwable cause){
        if(DEBUG){
            if(cause != null){
                debug(System.err, message);
                cause.printStackTrace(System.err);
            }else{
                debug(System.err, message);
            }
        }
    }
    
    protected static void debug(final String format, Object ...args){
        debug(System.out, format, args);
    }
    
    protected static void debug(final PrintStream out, String format, Object ...args){
        if(DEBUG){
            final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            String message = format;
            if(args.length > 0){
                message = String.format(format, args);
            }
            final String thread = Thread.currentThread().getName();
            out.println(String.format("%s[%s] %s", df.format(new Date()), thread, message));
        }
    }
    
    protected void stop(){
        IoUtils.close(this.selector);
        this.stopped = true;
    }
    
    protected void execute(CoroutineRunner coRunner, CoChannel coChannel) {
        if(coRunner.execute() == false){
            this.close(coChannel);
        }
    }
    
    protected void doWrite(final SelectionKey key) {
        final CoSocket socket = (CoSocket)key.attachment();
        final CoRunnerChannel corChan = this.chans.get(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        execute(coRunner, socket);
    }
    
    protected void doRead(final SelectionKey key) {
        final CoSocket socket = (CoSocket)key.attachment();
        final CoRunnerChannel corChan = this.chans.get(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        execute(coRunner, socket);
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
                execute(coRunner, socket);
                failed = false;
            } catch (final IOException cause){
                debug("Connection error", cause);
                coRunner.setContext(null);
                execute(coRunner, socket);
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
            coSocket = new NioCoSocket(coConnector, chan, this){
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
            // 2. Next accept
            ssRunChan.coRunner.setContext(coSocket);
            ssRunChan.coRunner.execute();
            // 3. Start the new
            sRunChan.coRunner.setContext(coSocket);
            execute(sRunChan.coRunner, coSocket);
            
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
            this.initialize();
            
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
            corChan.coRunner.setContext(nioSocket);
            corChan.coRunner.execute();
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
            chan.register(this.selector, SelectionKey.OP_CONNECT, coSocket);
            final Coroutine connector = coSocket.getCoConnector();
            this.chans.put(nioSocket, new CoRunnerChannel(connector, chan));
            chan.connect(endpoint);
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
    public boolean isShutdown() {
        return this.shutdown;
    }
    
    @Override
    public boolean isStopped(){
        return this.stopped;
    }
    
}
