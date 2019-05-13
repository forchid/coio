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
package io.co;

import java.io.*;

import com.offbynull.coroutines.user.Continuation;

/**
 * An Input stream based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoInputStream implements Closeable {
    
    protected final static int BUFFER_SIZE = Integer.getInteger("io.co.inBuffer.size", 8192);

    public int available(Continuation co) throws CoIOException {
        return 0;
    }
    
    public abstract int read(Continuation co) throws CoIOException;
    
    public int read(Continuation co, byte[] b) throws CoIOException {
        return read(co, b, 0, b.length);
    }
    
    public int read(Continuation co, byte[] b, int off, int len) throws CoIOException {
        int i = 0;
        for(; i < len; ) {
            final int c = read(co);
            if(c == -1) {
                break;
            }
            b[off++] = (byte)c;
            ++i;
        }
        return i;
    }
    
    public long skip(Continuation co, long n) throws CoIOException {
        final byte[] buff = new byte[8192];
        final int len = (int)Math.min(buff.length, n);
        long i = 0L;
        for(; i < n;) {
            final int m = read(co, buff, 0, len);
            if(m == 0) {
                break;
            }
            i += m;
        }
        return i;
    }
    
    public abstract void close();
    
}
