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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.offbynull.coroutines.user.Continuation;

/**
 * The coroutine scheduler.
 * 
 * @author little-pan
 * @since 2019-05-13
 *
 */
public interface Scheduler extends Runnable, SchedulerProvider {
    
    int INIT_CONNECTIONS = Integer.getInteger("io.co.initConnections",10240);
    int MAX_CONNECTIONS  = Integer.getInteger("io.co.maxConnections", 102400);
    String NAME          = System.getProperty("io.co.scheduler.name", "CoScheduler");
    
    String getName();

    boolean isDaemon();
    
    boolean isStarted();
    
    void schedule(Runnable task, long delay) throws NullPointerException;
    
    void schedule(Runnable task, long delay, long period) throws NullPointerException;
    
    Future<?> execute(Runnable task) throws IllegalStateException;
    
    <V> Future<V> execute(Runnable task, V value) throws IllegalStateException;

    /** Compute the task in this scheduler executor, then wait for it to finish.
     *
     * @param co waiting coroutine
     * @param task computation task
     * @throws IllegalStateException
     *  if current thread not the scheduler thread, or the coroutine state error
     * @throws ExecutionException
     *  if computing failed
     */
    void compute(Continuation co, Runnable task)
            throws IllegalStateException, ExecutionException;

    /** Compute the task in this scheduler executor, then wait the result.
     *
     * @param co waiting coroutine
     * @param task computation task
     * @param <V> result type
     * @return computation result
     * @throws IllegalStateException
     *  if current thread not the scheduler thread, or the coroutine state error
     * @throws ExecutionException
     *  if computing failed
     */
    <V> V compute(Continuation co, Callable<V> task)
            throws IllegalStateException, ExecutionException;

    void compute(Continuation co, Runnable task, Executor executor)
            throws IllegalStateException, ExecutionException;

    <V> V compute(Continuation co, Callable<V> task, Executor executor)
            throws IllegalStateException, ExecutionException;
    
    void await(Continuation co, long millis);

    void attachCurrentThread() throws IllegalStateException;
    
    boolean inScheduler();

    default void ensureInScheduler() throws IllegalStateException {
        if (!inScheduler()) {
            String s = "The current thread not this scheduler thread";
            throw new IllegalStateException(s);
        }
    }
    
    void close(CoChannel channel);
    
    boolean isTerminated();
    
    boolean isShutdown();
    
    void awaitTermination() throws InterruptedException;
    
    boolean awaitTermination(long millis) throws InterruptedException;
    
    void shutdown();

}
