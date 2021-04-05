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
    
    protected final static int BUFFER_SIZE = Integer.getInteger("io.co.inBuffer.size", 4096);

    public int available(Continuation co) throws IOException {
        return 0;
    }
    
    public abstract int read(Continuation co) throws IOException;
    
    public int read(Continuation co, byte[] b) throws IOException {
        return read(co, b, 0, b.length);
    }
    
    public int read(Continuation co, byte[] b, int off, int len) throws IOException {
        int c = read(co);
        if(c == -1) {
            return -1;
        }
        int i = 0;
        b[off++] = (byte)c;
        ++i;
        final int n = Math.min(len - 1, available(co));
        for(int j = 0 ; j < n; ++j) {
            c = read(co);
            b[off++] = (byte)c;
            ++i;
        }
        return i;
    }
    
    public long skip(Continuation co, final long n) throws IOException {
        final byte[] buff = new byte[8192];
        final int len = (int)Math.min(available(co), n);
        long i = 0L;
        while (i < len) {
            final int size = (int)Math.min(buff.length, len - i);
            final int m = read(co, buff, 0, size);
            if(m == -1) {
                break;
            }
            i += m;
        }
        return i;
    }
    
    public abstract void close();
    
}
