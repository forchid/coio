/*
 * Copyright (c) 2021, little-pan, All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.offbynull.coroutines.user.Continuation;

import io.co.CoInputStream;
import io.co.util.IoUtils;

/**
 * The NIO implementation of CoInputStream.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoInputStream extends CoInputStream {

    final NioCoSocket socket;
    final SocketChannel channel;
    final Selector selector;
    protected ByteBuffer buffer;
    
    public NioCoInputStream(NioCoSocket socket, SocketChannel channel, Selector selector) {
        this(socket, channel, selector, BUFFER_SIZE);
    }
    
    public NioCoInputStream(NioCoSocket socket, SocketChannel channel, Selector selector,
                            int bufferSize) {
        this.socket  = socket;
        this.channel = channel;
        this.selector= selector;
        this.buffer  = ByteBuffer.allocate(bufferSize);
        this.buffer.flip();
    }

    @Override
    public int available(Continuation co) throws IOException {
        final ByteBuffer buf = this.buffer;
        if(buf.hasRemaining()) {
            return buf.remaining();
        }

        buf.clear();
        while (buf.hasRemaining()) {
            final int n = this.channel.read(buf);
            if(n == 0 || n == -1){
                break;
            }
        }
        buf.flip();

        return buf.remaining();
    }
    
    @Override
    public int read(Continuation co) throws IOException {
        final ByteBuffer buf = this.buffer;
        if(buf.hasRemaining()){
            return buf.get();
        }

        buf.clear();
        final int i = read(co, buf);
        if(i == -1) {
            return -1;
        }
        buf.flip();
        return buf.get();
    }
    
    public int read(Continuation co, byte[] b, int off, int len) throws IOException {
        if(len < 0) {
            throw new IllegalArgumentException("len " + len);
        }
        
        final int n = Math.min(len, available(co));
        if(n > 0){
            this.buffer.get(b, off, n);
            off += n;
            len -= n;
        }
        if(len == 0){
            return n;
        }
        
        // Pass through buffer
        final ByteBuffer newBuf = ByteBuffer.wrap(b, off, len);
        final int i = read(co, newBuf);
        if(i == -1){
            if(n == 0){
                return -1;
            }
            return n;
        }
        
        return (n + i);
    }
    
    protected int read(Continuation co, ByteBuffer buf) throws IOException {
        final SocketChannel ch = this.channel;
        SelectionKey key = IoUtils.enableRead(ch, this.selector, this.socket);
        try {
            while (true) {
                int i = ch.read(buf);
                if (i == -1) {
                    return -1;
                }
                if (i == 0) {
                    this.socket.startReadTimer(co);
                    this.socket.suspend(co);
                    this.socket.cancelReadTimer();
                    continue;
                }
                int n = i;
                // Read more
                while (buf.hasRemaining()) {
                    i = ch.read(buf);
                    if (i == 0 || i == -1) {
                        break;
                    }
                    n += i;
                }
                return n;
            }
        } finally {
            IoUtils.disableRead(key, this.selector, this.socket);
            this.socket.cancelReadTimer();
        }
    }
    
    @Override
    public void close() {
        try {
            this.channel.shutdownInput();
        } catch (final IOException e) {
            // ignore
        }
        this.buffer = null;
    }
    
}
