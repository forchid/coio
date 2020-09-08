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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;
import static io.co.CoServerSocket.*;
import static io.co.util.LogUtils.*;

/**
 * The default accept coroutine.
 * 
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class Acceptor implements ServerSocketHandler {

    private static final long serialVersionUID = 1L;
    
    public Acceptor() {
        
    }
    
    @Override
    public void handle(Continuation co, CoServerSocket serverSocket) throws Exception {
        int port = serverSocket.getPort();
        if (PORT_UNDEFINED == port) {
            info("%s wait for bind() call", serverSocket);
            co.suspend();
        } else {
            InetAddress address = serverSocket.getBindAddress();
            SocketAddress bindAddress = new InetSocketAddress(address, port);
            serverSocket.bind(co, bindAddress, serverSocket.getBacklog());
        }
        SocketAddress sa = serverSocket.getLocalAddress();
        info("%s listen on %s", serverSocket, sa);

        Scheduler scheduler = serverSocket.getScheduler();
        while (!scheduler.isShutdown()) {
            handleAcception(co, serverSocket);
        }
    }

    protected void handleAcception(Continuation co, CoServerSocket serverSocket) {
        serverSocket.accept(co);
    }

}
