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
package io.co;

import java.io.Closeable;
import java.io.IOException;

import com.offbynull.coroutines.user.Continuation;

/**
 * An output stream based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoOutputStream implements Closeable {
    
    protected final static int BUFFER_SIZE = Integer.getInteger("io.co.outBuffer.size", 4096);
    
    public abstract void write(Continuation co, int b) throws IOException;
    
    public void write(Continuation co, byte[] b) throws IOException {
        write(co, b, 0, b.length);
    }
    
    public void write(Continuation co, byte[] b, int off, int len) throws IOException {
        for(;off < len;) {
            write(co, b[off++]);
        }
    }
    
    public abstract void flush(Continuation co) throws IOException;
    
    @Override
    public abstract void close();

}
