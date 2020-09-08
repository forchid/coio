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

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.CoroutineException;
import com.offbynull.coroutines.user.CoroutineRunner;

import io.co.*;
import io.co.util.IoUtils;
import io.co.util.ReflectUtils;
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
    
    protected final String name;
    protected final boolean daemon;

    private final Object startSync = new Object();
    private volatile boolean started, shutdown;
    private volatile boolean terminated;
    private volatile Thread schedulerThread;
    private volatile Throwable cause;
    
    private NioCoChannel<?>[] channels;
    private final int maxConnections;
    private int maxChanSlot;
    private int freeChanSlot = -1;
    
    private NioCoTimer[] timers;
    private int maxTimerSlot;
    private int freeTimerSlot = -1;
    
    protected final Selector selector;
    protected final AtomicBoolean wakeup;
    
    protected final NioScheduler[] children;
    private int nextChildSlot;
    final BlockingQueue<Runnable> syncQueue;
    
    public NioScheduler() {
        this(NAME, INIT_CONNECTIONS);
    }
    
    public NioScheduler(String name) {
        this(name, INIT_CONNECTIONS);
    }

    public NioScheduler(boolean daemon) {
        this(NAME, daemon, INIT_CONNECTIONS);
    }

    public NioScheduler(int initConnections) {
        this(NAME, initConnections);
    }

    public NioScheduler(String name, boolean daemon){
        this(name, daemon, INIT_CONNECTIONS);
    }
    
    public NioScheduler(String name, int initConnections) {
        this(name, initConnections, MAX_CONNECTIONS, CHILDREN_COUNT);
    }

    public NioScheduler(String name, boolean daemon, int initConnections) {
        this(name, daemon, initConnections, MAX_CONNECTIONS, CHILDREN_COUNT);
    }
    
    public NioScheduler(String name, int initConnections, int childrenCount) {
        this(name, initConnections, MAX_CONNECTIONS, childrenCount);
    }

    public NioScheduler(String name, boolean daemon, int initConnections, int childrenCount) {
        this(name, daemon, initConnections, MAX_CONNECTIONS, childrenCount);
    }

    public NioScheduler(String name, int initConnections, int maxConnections, int childrenCount) {
        this(name, DAEMON, initConnections, maxConnections, childrenCount);
    }
    
    public NioScheduler(String name, boolean daemon, int initConnections, int maxConnections, int childrenCount) {
        debug("%s - NioCoScheduler(): daemon = %s, initConnections = %s, maxConnections = %s, childrenCount = %s",
                name, daemon, initConnections, maxConnections, childrenCount);
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
        if(childrenCount < 0){
            throw new IllegalArgumentException("childrenCount " + childrenCount);
        }
        
        this.name = name;
        this.daemon = daemon;
        this.channels = new NioCoChannel<?>[initConnections];
        this.maxConnections = maxConnections;
        
        this.timers    = new NioCoTimer[1024];
        this.syncQueue = new LinkedBlockingQueue<>();
        this.children  = new NioScheduler[childrenCount];
        
        try {
            this.wakeup = new AtomicBoolean();
            this.selector = Selector.open();
        } catch (final IOException cause){
            throw new CoIOException(cause);
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
    public void start() {
        synchronized(this.startSync) {
            if(this.schedulerThread != null) {
                throw new IllegalStateException("Scheduler had started");
            }
            
            this.schedulerThread = new Thread(this::startAndServe, this.name);
            this.schedulerThread.setDaemon(this.daemon);
            this.schedulerThread.start();
        }
    }
    
    @Override
    public void startAndServe() {
        final String name = this.name;

        synchronized(this.startSync) {
            if(this.schedulerThread != null) {
                if(!inScheduler() || inScheduler() && isStarted()) {
                    throw new IllegalStateException("Scheduler had started");
                }
            }
            this.started = true;
            this.schedulerThread = Thread.currentThread();
        }
        this.schedulerThread.setName(name);

        NioScheduler[] children = this.children;
        int childCount = children.length;
        int minCon = this.channels.length;
        for (int i = 0; i < childCount; ++i) {
            // Child threads
            NioScheduler child;
            String childName = String.format("%s-child-%s", name, i);
            child = new NioScheduler(childName, this.daemon, minCon, this.maxConnections, 0);
            children[i] = child;
            child.start();
        }
        
        serve();
    }
    

    @Override
    public void awaitTermination() throws InterruptedException, IllegalStateException {
        this.schedulerThread.join();
        Throwable cause = this.cause;
        if (cause != null) throw new IllegalStateException(cause);
    }
    
    @Override
    public boolean awaitTermination(long millis)
            throws InterruptedException, IllegalStateException {

        this.schedulerThread.join(millis);
        Throwable cause = this.cause;
        if (cause != null) throw new IllegalStateException(cause);

        return isTerminated();
    }
    
    public void schedule(final NioCoTimer timerTask) {
        final NioScheduler self = this;

        execute(() -> {
            if(timerTask.isCanceled()) {
                return;
            }

            if(timerTask.id != -1) {
                throw new IllegalStateException("timerTask id had set: " + timerTask.id);
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
        });
    }
    
    @Override
    public void schedule(CoSocket socket, Runnable task, long delay) {
        schedule(socket, task, delay, 0L);
    }
    
    @Override
    public void schedule(CoSocket socket, Runnable task, long delay, long period) {
        final NioCoSocket nioSocket = (NioCoSocket)socket;
        final NioCoTimer timer = new NioCoTimer(nioSocket, task, delay, period);
        schedule(timer);
    }
    
    @Override
    public Future<?> execute(final Runnable task) throws CoIOException {
        return execute(task, null);
    }
    
    @Override
    public <V> Future<V> execute(Runnable task, V value) throws CoIOException {
        final FutureTask<V> future = new FutureTask<>(task, value);
        
        if(this.inScheduler()) {
            future.run();
            return future;
        }
        
        final boolean ok = this.syncQueue.offer(future);
        if(ok){
            if(this.wakeup.compareAndSet(false, true)){
                this.selector.wakeup();
            }
            return future;
        }
        
        throw new CoIOException("Execution queue full");
    }
    
    @Override
    public void await(Continuation co, final long millis) {
        if(millis < 0L) {
            throw new IllegalArgumentException("millis " + millis);
        }
        
        final NioCoSocket sock = (NioCoSocket)co.getContext();
        final NioCoTimer timer = new NioCoTimer(sock, millis);
        this.schedule(timer);
        // Do wait
        co.suspend();
    }
    
    @Override
    public void close(final CoChannel channel) {
        if(channel == null){
            return;
        }
        
        final NioCoChannel<?> chan = (NioCoChannel<?>)channel;
        if(chan.id() == -1){
            return;
        }
        
        execute(() -> {
            final NioCoChannel<?> scChan = slotCoChannel(chan);
            if(scChan != null && scChan == chan) {
                final int slot = chan.id();
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
        
        for (Scheduler child: this.children) {
            if(child == null){
                continue;
            }
            child.shutdown();
        }
    }
    
    @Override
    public boolean isStarted() {
        return this.started;
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
        return (this.schedulerThread == Thread.currentThread());
    }

    NioScheduler register(final NioCoChannel<?> channel) {
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
        final int size = Math.min(this.maxConnections, n << 1);
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
    
    protected void serve(){
        for(;;){
            try {
                // Shutdown handler
                if(tryShutdown()) {
                    break;
                }
                
                // Do selection
                final int n = doSelect();
                if(n == 0) {
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
                
            } catch (final Throwable uncaught) {
                this.terminate();
                error("Scheduler fatal", uncaught);
                break;
            }
        }
    }
    
    private int doSelect() throws IOException {
        final Selector selector = this.selector;
        
        final long minRunAt = minRunAt();
        //debug("minRunAt = %s, maxTimerSlot = %s", minRunAt, this.maxTimerSlot);
        
        // Has new runner-or-timer?
        final boolean wakened = this.wakeup.compareAndSet(true, false);
        if(wakened || (minRunAt == 0L && this.maxTimerSlot > 0)) {
            debug("selectNow()");
            return selector.selectNow();
        }
        
        return selector.select(minRunAt);
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
    
    private void processIO() throws IOException {
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
            
            try {
                final Runnable runner = this.syncQueue.poll();
                if(runner == null){
                    return;
                }
                
                runner.run();
            } catch (final CoroutineException e) {
                final Throwable cause = e.getCause();
                debug("Uncaught exception in coroutine", cause);
            }
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
    
    protected void resume(NioCoChannel<?> coChannel) {
        try {
            CoroutineRunner coRunner = coChannel.coRunner();
            if (!coRunner.execute()) {
                Object context = coRunner.getContext();
                if (context instanceof Throwable) {
                    this.cause = (Throwable)context;
                }
                debug("Coroutine completed then close %s", coChannel);
                IoUtils.close(coChannel);
            }
        } catch (CoroutineException e) {
            debug("Coroutine executes failed", e);
            IoUtils.close(coChannel);
        }
    }
    
    protected void doWrite(final SelectionKey key) {
        final NioCoChannel<?> socket = (NioCoChannel<?>)key.attachment();
        resume(socket);
    }
    
    protected void doRead(final SelectionKey key) {
        final NioCoChannel<?> socket = (NioCoChannel<?>)key.attachment();
        resume(socket);
    }
    
    protected void doConnect(final SelectionKey key) {
        final SocketChannel chan = (SocketChannel)key.channel();
        final NioCoSocket socket = (NioCoSocket)key.attachment();
        boolean failed = true;
        try {
            final NioCoChannel<?> coChan = slotCoChannel(socket);
            if(coChan == null){
                // Had closed when timeout etc
                return;
            }
            
            socket.cancelConnectionTimer();
            final CoroutineRunner coRunner = coChan.coRunner();
            try {
                chan.finishConnect();
                debug("doConnect(): %s", chan);
                IoUtils.disableOps(SelectionKey.OP_CONNECT, key, this.selector, socket);
                failed = false;
            } catch (final IOException cause) {
                SocketHandler connector = socket.getConnector();
                connector.exceptionCaught(cause);
                return;
            }
            // Connected
            coRunner.setContext(socket);
            resume(socket);
        } finally {
            if(failed){
                IoUtils.close(socket);
            }
        }
    }
    
    protected void doAccept(final SelectionKey key) throws IOException {
        PassiveNioCoSocket coSocket = null;
        SocketChannel chan = null;
        boolean failed = true;
        try {
            final ServerSocketChannel ssChan = (ServerSocketChannel)key.channel();
            final NioCoServerSocket cosSocket = (NioCoServerSocket)key.attachment();
            try {
                chan = ssChan.accept();
                chan.configureBlocking(false);
                chan.socket().setTcpNoDelay(true);
                
                // 1. Create coSocket
                Class<? extends SocketHandler> conClass = cosSocket.getConnectorClass();
                if(this.children.length != 0){
                    // 3-2. Post coSocket
                    postSocket(chan, conClass);
                    failed = false;
                    return;
                }
                final SocketHandler coConnector = ReflectUtils.newObject(conClass);
                coSocket = new PassiveNioCoSocket(coConnector, this, chan);
            } catch (final CoIOException cause){
                debug("Create accepted socket error", cause);
                return;
            } finally {
                // 2. Next accept
                resume(cosSocket);
            }

            // 3-1. Start coSocket
            coSocket.start();
            
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(chan);
                this.close(coSocket);
            }
        }
    }
    
    private void postSocket(SocketChannel channel,
                            Class<? extends SocketHandler> connectorClass) {
        final NioScheduler[] children = this.children;
        final int childCount = children.length;
        NioScheduler child = null;
        for(int i = 0; i < childCount; ++i){
            if(++this.nextChildSlot >= childCount){
                this.nextChildSlot = 0;
            }
            final NioScheduler c = children[this.nextChildSlot];
            if(!c.isShutdown()){
                child = c;
                break;
            }
        }
        if(child == null){
            IoUtils.close(channel);
            if(isShutdown()) {
                return;
            }
            throw new IllegalStateException("No valid child scheduler available");
        }

        PostSocketRunner runner = new PostSocketRunner(child, channel, connectorClass);
        boolean ok = child.syncQueue.offer(runner);
        if (ok) {
            child.selector.wakeup();
            debug("postSocket() ok");
        } else {
            IoUtils.close(channel);
        }
    }

    int nextTimerSlot() {
        final int freeSlot = this.freeTimerSlot;
        final int maxSlot = this.maxTimerSlot;
        
        debug("nextTimerSlot(): freeSlot = %s, maxSlot = %s", freeSlot, maxSlot);
        if(freeSlot != -1){
            this.freeTimerSlot = -1;
            return freeSlot;
        }
        
        final NioCoTimer[] timers = this.timers;
        final int n = timers.length;
        
        if(maxSlot < n){
            if(maxSlot > 0) {
                final int slot = maxSlot - 1; 
                // First try to reuse the last slot!
                final NioCoTimer tailTimer = timers[slot];
                if(tailTimer == null || tailTimer.isCanceled()) {
                    timers[slot] = null;
                    return slot;
                }
            }
            
            return this.maxTimerSlot++;
        }
        
        for(int i = 0; i < n; ++i){
            final NioCoTimer timer = timers[i];
            if(timer == null || timer.isCanceled()){
                return i;
            }
        }
        
        // expand
        final NioCoTimer[] newTimers = new NioCoTimer[n << 1];
        System.arraycopy(timers, 0, newTimers, 0, n);
        this.timers = newTimers;
        
        return this.maxTimerSlot++;
    }
    
    private long minRunAt() {
        final NioCoTimer[] timers  = this.timers;
        List<NioCoTimer> runTimers = null;
        
        final int n = Math.min(this.maxTimerSlot, timers.length);
        final long cur = System.currentTimeMillis();
        long min = Long.MAX_VALUE;
        boolean noMin = true;
        
        for(int i = 0; i < n; ++i){
            final NioCoTimer timer = timers[i];
            if(timer == null || timer.isCanceled()){
                timers[i] = null;
                this.freeTimerSlot = i;
                continue;
            }
            final long runAt = timer.runat();
            if(runAt <= cur){
                if(runTimers == null) {
                    runTimers = new ArrayList<>();
                    runTimers.add(timer);
                }else{
                    // Insertion sort
                    for(int j = runTimers.size(); j > 0; --j){
                        final NioCoTimer t = runTimers.get(j - 1);
                        if(runAt < t.runat()){
                            continue;
                        }
                        runTimers.add(j, timer);
                        break;
                    }
                }
                continue;
            }
            if(runAt < min){
                min = runAt;
                noMin = false;
            }
        }
        
        // recycle timer slot
        recycleTimerSlots();
        
        // Execute timer
        if(runTimers != null) {
            runTimers.forEach(timer -> {
                try {
                    timer.run();
                } catch (final Exception e) {
                    timer.cancel();
                    debug("Timer runs error", e);
                }
            });
        }
        
        if(noMin){
            return 0L;
        }
        
        return  (min - cur);
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

    static class PostSocketRunner implements Runnable {
        
        final NioScheduler scheduler;
        final SocketChannel channel;
        final Class<? extends SocketHandler> connectorClass;

        PostSocketRunner(NioScheduler scheduler, SocketChannel channel,
                         Class<? extends SocketHandler> connectorClass){
            this.scheduler = scheduler;
            this.channel   = channel;
            this.connectorClass = connectorClass;
        }
        
        @Override
        public void run() {
            final NioScheduler scheduler = this.scheduler;
            PassiveNioCoSocket coSocket = null;
            boolean failed = true;
            try {
                SocketHandler connector = ReflectUtils.newObject(connectorClass);
                coSocket = new PassiveNioCoSocket(connector, scheduler, this.channel);
                coSocket.start();
                failed = false;
            } finally {
                if(failed){
                    IoUtils.close(this.channel);
                    IoUtils.close(coSocket);
                }
            }
        }
        
    }

    @Override
    protected void finalize() {
        shutdown();
    }

}
