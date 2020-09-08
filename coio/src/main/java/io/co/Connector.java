/*
 * Copyright (c) 2020, little-pan, All rights reserved.
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

import com.offbynull.coroutines.user.Continuation;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The default connect coroutine.
 *
 * @author little-pan
 * @since 2019-09-08
 *
 */
public abstract class Connector implements SocketHandler {

    @Override
    public void handle(Continuation co, CoSocket socket) throws Exception {
        int port = socket.getPort();
        if (CoSocket.PORT_UNDEFINED != port && !socket.isConnected()) {
            SocketAddress sa = new InetSocketAddress(socket.getAddress(), port);
            socket.connect(co, sa, socket.getSoTimeout());
            // wait for connection finished
            co.suspend();
        }
        handleConnection(co, socket);
    }

    public abstract void handleConnection(Continuation co, CoSocket socket)
            throws Exception;

}
