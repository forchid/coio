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
    
    private CoRunnerChannel[] chans;
    private int maxSlot;
    
    protected volatile Selector selector;
    protected volatile boolean shutdown;
    protected volatile boolean stopped;
    
    public NioCoScheduler(){
        this(1024);
    }
    
    public NioCoScheduler(int initConnections){
        this.chans = new CoRunnerChannel[initConnections];
    }
    
    @Override
    public void start() {
        initialize();
        schedule();
    }
    
    @Override
    public CoSocket accept(Continuation co, CoServerSocket coServerSocket)
        throws CoIOException {
        try {
            this.initialize();
            
            final NioCoServerSocket sSocket = (NioCoServerSocket)coServerSocket;
            final CoRunnerChannel corChan= this.chans[sSocket.id()];
            final SelectableChannel chan = (SelectableChannel)corChan.coChannel.channel();
            chan.register(this.selector, SelectionKey.OP_ACCEPT, coServerSocket);
        } catch (final IOException cause){
            throw new CoIOException(cause);
        }
        co.suspend();
        return (CoSocket)co.getContext();
    }
    
    @Override
    public void bind(CoServerSocket coServerSocket, SocketAddress endpoint, int backlog)
        throws IOException {
        
        final NioCoServerSocket serverSocket = (NioCoServerSocket)coServerSocket;
        final ServerSocketChannel ssChan = serverSocket.channel();
        boolean failed = true;
        try {
            ssChan.bind(endpoint, backlog);
            final CoRunnerChannel corChan = runnerChannel(serverSocket);
            corChan.coRunner.setContext(serverSocket);
            corChan.coRunner.execute();
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(serverSocket);
            }
        }
    }
    
    @Override
    public void connect(CoSocket coSocket, SocketAddress remote) throws IOException {
        connect(coSocket, remote, 0);
    }
    
    @Override
    public void connect(CoSocket coSocket, SocketAddress remote, int timeout)
            throws IOException {
        final NioCoSocket socket = (NioCoSocket)coSocket;
        boolean failed = true;
        try {
            final SocketChannel chan = socket.channel();
            chan.register(this.selector, SelectionKey.OP_CONNECT, coSocket);
            chan.connect(remote);
            failed = false;
        } finally {
            if(failed){
                this.close(coSocket);
            }
        }
    }
    
    public void close(CoChannel coChannel) {
        if(coChannel == null){
            return;
        }
        
        final NioCoChannel<?> chan = (NioCoChannel<?>)coChannel;
        final CoRunnerChannel corChan = runnerChannel(chan);
        if(corChan != null && corChan.coChannel == chan) {
            final int slot   = chan.id();
            this.chans[slot] = null;
            if(slot == this.maxSlot - 1){
                this.maxSlot--;
            }
        }
    }
    
    @Override
    public void shutdown() {
        final Selector sel = this.selector;
        if(sel != null){
            sel.wakeup();
        }
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
    
    CoRunnerChannel register(final NioCoChannel<?> coChannel, final Coroutine co){
        final int slot = coChannel.id();
        final CoRunnerChannel oldChan = this.chans[slot];
        if(oldChan != null && oldChan.coChannel.isOpen()){
            throw new IllegalStateException(String.format("Channel slot %s used", slot));
        }
        final CoRunnerChannel newChan = new CoRunnerChannel(co, coChannel);
        this.chans[slot] = newChan;
        return newChan;
    }
    
    <S extends Channel> CoRunnerChannel runnerChannel(final NioCoChannel<S> coChannel){
        final CoRunnerChannel corChan = this.chans[coChannel.id()];
        if(corChan == null || corChan.coChannel != coChannel){
            return null;
        }
        return corChan;
    }
    
    int nextSlot() {
        final CoRunnerChannel[] chans = this.chans;
        final int n = chans.length;
        
        // quick allocate
        if(this.maxSlot < n){
            return this.maxSlot++;
        }
        
        // traverse
        for(int i = 0; i < n; ++i){
            final CoRunnerChannel corChan = chans[i];
            if(corChan == null || !corChan.coChannel.isOpen()){
                return i;
            }
        }
        
        // expand
        final CoRunnerChannel[] newChans = new CoRunnerChannel[n << 1];
        System.arraycopy(chans, 0, newChans, 0, n);
        this.chans = newChans;
        return this.maxSlot++;
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
                    final CoRunnerChannel[] chans = this.chans;
                    int actives = 0;
                    for(int i = 0, n = chans.length; i < n; ++i){
                        final CoRunnerChannel runChan = chans[i];
                        if(runChan == null){
                            continue;
                        }
                        final Channel chan = runChan.coChannel.channel();
                        if(chan instanceof ServerSocketChannel){
                            this.close(runChan.coChannel);
                            continue;
                        }
                        ++actives;
                    }
                    if(actives == 0){
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
    
    protected void execute(CoroutineRunner coRunner, NioCoChannel<?> coChannel) {
        if(coRunner.execute() == false){
            this.close(coChannel);
        }
    }
    
    protected void doWrite(final SelectionKey key) {
        final NioCoChannel<?> socket = (NioCoChannel<?>)key.attachment();
        final CoRunnerChannel corChan = runnerChannel(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        execute(coRunner, socket);
    }
    
    protected void doRead(final SelectionKey key) {
        final NioCoChannel<?> socket = (NioCoChannel<?>)key.attachment();
        final CoRunnerChannel corChan = runnerChannel(socket);
        final CoroutineRunner coRunner = corChan.coRunner;
        execute(coRunner, socket);
    }
    
    protected void doConnect(final SelectionKey key) {
        final SocketChannel chan = (SocketChannel)key.channel();
        final NioCoChannel<?> socket = (NioCoChannel<?>)key.attachment();
        boolean failed = true;
        try {
            final CoRunnerChannel corChan = runnerChannel(socket);
            final CoroutineRunner coRunner = corChan.coRunner;
            try {
                chan.finishConnect();
                failed = false;
            } catch (final IOException cause){
                coRunner.setContext(cause);
                execute(coRunner, socket);
                return;
            }
            coRunner.setContext(socket);
            execute(coRunner, socket);
        } finally {
            if(failed){
                IoUtils.close(socket);
            }
        }
    }
    
    protected void doAccept(final SelectionKey key) throws IOException {
        NioCoSocket coSocket = null;
        SocketChannel chan = null;
        boolean failed = true;
        try {
            final ServerSocketChannel ssChan = (ServerSocketChannel)key.channel();
            final NioCoChannel<?> sSocket = (NioCoChannel<?>)key.attachment();
            final CoRunnerChannel ssRunChan = runnerChannel(sSocket);
            final CoServerSocket cosSocket  = (CoServerSocket)ssRunChan.coChannel;
            try {
                chan = ssChan.accept();
                chan.configureBlocking(false);
                
                // 1. Create coSocket
                final Coroutine coConnector = cosSocket.getCoConnector();
                coSocket = new PassiveNioCoSocket(coConnector, chan, this);
            } finally {
                // 2. Next accept
                ssRunChan.coRunner.setContext(coSocket);
                ssRunChan.coRunner.execute();
            }
            
            // 3. Start coSocket
            final CoRunnerChannel sRunChan = runnerChannel(coSocket);
            sRunChan.coRunner.setContext(coSocket);
            execute(sRunChan.coRunner, coSocket);
            
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(chan);
                this.close(coSocket);
            }
        }
    }
    
}
