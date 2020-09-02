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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * A simple Socket demo.
 * 
 * @author little-pan
 * @since 2019-05-16
 *
 */
public class EchoClient {
    static final int soTimeout = 30000;
    static final boolean debug = Boolean.getBoolean("io.co.debug");

    public static void main(String[] args) throws Exception {
        
        final long ts = System.currentTimeMillis();
        final String host = System.getProperty("io.co.host", "localhost");
        final SocketAddress remote = new InetSocketAddress(host, 9999);
        
        final int conns, threads = 2;
        if(args.length > 0){
            conns = Integer.parseInt(args[0]);
        }else{
            conns = 250;
        }
        
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger idgen   = new AtomicInteger();
        final CountDownLatch latch  = new CountDownLatch(conns);
        final Bootstrap boot = new Bootstrap();
        final EventLoopGroup group= new NioEventLoopGroup(threads);
        try {
            boot.group(group)
            .remoteAddress(remote)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new Connector(idgen.getAndIncrement(), success, latch));
                }
            });
            
            for(int i = 0; i < conns; ++i) {
                boot.connect().addListener(new ChannelFutureListener(){
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if(!future.isSuccess()) {
                            latch.countDown();
                            future.channel().close();
                        }
                    }
                });
            }
            
            latch.await();
            System.out.println(String.format("Bye: conns = %s, success = %s, time = %sms",
                    conns, success, System.currentTimeMillis() - ts));
            
        } finally {
            group.shutdownGracefully();
        }
        
    }
    
    static class Connector extends ChannelInboundHandlerAdapter {
        final long ts = System.currentTimeMillis();
        final byte[] b = new byte[512];
        final int requests = 100;
        int cureqs = 0;
        
        final CountDownLatch latch;
        final AtomicInteger success;
        final int id;
        
        Connector(int id, AtomicInteger success, CountDownLatch latch){
            this.success = success;
            this.id = id;
            this.latch = latch;
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            send(ctx);
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            final ByteBuf buf = (ByteBuf)message;
            try {
                if(buf.readableBytes() < b.length) {
                    ctx.read();
                    return;
                }
                if(++cureqs >= requests) {
                    success.incrementAndGet();
                    latch.countDown();
                    ctx.close();
                    info();
                    return;
                }
                
                buf.readBytes(b);
            }finally{
                buf.release();
            }
            
            // next step
            send(ctx);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, final Throwable cause) {
            latch.countDown();
            if(debug) {
                cause.printStackTrace();
            }
            ctx.close();
            info();
        }
        
        void info() {
            System.out.println(String.format("[%s]Client-%05d: time %dms", 
                    Thread.currentThread().getName(), id, (System.currentTimeMillis() - ts)));
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
