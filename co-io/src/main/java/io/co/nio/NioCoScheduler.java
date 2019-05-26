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
import java.net.SocketAddress;
import java.nio.channels.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineException;
import com.offbynull.coroutines.user.CoroutineRunner;

import io.co.CoChannel;
import io.co.CoIOException;
import io.co.CoScheduler;
import io.co.CoServerSocket;
import io.co.CoSocket;
import io.co.util.IoUtils;
import io.co.util.ReflectUtils;

/**
 * The coroutine scheduler based on NIO.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public class NioCoScheduler implements CoScheduler {
    static final int ioRatio = initIoRatio();
    
    final String name;

    private final Object startSync = new Object();
    private volatile boolean started, shutdown;
    private volatile boolean terminated;
    private volatile Thread schedThread;
    
    private NioCoChannel<?>[] chans;
    private final int maxConnections;
    private int maxChanSlot;
    private int freeChanSlot = -1;
    
    private NioCoTimer[] timers;
    private int maxTimerSlot;
    private int freeTimerSlot = -1;
    
    protected final Selector selector;
    protected final AtomicBoolean wakeup;
    
    protected final NioCoScheduler[] childs;
    private int nextChildSlot;
    final BlockingQueue<Runnable> syncQueue;
    
    public NioCoScheduler(){
        this(NAME, INIT_CONNECTIONS);
    }
    
    public NioCoScheduler(String name){
        this(name, INIT_CONNECTIONS);
    }
    
    public NioCoScheduler(String name, int initConnections){
        this(name, initConnections, MAX_CONNECTIONS, CHILD_COUNT);
    }
    
    public NioCoScheduler(String name, int initConnections, int childCount){
        this(name, initConnections, MAX_CONNECTIONS, childCount);
    }
    
    public NioCoScheduler(String name, int initConnections, int maxConnections, int childCount){
        debug("%s - NioCoScheduler(): initConnections = %s, maxConnections = %s, childCount = %s",
                name, initConnections, maxConnections, childCount);
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
        if(childCount < 0){
            throw new IllegalArgumentException("childCount " + childCount);
        }
        
        this.name  = name;
        this.chans = new NioCoChannel<?>[initConnections];
        this.maxConnections = maxConnections;
        
        this.timers    = new NioCoTimer[1024];
        this.syncQueue = new LinkedBlockingQueue<>();
        this.childs    = new NioCoScheduler[childCount];
        
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
    public void start() {
        synchronized(this.startSync) {
            if(this.schedThread != null) {
                throw new IllegalStateException("Scheduler had started");
            }
            
            this.schedThread = new Thread(this.name) {
                @Override
                public void run() {
                    startAndServe();
                }
            };
            this.schedThread.start();
        }
    }
    
    @Override
    public void startAndServe() {
        final String name = this.name;
        
        synchronized(this.startSync) {
            if(this.schedThread != null) {
                if(!inScheduler() || inScheduler() && isStarted()) {
                    throw new IllegalStateException("Scheduler had started");
                }
            }
            this.started = true;
            this.schedThread = Thread.currentThread();
        }
        this.schedThread.setName(name);
        
        final NioCoScheduler[] childs = this.childs;
        final int minConns = this.chans.length;
        final int maxConnx = this.maxConnections;
        for(int i = 0; i < childs.length; ++i){
            // Child threads
            final String cname = String.format("%s-child-%s", name, i);
            final NioCoScheduler child = 
                    new NioCoScheduler(cname, minConns, maxConnx, 0);
            childs[i] = child;
            new Thread() {
                @Override
                public void run(){
                    child.startAndServe();
                }
            }.start();
        }
        
        serve();
    }
    

    @Override
    public void awaitTermination() throws InterruptedException {
        this.schedThread.join();
    }
    
    @Override
    public boolean awaitTermination(long millis) throws InterruptedException {
        this.schedThread.join(millis);
        return isTerminated();
    }
    
    @Override
    public CoSocket accept(Continuation co, CoServerSocket coServerSocket) {
        debug("accept() ->");
        co.suspend();
        final CoSocket coSocket = (CoSocket)co.getContext();
        debug("accept() <-");
        return coSocket;
    }
    
    @Override
    public void bind(final CoServerSocket coServerSocket, final SocketAddress endpoint, final int backlog)
        throws CoIOException {
        final NioCoScheduler self = this;
        self.execute(new Runnable(){
            @Override
            public void run() {
                final NioCoServerSocket serverSocket = (NioCoServerSocket)coServerSocket;
                final ServerSocketChannel ssChan = serverSocket.channel();
                boolean failed = true;
                try {
                    self.register(serverSocket);
                    ssChan.bind(endpoint, backlog);
                    ssChan.register(self.selector, SelectionKey.OP_ACCEPT, serverSocket);
                    final CoroutineRunner coRunner = serverSocket.coRunner();
                    coRunner.setContext(serverSocket);
                    coRunner.execute();
                    failed = false;
                } catch(final IOException e){
                    final CoroutineRunner coRunner = serverSocket.coRunner();
                    coRunner.setContext(e);
                    self.resume(serverSocket);
                } finally {
                    if(failed){
                        IoUtils.close(serverSocket);
                    }
                }
            }
        });
    }
    
    @Override
    public void connect(CoSocket coSocket, SocketAddress remote) throws IOException {
        connect(coSocket, remote, 0);
    }
    
    @Override
    public void connect(final CoSocket coSocket, final SocketAddress remote, final int timeout)
            throws IOException {
        final NioCoScheduler self = this;
        self.execute(new Runnable(){
            @Override
            public void run(){
                final NioCoSocket socket = (NioCoSocket)coSocket;
                boolean failed = true;
                try {
                    final SocketChannel chan = socket.channel();
                    self.register(socket);
                    chan.register(self.selector, SelectionKey.OP_CONNECT, coSocket);
                    chan.connect(remote);
                    socket.startConnectionTimer(timeout);
                    failed = false;
                } catch(final IOException e){
                    final CoroutineRunner coRunner = socket.coRunner();
                    coRunner.setContext(e);
                    self.resume(socket);
                } finally {
                    if(failed){
                        self.close(coSocket);
                    }
                }
            }
        });
    }
    
    public void schedule(final NioCoTimer timerTask) {
        final NioCoScheduler self = this;
        
        execute(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }
    
    @Override
    public void schedule(CoSocket coSocket, Runnable task, long delay) {
        schedule(coSocket, task, delay, 0L);
    }
    
    @Override
    public void schedule(CoSocket coSocket, Runnable task, long delay, long period) {
        final NioCoSocket socket = (NioCoSocket)coSocket;
        final NioCoTimer timer = new NioCoTimer(socket, task, delay, period);
        schedule(timer);
    }
    
    @Override
    public void execute(final Runnable task) throws CoIOException {
        if(this.inScheduler()) {
            task.run();
            return;
        }
        
        final boolean ok = this.syncQueue.offer(task);
        if(ok){
            if(this.wakeup.compareAndSet(false, true)){
                this.selector.wakeup();
            }
            return;
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
    public void close(CoChannel coChannel) {
        if(coChannel == null){
            return;
        }
        
        final NioCoChannel<?> chan = (NioCoChannel<?>)coChannel;
        if(chan.id() == -1){
            return;
        }
        
        execute(new Runnable() {
            @Override
            public void run() {
                final NioCoChannel<?> scChan = slotCoChannel(chan);
                if(scChan != null && scChan == chan) {
                    final int slot = chan.id();
                    recycleChanSlot(slot);
                    debug("Close: %s", scChan);
                }
            }
        });
    }
    
    @Override
    public void shutdown() {
        this.shutdown = true;
        final Selector sel = this.selector;
        if(sel != null){
            sel.wakeup();
        }
        
        for(final NioCoScheduler child: this.childs){
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
        final Thread schedThread = this.schedThread;
        return (schedThread == Thread.currentThread());
    }
    
    NioCoScheduler register(final NioCoChannel<?> coChannel){
        final int slot = nextChanSlot();
        coChannel.id(slot);
        
        final NioCoChannel<?> oldChan = this.chans[slot];
        if(oldChan != null && oldChan.isOpen()){
            throw new IllegalStateException(String.format("Channel slot %s used", slot));
        }
        
        this.chans[slot] = coChannel;
        return this;
    }
    
    <S extends Channel> NioCoChannel<?> slotCoChannel(final NioCoChannel<S> coChannel){
        final NioCoChannel<?> coChan = this.chans[coChannel.id()];
        if(coChan == null || coChan != coChannel){
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
            
        final NioCoChannel<?>[] chans = this.chans;
        final int n = chans.length;
        
        // quick allocate
        if(this.maxChanSlot < n){
            return this.maxChanSlot++;
        }
        
        // traverse
        for(int i = 0; i < n; ++i){
            final NioCoChannel<?> coChan = chans[i];
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
        final NioCoChannel<?>[] newChans = new NioCoChannel<?>[size];
        System.arraycopy(chans, 0, newChans, 0, n);
        this.chans = newChans;
        return this.maxChanSlot++;
    }
    
    private void recycleChanSlot(final int slot){
        this.chans[slot] = null;
        this.freeChanSlot= slot;
        if(slot == this.maxChanSlot - 1){
            this.maxChanSlot--;
            this.freeChanSlot = -1;
            for(int i = slot - 1; i >= 0; --i){
                final NioCoChannel<?> c = this.chans[i];
                if(c != null && c.isOpen()){
                    break;
                }
                this.chans[i] = null;
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
                final int sels = doSelect();
                if(sels == 0) {
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
                
            } catch (final Throwable uncaught){
                this.terminate();
                debug("Scheduler fatal", uncaught);
                break;
            }
        }
    }
    
    private int doSelect() throws IOException {
        final Selector selector = this.selector;
        
        final long minRunat = minRunat();
        //debug("minRunat = %s, maxTimerSlot = %s", minRunat, this.maxTimerSlot);
        
        // Has new runner-or-timer?
        final boolean wakeuped = this.wakeup.compareAndSet(true, false);
        if(wakeuped || (minRunat == 0L && this.maxTimerSlot > 0)) {
            debug("selectNow()");
            return selector.selectNow();
        }
        
        return selector.select(minRunat);
    }
    
    private boolean tryShutdown() {
        if(!isShutdown()){
            return false;
        }
        
        final NioCoChannel<?>[] chans = this.chans;
        int actives = 0;
        for(int i = 0, n = chans.length; i < n; ++i){
            final NioCoChannel<?> runChan = chans[i];
            if(runChan == null){
                continue;
            }
            final Channel chan = runChan.channel();
            if(chan instanceof ServerSocketChannel){
                this.close(runChan);
                continue;
            }
            ++actives;
        }
        debug("tryShutdown(): actives %s", actives);
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
        final BlockingQueue<Runnable> queue = this.syncQueue;
        final long deadNano = System.nanoTime() + runNanos;
        for(;;){
            if(runNanos > 0L && System.nanoTime() >= deadNano) {
                return;
            }
            
            try {
                final Runnable runner = queue.poll();
                if(runner == null){
                    return;
                }
                
                runner.run();
            } catch (final CoroutineException e){
                final Throwable cause = e.getCause();
                debug("Uncaught exception in coroutine", cause);
            }
        }
    }

    protected static void debug(final String message, final Throwable cause){
        if(DEBUG){
            if(cause != null){
                debug(System.err, message);
                cause.printStackTrace(System.err);
            }else{
                debug(System.err, message);
            }
        }
    }
    
    protected static void debug(final String format, Object ...args){
        debug(System.out, format, args);
    }
    
    protected static void debug(final PrintStream out, String format, Object ...args){
        if(DEBUG){
            final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            String message = format;
            if(args.length > 0){
                message = String.format(format, args);
            }
            final String thread = Thread.currentThread().getName();
            out.println(String.format("%s[%s] %s", df.format(new Date()), thread, message));
        }
    }
    
    protected void terminate(){
        debug("Nio coScheduler: terminate()");
        IoUtils.close(this.selector);
        this.shutdown();
        this.chans   = null;
        this.timers  = null;
        this.terminated = true;
    }
    
    protected void resume(final NioCoChannel<?> coChannel) {
        try {
            if(coChannel.coRunner().execute() == false){
                IoUtils.close(coChannel);
            }
            return;
        } catch (final CoroutineException coe) {
            debug("Coroutine executes error", coe);
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
            
            socket.cancelConnetionTimer();
            final CoroutineRunner coRunner = coChan.coRunner();
            try {
                chan.finishConnect();
                debug("doConnect(): %s", chan);
                IoUtils.disableOps(SelectionKey.OP_CONNECT, key, this.selector, socket);
                failed = false;
            } catch (final IOException cause){
                coRunner.setContext(cause);
                resume(socket);
                return;
            }
            coRunner.setContext(socket);
            resume(socket);
        } finally {
            if(failed){
                IoUtils.close(socket);
            }
        }
    }
    
    protected void doAccept(final SelectionKey key) throws IOException {
        NioCoSocket coSocket = null;
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
                final Class<? extends Coroutine> connectorClass = cosSocket.getConnectorClass();
                if(this.childs.length != 0){
                    // 3-2. Post coSocket
                    postSocket(chan, connectorClass);
                    failed = false;
                    return;
                }
                final Coroutine coConnector = ReflectUtils.newObject(connectorClass);
                coSocket = new PassiveNioCoSocket(coConnector, chan, this);
            } catch (final CoIOException cause){
                debug("Create accepted socket error", cause);
                return;
            } finally {
                // 2. Next accept
                final CoroutineRunner coRunner = cosSocket.coRunner();
                coRunner.setContext(coSocket);
                coRunner.execute();
            }
            
            // 3-1. Start coSocket
            final CoroutineRunner coRunner = coSocket.coRunner();
            coRunner.setContext(coSocket);
            resume(coSocket);
            
            failed = false;
        } finally {
            if(failed){
                IoUtils.close(chan);
                this.close(coSocket);
            }
        }
    }
    
    private void postSocket(SocketChannel channel, Class<? extends Coroutine> connectorClass) {
        final NioCoScheduler[] childs = this.childs;
        final int childCount = childs.length;
        NioCoScheduler child = null;
        for(int i = 0; i < childCount; ++i){
            if(++this.nextChildSlot >= childCount){
                this.nextChildSlot = 0;
            }
            final NioCoScheduler c = childs[this.nextChildSlot];
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
        final boolean ok = child.syncQueue.offer(runner);
        if(ok){
            child.selector.wakeup();
            debug("postSocket()");
            return;
        }
        IoUtils.close(channel);
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
    
    private long minRunat() {
        final NioCoTimer[] timers  = this.timers;
        List<NioCoTimer> runTimers = null;
        
        final int n = Math.min(this.maxTimerSlot, timers.length);
        final long cur = System.currentTimeMillis();
        long min = Long.MAX_VALUE;
        boolean nomin = true;
        
        for(int i = 0; i < n; ++i){
            final NioCoTimer timer = timers[i];
            if(timer == null || timer.isCanceled()){
                timers[i] = null;
                this.freeTimerSlot = i;
                continue;
            }
            final long runat = timer.runat();
            if(runat <= cur){
                if(runTimers == null) {
                    runTimers = new ArrayList<>();
                    runTimers.add(timer);
                }else{
                    // Insertion sort
                    for(int j = runTimers.size(); j > 0; --j){
                        final NioCoTimer t = runTimers.get(j - 1);
                        if(runat < t.runat()){
                            continue;
                        }
                        runTimers.add(j, timer);
                        break;
                    }
                }
                continue;
            }
            if(runat < min){
                min = runat;
                nomin = false;
            }
        }
        
        // recycle timer slot
        recycleTimerSlots();
        
        // Execute timer
        if(runTimers != null) {
            for(int i = 0, size = runTimers.size(); i < size; ++i) {
                final NioCoTimer timer = runTimers.get(i);
                try {
                    timer.run();
                } catch (final Exception e){
                    timer.cancel();
                    debug("Timer runs error", e);
                }
            }
        }
        
        if(nomin){
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
        
        final NioCoScheduler scheduler;
        final SocketChannel channel;
        final Class<? extends Coroutine> connectorClass;

        PostSocketRunner(NioCoScheduler scheduler, 
                SocketChannel channel, Class<? extends Coroutine> connectorClass){
            this.scheduler = scheduler;
            this.channel   = channel;
            this.connectorClass = connectorClass;
        }
        
        @Override
        public void run() {
            final NioCoScheduler scheduler = this.scheduler;
            NioCoSocket coSocket = null;
            boolean failed = true;
            try {
                final Coroutine connector = ReflectUtils.newObject(connectorClass);
                coSocket = new PassiveNioCoSocket(connector, this.channel, scheduler);
                this.scheduler.register(coSocket);
                final CoroutineRunner coRunner = coSocket.coRunner();
                coRunner.setContext(coSocket);
                scheduler.resume(coSocket);
                failed = false;
            } finally {
                if(failed){
                    IoUtils.close(this.channel);
                    IoUtils.close(coSocket);
                }
            }
        }
        
    }
    
}
