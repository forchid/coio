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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import com.offbynull.coroutines.user.Coroutine;

/** 
 * @author little-pan
 * @since 2019-05-14
 *
 */
class PassiveNioCoSocket extends NioCoSocket {
    
    public PassiveNioCoSocket(Coroutine coConnector, SocketChannel channel, NioCoScheduler coScheduler){
        super(coConnector, channel, coScheduler);
    }
    
    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        throw new UnsupportedOperationException();
    }

}
