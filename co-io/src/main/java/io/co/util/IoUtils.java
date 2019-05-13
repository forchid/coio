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
package io.co.util;

import java.io.*;
import java.nio.channels.*;

/**
 * IO utils.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public final class IoUtils {
    
    private IoUtils(){
        // noop
    }
    
    public static void close(Closeable closeable){
        if(closeable != null){
            try {
                closeable.close();
            } catch (final Exception e) {
                // ignore
            }
        }
    }
    
    public static SelectionKey enableRead(SelectableChannel chan, Selector selector, Object attachment){
        return enableOps(SelectionKey.OP_READ, chan, selector, attachment);
    }
    
    public static void disableRead(SelectionKey selKey, Selector selector, Object attachment){
        disableOps(SelectionKey.OP_READ, selKey, selector, attachment);
    }
    
    public static SelectionKey enableWrite(SelectableChannel chan, Selector selector, Object attachment){
        return enableOps(SelectionKey.OP_WRITE, chan, selector, attachment);
    }
    
    public static void disableWrite(SelectionKey selKey, Selector selector, Object attachment){
        disableOps(SelectionKey.OP_WRITE, selKey, selector, attachment);
    }
    
    public static void disableOps(int ops, SelectionKey selKey, Selector selector, Object attachment){
        final int iOps = selKey.interestOps();
        try {
            final SelectableChannel chan = selKey.channel();
            chan.register(selector, iOps&(~ops), attachment);
        } catch (final ClosedChannelException e) {
            // ignore;
        }
    }
    
    public static SelectionKey enableOps(int ops, 
            SelectableChannel chan, Selector selector, Object attachment){
        SelectionKey selKey = chan.keyFor(selector);
        try {
            // 启用读
            if(selKey == null){
                selKey = chan.register(selector, ops, attachment);
            }else{
                final int iOps = selKey.interestOps();
                chan.register(selector, iOps|ops, attachment);
            }
        } catch (final ClosedChannelException cause){
            // ignore
        }
        return selKey;
    }

}
