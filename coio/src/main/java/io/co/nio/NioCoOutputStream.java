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

import io.co.CoOutputStream;
import io.co.util.IoUtils;

/**
 * A NIO implementation of CoOutputStream.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoOutputStream extends CoOutputStream {
    
    protected final NioCoSocket socket;
    protected final Selector selector;
    protected final SocketChannel channel;
    private ByteBuffer buffer;
    
    public NioCoOutputStream(NioCoSocket socket, SocketChannel channel, Selector selector) {
        this(socket, channel, selector, BUFFER_SIZE);
    }
    
    public NioCoOutputStream(NioCoSocket socket, SocketChannel channel, Selector selector,
                             int bufferSize) {
        this.socket  = socket;
        this.selector= selector;
        this.channel = channel;
        this.buffer  = ByteBuffer.allocate(bufferSize);
    }
    
    @Override
    public void write(Continuation co, int b) throws IOException {
        final ByteBuffer buf = this.buffer;
        if(buf.hasRemaining()){
            buf.put((byte)b);
            return;
        }
        flush(co);
        write(co, b);
    }
    
    public void write(Continuation co, byte[] b, int off, int len) throws IOException {
        final ByteBuffer buf = this.buffer;
        if(buf.hasRemaining()){
            final int size = Math.min(buf.remaining(), len);
            buf.put(b, off, size);
            if(buf.hasRemaining()){
                return;
            }
            flush(co);
            off += size;
            len -= size;
            if(len == 0){
                return;
            }
        }else{
            flush(co);
        }
        if(len == 0){
            return;
        }
        
        // Pass through buffer
        final ByteBuffer newBuf = ByteBuffer.wrap(b, off, len);
        flush(co, newBuf);
    }
    
    @Override
    public void flush(Continuation co) throws IOException {
        final ByteBuffer buf = this.buffer;
        buf.flip();
        flush(co, buf);
        buf.clear();
    }
    
    protected void flush(Continuation co, final ByteBuffer buf) throws IOException {
        if(!buf.hasRemaining()){
            return;
        }
        
        final SocketChannel ch = this.channel;
        final SelectionKey selKey = IoUtils.enableWrite(ch, this.selector, this.socket);
        try{
            while (buf.hasRemaining()) {
                final int n = ch.write(buf);
                if (n == 0) {
                    this.socket.suspend(co);
                }
            }
        } finally {
            IoUtils.disableWrite(selKey, this.selector, this.socket);
        }
    }
    
    @Override
    public void close() {
        try {
            this.channel.shutdownOutput();
        } catch (final IOException e) {
            // ignore
        }
        this.buffer = null;
    }

}
