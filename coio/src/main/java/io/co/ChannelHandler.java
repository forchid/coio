/*
 * Copyright (c) 2020, little-pan, All rights reserved.
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

/** A coroutine that handles channel.
 *
 * @author little-pan
 * @since 2020-09-02
 */
public interface ChannelHandler<T extends CoChannel> extends CoHandler {

    @Override
    @SuppressWarnings("unchecked")
    default void handle(Continuation co) throws Exception {
        Object context = co.getContext();
        T channel = (T) context;
        handle(co, channel);
    }

    void handle(Continuation co, T channel) throws Exception;

}
