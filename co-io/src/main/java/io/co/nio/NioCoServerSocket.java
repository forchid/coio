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
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    
    protected ServerSocketChannel channel;
    protected CoroutineRunner coRunner;
    
    private int id = -1;
    
    public NioCoServerSocket() {
        this(DefaultAcceptor.class, DefaultConnector.class);
    }
    
    public NioCoServerSocket(int port) {
        this(port, DefaultAcceptor.class, DefaultConnector.class);
    }
    
    public NioCoServerSocket(int port,  Class<? extends Coroutine> connectorClass) {
        this(port, BACKLOG_DEFAULT, null, DefaultAcceptor.class, connectorClass, null);
    }
    
    public NioCoServerSocket(int port, int backlog) {
        this(port, backlog, DefaultAcceptor.class, DefaultConnector.class, null);
    }
    
    public NioCoServerSocket(int port, int backlog, Class<? extends Coroutine> connectorClass) {
        this(port, backlog, null, DefaultAcceptor.class, connectorClass, null);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddr) {
        this(port, backlog, bindAddr, DefaultAcceptor.class, DefaultConnector.class, null);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddr,
            Class<? extends Coroutine> connectorClass) {
        this(port, backlog, bindAddr, DefaultAcceptor.class, connectorClass, null);
    }
    
    public NioCoServerSocket(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass) {
        this(acceptorClass, connectorClass, null);
    }
    
    public NioCoServerSocket(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        super(acceptorClass, connectorClass, coScheduler);
        initialize(-1, BACKLOG_DEFAULT, null);
    }
    
    public NioCoServerSocket(int port,
            Class<? extends Coroutine> acceptorClass,  Class<? extends Coroutine> connectorClass) {
        this(port, acceptorClass, connectorClass, null);
    }
    
    public NioCoServerSocket(int port,
            Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        this(port, BACKLOG_DEFAULT, null, acceptorClass, connectorClass, coScheduler);
    }
    
    public NioCoServerSocket(int port, int backlog,
            Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        this(port, backlog, null, acceptorClass, connectorClass, coScheduler);
    }
    
    public NioCoServerSocket(int port, int backlog, InetAddress bindAddr, 
            Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, NioCoScheduler coScheduler) {
        super(port, backlog, bindAddr, acceptorClass, connectorClass, coScheduler);
        initialize(port, backlog, bindAddr);
    }
    
    private void initialize(int port, int backlog, InetAddress bindAddr) {
        // 1. Initialize server socket
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
        
        // 2. initialize scheduler
        boolean newSched = false;
        if(this.coScheduler == null) {
            newSched = true;
            NioCoScheduler scheduler = null;
            try {
                failed = true;
                final String sname;
                if(port > 0) {
                    sname = "nio-cosched-port-" + port;
                }else {
                    sname = "nio-cosched-server";
                }
                this.coScheduler = scheduler = new NioCoScheduler(sname);
                scheduler.start();
                failed = false;
            } finally {
                if(failed) {
                    IoUtils.close(ssChan);
                    if(scheduler != null) {
                        scheduler.shutdown();
                    }
                }
            }
        }
        
        // 3. Try to bind
        if(port != -1) {
            try {
                failed = true;
                bind(new InetSocketAddress(bindAddr, port), backlog);
                failed = false;
            } finally {
                if(failed) {
                    IoUtils.close(ssChan);
                    if(newSched) {
                        this.coScheduler.shutdown();
                    }
                }
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
    public NioCoScheduler getScheduler(){
        return (NioCoScheduler)super.getScheduler();
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
    
    @Override
    public InetAddress getInetAddress() {
        return this.channel.socket().getInetAddress();
    }
    
    @Override
    public int getLocalPort() {
        return this.channel.socket().getLocalPort();
    }
    
    @Override
    public SocketAddress getLocalSocketAddress() throws CoIOException {
        try {
            return this.channel.getLocalAddress();
        } catch (final IOException e) {
            throw new CoIOException(e);
        }
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint)
            throws CoIOException {
        startAndServe(connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends Coroutine> connectorClass, SocketAddress endpoint, int backlog)
            throws CoIOException {
        startAndServe(DefaultAcceptor.class, connectorClass, endpoint, backlog);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, SocketAddress endpoint) throws CoIOException {
        startAndServe(acceptorClass, connectorClass, endpoint, BACKLOG_DEFAULT);
    }
    
    public static void startAndServe(Class<? extends Coroutine> acceptorClass, 
            Class<? extends Coroutine> connectorClass, SocketAddress endpoint, int backlog)
                    throws CoIOException {
        final NioCoScheduler scheduler = new NioCoScheduler("nio-cosched-" + endpoint);
        NioCoServerSocket ssSocket = null;
        boolean failed = true;
        try {
            ssSocket = new NioCoServerSocket(acceptorClass, connectorClass, scheduler);
            ssSocket.bind(endpoint, backlog);
            // Boot itself
            scheduler.startAndServe();
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(ssSocket);
            }
            scheduler.shutdown();
        }
    }
    
}
