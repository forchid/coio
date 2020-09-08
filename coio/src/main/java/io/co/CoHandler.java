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
import com.offbynull.coroutines.user.Coroutine;
import static io.co.util.LogUtils.*;

/** A coroutine that dispatches exception and normal process.
 *
 * @author little-pan
 * @since 2020-09-02
 */
public interface CoHandler extends Coroutine {

    @Override
    default void run(Continuation co) throws Exception {
        Object context = co.getContext();

        if (context instanceof Throwable) {
            Throwable cause = (Throwable)context;
            exceptionCaught(cause);
        } else {
            try {
                handle(co);
            } catch (Throwable cause) {
                co.setContext(cause);
                exceptionCaught(cause);
            }
        }
    }

    default void exceptionCaught(Throwable cause) {
        error("Uncaught exception: ", cause);
    }

    void handle(Continuation co) throws Exception;

}
