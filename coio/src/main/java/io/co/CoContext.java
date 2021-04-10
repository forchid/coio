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

import com.offbynull.coroutines.user.CoroutineRunner;
import io.co.util.IoUtils;

public class CoContext implements AutoCloseable {

    private final CoroutineRunner runner;
    private Object attachment;
    private AutoCloseable cleaner;

    public CoContext(CoroutineRunner runner) {
        this(runner, null, null);
    }

    public CoContext(CoroutineRunner runner, Object attachment) {
        this(runner, attachment, null);
    }

    public CoContext(CoroutineRunner runner, AutoCloseable cleaner) {
        this(runner, null, cleaner);
    }

    public CoContext(CoroutineRunner runner, Object attachment, AutoCloseable cleaner) {
        if (runner == null) throw new NullPointerException();
        this.runner = runner;
        this.attachment = attachment;
        this.cleaner = cleaner;
    }

    public CoroutineRunner coRunner() {
        return this.runner;
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

}
