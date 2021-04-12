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

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.CoroutineException;
import com.offbynull.coroutines.user.CoroutineRunner;
import io.co.util.IoUtils;

import static io.co.util.LogUtils.debug;
import static io.co.util.LogUtils.error;

public class CoContext implements AutoCloseable, SchedulerProvider {

    private final CoroutineRunner runner;
    private final Scheduler scheduler;
    private int suspendTick, lastTick;

    private Object attachment;
    private AutoCloseable cleaner;

    public CoContext(CoroutineRunner runner, Scheduler scheduler)
            throws NullPointerException {
        this(runner, scheduler, null, null);
    }

    public CoContext(CoroutineRunner runner, Scheduler scheduler, Object attachment)
            throws NullPointerException {
        this(runner, scheduler, attachment, null);
    }

    public CoContext(CoroutineRunner runner, Scheduler scheduler, AutoCloseable cleaner)
            throws NullPointerException {
        this(runner, scheduler, null, cleaner);
    }

    public CoContext(CoroutineRunner runner, Scheduler scheduler, Object attachment,
                     AutoCloseable cleaner) throws NullPointerException {
        if (runner == null || scheduler == null) throw new NullPointerException();
        this.runner = runner;
        this.scheduler = scheduler;
        this.attachment = attachment;
        this.cleaner = cleaner;
    }

    public CoroutineRunner coRunner() {
        return this.runner;
    }

    @Override
    public Scheduler getScheduler() {
        return this.scheduler;
    }

    public Object attach(Object attachment) {
        Object old = this.attachment;
        this.attachment = attachment;
        return old;
    }

    public Object attach() {
        return this.attachment;
    }

    public Object detach() {
        Object attachment = this.attachment;
        this.attachment = null;
        return attachment;
    }

    public AutoCloseable cleaner(AutoCloseable cleaner) {
        AutoCloseable old = this.cleaner;
        this.cleaner = cleaner;
        return old;
    }

    public AutoCloseable cleaner() {
        return this.cleaner;
    }

    @Override
    public void close() {
        IoUtils.close(cleaner());
        this.cleaner = null;
    }

    @Override
    public String  toString() {
        AutoCloseable cleaner = this.cleaner;
        if (cleaner == null) {
            return "<null>";
        } else {
            return cleaner + "";
        }
    }

    public static void suspend(Continuation co) throws IllegalStateException {
        CoContext ctx = (CoContext)co.getContext();
        Scheduler scheduler = ctx.scheduler;

        scheduler.ensureInScheduler();
        if (ctx.lastTick != ctx.suspendTick/*once*/) {
            throw new IllegalStateException("Coroutine suspend state corrupted");
        }
        final int tick = ++ctx.suspendTick;
        co.suspend();
        ++ctx.lastTick;
        if (tick != ctx.suspendTick/*one*/ || ctx.lastTick != ctx.suspendTick/*once*/) {
            throw new IllegalStateException("Coroutine state corrupted after suspend");
        }
    }

    public static void resume(Continuation co) throws IllegalStateException {
        CoContext context = (CoContext)co.getContext();
        context.resume();
    }

    public void resume() throws IllegalStateException {
        this.scheduler.ensureInScheduler();
        if (this.lastTick + 1 != this.suspendTick/*once*/) {
            throw new IllegalStateException("Coroutine state corrupted when resuming");
        }

        try {
            CoroutineRunner coRunner = coRunner();
            if (!coRunner.execute()) {
                debug("Coroutine completed then close %s", this);
                IoUtils.close(this);
            }
        } catch (CoroutineException e) {
            error("Coroutine failed: " + this, e);
            IoUtils.close(this);
        }
    }

}
