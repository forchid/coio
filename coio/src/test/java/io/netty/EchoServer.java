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
package io.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * A simple CoServerSocket demo.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class EchoServer {
    static final int soTimeout = 30000;

    public static void main(String[] args) throws Exception {
        final String host = System.getProperty("io.co.host", "localhost");
        SocketAddress endpoint = new InetSocketAddress(host, 9999);
        
        final int threads = 4;
        final ServerBootstrap boot = new ServerBootstrap();
        final EventLoopGroup group = new NioEventLoopGroup(1);
        final EventLoopGroup child = new NioEventLoopGroup(threads);
        try {
            boot.group(group, child)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new Connector());
                }
            });
            
            boot.bind(endpoint);
            group.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            
            System.out.println("Bye");
        } finally{
            child.shutdownGracefully();
            group.shutdownGracefully();
        }
    }

    static class Connector extends ChannelInboundHandlerAdapter {
        final byte[] b = new byte[512];
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            final ByteBuf buf = (ByteBuf)message;
            if(buf.readableBytes() < b.length) {
                ctx.read();
                return;
            }
            
            buf.readBytes(b);
            buf.release();
            // next step
            send(ctx);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, final Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
        
        void send(final ChannelHandlerContext ctx) {
            final ByteBuf buf = Unpooled.wrappedBuffer(b);
            ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess()) {
                        ctx.read();
                        return;
                    }
                    ctx.close();
                }
            });
        }
        
    }
}
