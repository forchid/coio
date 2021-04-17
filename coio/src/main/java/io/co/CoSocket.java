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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.offbynull.coroutines.user.Continuation;

/**
 * A socket based on coroutines.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public abstract class CoSocket implements CoChannel {
    
    protected static final int SO_TIMEOUT = Integer.getInteger("io.co.soTimeout", 0);

    private int soTimeout = SO_TIMEOUT;

    protected CoSocket() {

    }
    
    public int getSoTimeout(){
        return this.soTimeout;
    }
    
    public void setSoTimeout(int soTimeout) throws IllegalArgumentException {
        if(soTimeout < 0){
            throw new IllegalArgumentException("soTimeout " + soTimeout);
        }
        this.soTimeout = soTimeout;
    }

    /** Returns the address of the endpoint this socket is connected to, or null
     * if it is unconnected.
     *
     * @return The address of the endpoint this socket is connected to, or null
     *  if it is not connected yet
     */
    public abstract SocketAddress getRemoteSocketAddress();

    /** Returns the address to which the socket is connected.
     *
     * @return The remote IP address to which this socket is connected, or null
     *  if the socket is not connected.
     */
    public abstract InetAddress getInetAddress();

    /** Returns the remote port number to which this socket is connected.
     *
     * @return The remote port number to which this socket is connected, or 0
     *  if the socket is not connected yet
     */
    public abstract int getPort();

    /** Gets the local address to which the socket is bound.
     *
     * @return The local address to which the socket is bound
     */
    public abstract InetAddress getLocalAddress();

    /** Returns the address of the endpoint this socket is bound to.
     *
     * @return The address of the endpoint this socket is bound to, or null
     *  if the socket is not bound yet
     */
    public abstract SocketAddress getLocalSocketAddress();

    /** Returns the local port number to which this socket is bound.
     *
     * @return The local port number to which this socket is bound or -1
     *  if the socket is not bound yet
     */
    public abstract int getLocalPort();

    /** Returns the binding state of the socket.
     *
     * @return true if the socket was successfully bound to an address
     */
    public abstract boolean isBound();

    public void connect(Continuation co, int port) throws IOException {
        SocketAddress endpoint = new InetSocketAddress(port);
        connect(co, endpoint, this.soTimeout);
    }

    public void connect(Continuation co, String host, int port) throws IOException {
        connect(co, host, port, this.soTimeout);
    }

    public void connect(Continuation co, String host, int port, int timeout)
            throws IOException {
        SocketAddress endpoint = new InetSocketAddress(host, port);
        connect(co, endpoint, timeout);
    }

    public void connect(Continuation co, InetAddress address, int port)
            throws IOException {
        connect(co, address, port, this.soTimeout);
    }

    public void connect(Continuation co, InetAddress address, int port, int timeout)
            throws IOException {
        SocketAddress endpoint = new InetSocketAddress(address, port);
        connect(co, endpoint, timeout);
    }
    
    public void connect(Continuation co, SocketAddress endpoint) throws IOException {
        connect(co, endpoint, this.soTimeout);
    }
    
    public abstract void connect(Continuation co, SocketAddress endpoint, int timeout)
            throws IOException;

    public abstract int available(Continuation co) throws IOException;

    public abstract int read(Continuation co) throws IOException;

    public abstract int read(Continuation co, byte[] b) throws IOException;

    public abstract int read(Continuation co, byte[] b, int off, int len)
            throws IOException;

    public abstract int readFully(Continuation co, byte[] b) throws IOException;

    public abstract int readFully(Continuation co, byte[] b, int off, int len)
            throws IOException;

    public abstract long skip(Continuation co, final long n) throws IOException;
    
    public abstract CoInputStream getInputStream();

    public abstract void write(Continuation co, int b) throws IOException;

    public abstract void write(Continuation co, byte[] b) throws IOException;

    public abstract void write(Continuation co, byte[] b, int off, int len)
            throws IOException;

    public abstract void flush(Continuation co) throws IOException;
    
    public abstract CoOutputStream getOutputStream();

    public abstract boolean isConnected();

    public void shutdownInput() {
        CoInputStream in = getInputStream();
        in.close();
    }

    public void shutdownOutput() {
        CoOutputStream out = getOutputStream();
        out.close();
    }
    
}
