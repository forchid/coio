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

public class CoContext {

    public final CoroutineRunner runner;
    private CoChannel channel;
    private Object attachment;

    public CoContext(CoroutineRunner runner) {
        this(runner, null);
    }

    public CoContext(CoroutineRunner runner, Object attachment) {
        if (runner == null) throw new NullPointerException();
        this.runner = runner;
        this.attachment = attachment;
    }

    public CoChannel channel() {
        return this.channel;
    }

    public CoChannel channel(CoChannel channel) {
        CoChannel old = this.channel;
        this.channel = channel;
        return old;
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

}
