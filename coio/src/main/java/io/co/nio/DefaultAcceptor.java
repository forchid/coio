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

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

import io.co.CoScheduler;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

/**
 * The default accept coroutine.
 * 
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class DefaultAcceptor implements Coroutine {
    private static final long serialVersionUID = 1608438566384500434L;
    
    public DefaultAcceptor(){
        
    }
    
    @Override
    public void run(final Continuation co) throws Exception {
        final Object context = co.getContext();

        if (context instanceof Exception) throw (Exception)context;
        if (context instanceof Error) throw (Error)context;

        NioCoServerSocket ssSocket = (NioCoServerSocket)context;
        final ServerSocketChannel chan = ssSocket.channel();
        SocketAddress sa = chan.getLocalAddress();
        NioCoScheduler.debug("Server listen on %s", sa);
        final CoScheduler scheduler = ssSocket.getScheduler();
        while (!scheduler.isShutdown()) {
            ssSocket.accept(co);
        }
    }
    
}
