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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutor;

/**
 * A netty echo client demo.
 * 
 * @author little-pan
 * @since 2019-05-16
 *
 */
public class EchoClient {

    //static final int soTimeout = 30000;
    static final boolean debug = Boolean.getBoolean("io.co.debug");

    public static void main(String[] args) throws Exception {
        
        final long ts = System.currentTimeMillis();
        final String host = System.getProperty("io.co.host", "localhost");
        final SocketAddress remote = new InetSocketAddress(host, 9999);
        
        final int conns, requests, threads = 1;
        if (args.length > 0) {
            conns = Integer.parseInt(args[0]);
        } else {
            conns = 10000;
        }
        if (args.length > 1) {
            requests = Integer.parseInt(args[1]);
        } else {
            requests = 100;
        }
        
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger idGen   = new AtomicInteger();
        final CountDownLatch latch  = new CountDownLatch(conns);
        final Bootstrap boot = new Bootstrap();
        final EventLoopGroup group= new NioEventLoopGroup(threads);
        try {
            boot.group(group)
            .remoteAddress(remote)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.AUTO_READ, false)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    int id = idGen.getAndIncrement();
                    ChannelHandler handler = new Connector(id, success, latch, requests);
                    ch.pipeline().addLast(handler);
                }
            });
            
            for(int i = 0; i < conns; ++i) {
                boot.connect().addListener((ChannelFutureListener) future -> {
                    if(!future.isSuccess()) {
                        latch.countDown();
                        future.channel().close();
                    }
                });
            }
            
            latch.await();
            System.out.printf("Bye: conns = %s, success = %s, time = %sms%n",
                    conns, success, System.currentTimeMillis() - ts);
        } finally {
            group.shutdownGracefully();
        }
        
    }
    
    static class Connector extends ChannelInboundHandlerAdapter {
        final long ts = System.currentTimeMillis();
        final byte[] b = new byte[512];
        final int requests;
        int cureqs = 0;
        
        final CountDownLatch latch;
        final AtomicInteger success;
        final int id;
        
        Connector(int id, AtomicInteger success, CountDownLatch latch, int requests) {
            this.success = success;
            this.id = id;
            this.latch = latch;
            this.requests = requests;
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
            //String threadName = Thread.currentThread().getName();
            //long time = System.currentTimeMillis() - this.ts;
            //System.out.printf("[%s]Client-%05d: time %dms%n", threadName, this.id, time);
        }

        void send(final ChannelHandlerContext ctx) {
            final ByteBuf buf = Unpooled.wrappedBuffer(b);
            ctx.writeAndFlush(buf).addListener((ChannelFutureListener) future -> {
                if(!future.isSuccess()) {
                    ctx.close();
                }
            });
            EventExecutor executor = ctx.executor();
            executor.schedule(ctx::read, 1, TimeUnit.MILLISECONDS);
        }
        
    }

}
