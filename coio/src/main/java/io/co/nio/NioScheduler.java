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
package io.co.nio;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.CoroutineException;
import com.offbynull.coroutines.user.CoroutineRunner;

import io.co.*;
import io.co.nio.NioCoServerSocket.AcceptResult;
import io.co.util.IoUtils;
import static io.co.util.LogUtils.*;

/**
 * The coroutine scheduler based on NIO.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioScheduler implements Scheduler {

    static final int ioRatio = initIoRatio();
    
    protected String name;
    protected boolean daemon;

    private volatile boolean shutdown;
    private volatile boolean terminated;
    private final AtomicReference<Thread> threadRef;
    
    private NioCoChannel<?>[] channels;
    private final int maxConnections;
    private int maxChanSlot;
    private int freeChanSlot = -1;
    
    private NioCoTimer[] timers;
    private int maxTimerSlot;
    private int freeTimerSlot = -1;
    private boolean timersChanged;
    
    protected final Selector selector;
    protected final AtomicBoolean wakeup;
    final BlockingQueue<Runnable> syncQueue;
    
    public NioScheduler() throws IOError {
        this(NAME, MAX_CONNECTIONS);
    }
    
    public NioScheduler(String name) throws IOError {
        this(name, MAX_CONNECTIONS);
    }

    public NioScheduler(int maxConnections) throws IOError {
        this(NAME, maxConnections);
    }
    
    public NioScheduler(String name, int maxConnections) throws IOError {
        this(name, INIT_CONNECTIONS, maxConnections);
    }
    
    public NioScheduler(String name, int initConnections, int maxConnections) throws IOError {
        debug("%s - NioCoScheduler(): initConnections = %s, maxConnections = %s",
                name, initConnections, maxConnections);
        if(initConnections < 0){
            throw new IllegalArgumentException("initConnections " + initConnections);
        }
        if(maxConnections  < 0){
            throw new IllegalArgumentException("maxConnections " + maxConnections);
        }
        if(initConnections > maxConnections){
            final String fmt = "initConnections %s bigger than maxConnections %s";
            throw new IllegalArgumentException(String.format(fmt, initConnections, maxConnections));
        }

        this.name = name;
        this.threadRef = new AtomicReference<>();
        this.channels  = new NioCoChannel<?>[initConnections];
        this.maxConnections = maxConnections;
        
        this.timers    = new NioCoTimer[1 << 10/* Init capacity */];
        this.syncQueue = new LinkedBlockingQueue<>();

        try {
            this.selector = Selector.open();
            this.wakeup = new AtomicBoolean();
        } catch (IOException cause){
            throw new IOError(cause);
        }
    }
    
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean isDaemon() {
        return this.daemon;
    }

    @Override
    public void awaitTermination() throws InterruptedException, IllegalStateException {
        Thread thread = this.threadRef.get();
        if (thread == null) throw new IllegalStateException("Scheduler not started");
        thread.join();
    }
    
    @Override
    public boolean awaitTermination(long millis)
            throws InterruptedException, IllegalStateException {

        Thread thread = this.threadRef.get();
        if (thread == null) throw new IllegalStateException("Scheduler not started");
        thread.join(millis);

        return isTerminated();
    }
    
    protected void schedule(final NioCoTimer timerTask) {
        final NioScheduler self = this;

        execute(() -> {
            if(timerTask.isCanceled()) {
                return;
            }
            if(timerTask.id != -1) {
                String error = "timerTask id had set: " + timerTask.id;
                throw new IllegalStateException(error);
            }

            final int slot = self.nextTimerSlot();
            timerTask.id = slot;
            debug("Schedule timer: %s", timerTask);

            final NioCoTimer oldTimer = self.timers[slot];
            if(oldTimer != null && !oldTimer.isCanceled()){
                final String f = "Time runner slot %s used: %s";
                final String error = String.format(f, slot, oldTimer);
                debug("schedule(): %s", error);
                throw new IllegalStateException(error);
            }
            self.timers[slot] = timerTask;
            this.timersChanged= true;
        });
    }
    
    @Override
    public void schedule(Runnable task, long delay) throws NullPointerException {
        schedule(task, delay, 0);
    }
    
    @Override
    public void schedule(Runnable task, long delay, long period) throws NullPointerException {
        NioCoTimer timer = new NioCoTimer(this, task, delay, period);
        schedule(timer);
    }
    
    @Override
    public Future<?> execute(final Runnable task) throws IllegalStateException {
        return execute(task, null);
    }
    
    @Override
    public <V> Future<V> execute(Runnable task, V value) throws IllegalStateException {
        final FutureTask<V> future = new FutureTask<>(task, value);
        
        if (inScheduler()) {
            future.run();
            return future;
        }
        
        boolean ok = this.syncQueue.offer(future);
        if(ok){
            if(this.wakeup.compareAndSet(false, true)){
                this.selector.wakeup();
            }
            return future;
        }
        
        throw new IllegalStateException("Task queue full");
    }
    
    @Override
    public void await(Continuation co, long millis) {
        if(millis < 0L) {
            throw new IllegalArgumentException("millis " + millis);
        }

        CoContext context = (CoContext)co.getContext();
        NioCoTimer timer = new NioCoTimer(context, this, millis);
        schedule(timer);
        co.suspend();
    }
    
    @Override
    public void close(final CoChannel channel) {
        if(channel == null){
            return;
        }
        
        final NioCoChannel<?> ch = (NioCoChannel<?>)channel;
        if(ch.id() == -1){
            return;
        }
        
        execute(() -> {
            final NioCoChannel<?> scChan = slotCoChannel(ch);
            if(scChan != null && scChan == ch) {
                final int slot = ch.id();
                recycleChanSlot(slot);
                debug("Close: %s", scChan);
            }
        });
    }
    
    @Override
    public void shutdown() {
        Selector sel = this.selector;
        this.shutdown = true;
        if(sel != null){
            sel.wakeup();
        }
    }
    
    @Override
    public boolean isStarted() {
        return this.threadRef.get() != null;
    }
    
    @Override
    public boolean isShutdown() {
        return this.shutdown;
    }
    
    @Override
    public boolean isTerminated(){
        return this.terminated;
    }
    
    @Override
    public boolean inScheduler() {
        final Thread thread = this.threadRef.get();
        return (thread == Thread.currentThread() || thread == null);
    }

    NioScheduler register(final NioCoChannel<?> channel)
            throws CoIOException, IllegalStateException {

        final int slot = nextChanSlot();
        channel.id(slot);
        
        final NioCoChannel<?> oldChan = this.channels[slot];
        if (oldChan != null && oldChan != channel && oldChan.isOpen()) {
            String s = String.format("Channel slot %s used", slot);
            throw new IllegalStateException(s);
        }
        
        this.channels[slot] = channel;
        return this;
    }
    
    <S extends Channel> NioCoChannel<?> slotCoChannel(final NioCoChannel<S> channel){
        final NioCoChannel<?> coChan = this.channels[channel.id()];
        if(coChan == null || coChan != channel){
            return null;
        }
        return coChan;
    }
    
    private int nextChanSlot() throws CoIOException {
        final int slot = this.freeChanSlot;
        if(slot != -1) {
            this.freeChanSlot = -1;
            return slot;
        }
            
        final NioCoChannel<?>[] channels = this.channels;
        final int n = channels.length;
        
        // quick allocate
        if(this.maxChanSlot < n){
            return this.maxChanSlot++;
        }
        
        // traverse
        for(int i = 0; i < n; ++i){
            final NioCoChannel<?> coChan = channels[i];
            if(coChan == null || !coChan.isOpen()){
                return i;
            }
        }
        
        // check limit
        if(this.maxChanSlot >= this.maxConnections){
            // Too many connections
            throw new CoIOException("Too many connections: " + this.maxChanSlot);
        }
        
        // expand
        final int size = Math.min(this.maxConnections, Math.max(n << 1, 2));
        final NioCoChannel<?>[] newChannels = new NioCoChannel<?>[size];
        System.arraycopy(channels, 0, newChannels, 0, n);
        this.channels = newChannels;
        return this.maxChanSlot++;
    }
    
    private void recycleChanSlot(final int slot){
        this.channels[slot] = null;
        this.freeChanSlot= slot;
        if(slot == this.maxChanSlot - 1){
            this.maxChanSlot--;
            this.freeChanSlot = -1;
            for(int i = slot - 1; i >= 0; --i){
                final NioCoChannel<?> c = this.channels[i];
                if(c != null && c.isOpen()){
                    break;
                }
                this.channels[i] = null;
                this.maxChanSlot--;
                // recycle free slot
                if(this.maxChanSlot == this.freeChanSlot) {
                    this.freeChanSlot = -1;
                }
            }
        }
    }

    @Override
    public void run(){
        final Thread thread = Thread.currentThread();
        if(!this.threadRef.compareAndSet(null, thread)) {
            throw new IllegalStateException("Scheduler started");
        }
        this.daemon = thread.isDaemon();

        while (!tryShutdown()) {
            // Shutdown handler

            // Do selection
            final int n = doSelect();
            if (n == 0) {
                execSyncRunners(-1L);
                continue;
            }

            // Process & execute runners
            if (ioRatio == 100) {
                try {
                    processIO();
                } finally {
                    execSyncRunners(-1L);
                }
            } else {
                final long ioStart = System.nanoTime();
                try {
                    processIO();
                } finally {
                    final long ioTime = System.nanoTime() - ioStart;
                    execSyncRunners(ioTime * (100 - ioRatio) / ioRatio);
                }
            }
        }
    }
    
    private int doSelect() throws IOError {
        final Selector selector = this.selector;
        final long timeout = minRunAt();

        try {
            // Has new runner-or-timer? Or shutdown?
            final boolean wakened = this.wakeup.compareAndSet(true, false);
            if (wakened || (timeout == 0L && (this.maxTimerSlot > 0 || isShutdown()))) {
                debug("selectNow()");
                return selector.selectNow();
            }

            return selector.select(timeout);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }
    
    private boolean tryShutdown() {
        if(!isShutdown()){
            return false;
        }
        
        final NioCoChannel<?>[] channels = this.channels;
        int actives = 0;
        for (final NioCoChannel<?> runChan : channels) {
            if (runChan == null) {
                continue;
            }
            final Channel chan = runChan.channel();
            if (chan instanceof ServerSocketChannel) {
                IoUtils.close(runChan);
                continue;
            }
            ++actives;
        }
        debug("tryShutdown(): active channels %s", actives);
        if(actives == 0){
            this.terminate();
            // Exit normally
            return true;
        }
        return false;
    }
    
    private void processIO() {
        Set<SelectionKey> selKeys = this.selector.selectedKeys();
        final Iterator<SelectionKey> i = selKeys.iterator();
        for(; i.hasNext(); i.remove()){
            NioCoChannel<?> coChan = null;
            boolean failed = false;
            try {
                final SelectionKey key = i.next();
                if(!key.isValid()){
                    failed = true;
                    continue;
                }
                coChan = (NioCoChannel<?>)key.attachment();
                if(key.isAcceptable()){
                    doAccept(key);
                    continue;
                }
                if(key.isConnectable()){
                    doConnect(key);
                    continue;
                }
                if(key.isReadable()){
                    doRead(key);
                }
                if(key.isValid() && key.isWritable()){
                    doWrite(key);
                }
            } catch (final CoroutineException e){
                failed = true;
                debug("Uncaught exception in coroutine", e.getCause());
            } finally {
                if(failed){
                    IoUtils.close(coChan);
                }
            }
        }
    }
    
    private void execSyncRunners(final long runNanos) {
        final long deadNano = System.nanoTime() + runNanos;
        for(;;){
            if(runNanos > 0L && System.nanoTime() >= deadNano) {
                return;
            }

            final Runnable runner = this.syncQueue.poll();
            if(runner == null){
                return;
            }

            runner.run();
        }
    }
    
    protected void terminate() {
        debug("terminate..");
        IoUtils.close(this.selector);
        this.shutdown();
        this.channels = null;
        this.timers   = null;
        this.terminated = true;
        debug("terminated");
    }
    
    protected void resume(CoContext context) {
        try {
            CoroutineRunner coRunner = context.coRunner();
            if (!coRunner.execute()) {
                debug("Coroutine completed then close %s", context);
                IoUtils.close(context);
            }
        } catch (CoroutineException e) {
            error("Coroutine failed: " + context, e);
            IoUtils.close(context);
        }
    }
    
    protected void doWrite(final SelectionKey key) {
        final NioCoSocket socket = (NioCoSocket)key.attachment();
        CoContext context = socket.getContext();
        resume(context);
    }
    
    protected void doRead(final SelectionKey key) {
        final NioCoSocket socket = (NioCoSocket)key.attachment();
        CoContext context = socket.getContext();
        resume(context);
    }
    
    protected void doConnect(final SelectionKey key) {
        SocketChannel ch = (SocketChannel)key.channel();
        NioCoSocket socket = (NioCoSocket)key.attachment();
        NioCoChannel<?> coChan = slotCoChannel(socket);
        if (coChan == null) {
            // Had closed when timeout etc
            return;
        }

        CoContext context = socket.getContext();
        boolean failed = true;
        try {
            socket.cancelConnectionTimer();
            ch.finishConnect();
            debug("doConnect(): %s", ch);
            IoUtils.disableOps(SelectionKey.OP_CONNECT, key, this.selector, socket);
            context.attach(socket);
            failed = false;
        } catch (IOException cause) {
            debug("Connect error", cause);
            context.attach(cause);
        } finally {
            if(failed){
                IoUtils.close(socket);
            }
        }

        resume(context);
    }
    
    protected void doAccept(final SelectionKey key) {
        NioCoSocket socket = null;
        SocketChannel ch = null;
        boolean failed = true;

        NioCoServerSocket server = (NioCoServerSocket)key.attachment();
        CoContext context = server.getContext();
        AcceptResult result;
        try {
            ServerSocketChannel ssChan = server.channel();
            ch = ssChan.accept();
            ch.configureBlocking(false);
            ch.socket().setTcpNoDelay(true);
            socket = new NioCoSocket(ch, this);
            register(socket);
            result = new AcceptResult(socket);
            failed = false;
        } catch (CoIOException cause) {
            debug("Register socket error", cause);
            return;
        } catch (IOException cause) {
            debug("Accept socket error", cause);
            result = new AcceptResult(cause);
        } finally {
            if(failed){
                IoUtils.close(ch);
                this.close(socket);
            }
        }

        if (context == null) {
            server.onAccept(result);
        } else {
            context.attach(result);
            resume(context);
        }
    }

    int nextTimerSlot() {
        final int freeSlot = this.freeTimerSlot;
        final int maxSlot = this.maxTimerSlot;
        
        debug("nextTimerSlot(): freeSlot = %s, maxSlot = %s", freeSlot, maxSlot);
        if (freeSlot != -1) {
            this.freeTimerSlot = -1;
            return freeSlot;
        }

        final NioCoTimer[] timers = this.timers;
        final int n = timers.length;

        if (maxSlot < n) {
            if (maxSlot > 0) {
                final int slot = maxSlot - 1;
                // First try to reuse the last slot!
                final NioCoTimer tailTimer = timers[slot];
                if (tailTimer == null || tailTimer.isCanceled()) {
                    timers[slot] = null;
                    return slot;
                }
            }

            return this.maxTimerSlot++;
        }

        for (int i = 0; i < n; ++i) {
            final NioCoTimer timer = timers[i];
            if (timer == null || timer.isCanceled()) {
                return i;
            }
        }

        // expand
        final NioCoTimer[] newTimers = new NioCoTimer[n << 1];
        System.arraycopy(timers, 0, newTimers, 0, n);
        this.timers = newTimers;

        return this.maxTimerSlot++;
    }

    protected long minRunAt() {
        List<NioCoTimer> runTimers = new ArrayList<>();

        long min = minRunAt(runTimers);
        this.timersChanged = false;
        // recycle timer slot
        recycleTimerSlots();
        // Execute timer
        runTimers.forEach(this::runTimer);

        // Note: we must calculate minRunAt again for select()
        // if some timers are added after timers running
        if (this.timersChanged) {
            min = minRunAt(null);
        }

        return min;
    }

    protected void runTimer(NioCoTimer timer) {
        try {
            timer.run();
        } catch (Exception e) {
            timer.cancel();
            debug("Timer runs error", e);
        }
    }

    protected long minRunAt(final List<NioCoTimer> runTimers) {
        final NioCoTimer[] timers  = this.timers;

        final int n = Math.min(this.maxTimerSlot, timers.length);
        final long cur = System.currentTimeMillis();
        long min = Long.MAX_VALUE;

        for (int i = 0; i < n; ++i) {
            final NioCoTimer timer = timers[i];
            if (timer == null || timer.isCanceled()) {
                timers[i] = null;
                this.freeTimerSlot = i;
                continue;
            }
            final long runAt = timer.runat();
            if (runAt <= cur && runTimers != null) {
                int j = runTimers.size();
                if (j == 0) {
                    runTimers.add(timer);
                    continue;
                }
                // Insertion sort
                for (; j > 0; --j) {
                    NioCoTimer t = runTimers.get(j - 1);
                    if(runAt < t.runat()){
                        continue;
                    }
                    runTimers.add(j, timer);
                    break;
                }
                continue;
            }
            if (runAt < min) {
                min = runAt;
            }
        }

        return  (Math.max(0, min - cur));
    }
    
    boolean cancel(final NioCoTimer coTimer) {
        final NioCoTimer[] timers = this.timers;
        final int slot = coTimer.id;
        final NioCoTimer timer = timers[slot];
        if(timer != coTimer){
            return false;
        }
        
        recycleTimer(slot);
        return true;
    }
    
    private void recycleTimer(final int slot){
        final NioCoTimer[] timers = this.timers;
        timers[slot] = null;
        this.freeTimerSlot = slot;
        
        if(slot == this.maxTimerSlot - 1){
            this.maxTimerSlot--;
            // Free slot recycled!
            this.freeTimerSlot = -1;
            recycleTimerSlots();
        }
    }
    
    private void recycleTimerSlots() {
        final NioCoTimer[] timers = this.timers;
        for(int i = this.maxTimerSlot - 1; i >= 0; --i){
            final NioCoTimer timer = timers[i];
            if(timer != null && !timer.isCanceled()){
                break;
            }
            timers[i] = null;
            this.maxTimerSlot--;
            // Free slot recycled!
            if(this.maxTimerSlot == this.freeTimerSlot) {
                this.freeTimerSlot = -1;
            }
        }
    }
    
    static int initIoRatio() {
        final int ioRatio = Integer.getInteger("io.co.ioRatio", 50);
        if(ioRatio <= 0 || ioRatio > 100) {
            final String error = "ioRatio "+ ioRatio + ", expect 0 < ioRatio <= 100";
            throw new ExceptionInInitializerError(error);
        }
        return ioRatio;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected void finalize() {
        shutdown();
    }

}
