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

import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/** The starter of coroutine, sets current coroutine runner as
 * continuation context.
 */
public class CoStarter {

    protected final CoContext context;

    public CoStarter(Coroutine c) {
        this(c, null);
    }

    public CoStarter(Coroutine c, AutoCloseable cleaner) {
        CoroutineRunner runner = new CoroutineRunner(c);
        this.context = new CoContext(runner, cleaner);
        runner.setContext(this.context);
    }

    public void start() {
        this.context.coRunner().execute();
    }

    public static CoStarter start(Coroutine c) {
        CoStarter starter = new CoStarter(c);
        starter.start();
        return starter;
    }

    public static CoStarter start(Coroutine c, AutoCloseable cleaner) {
        CoStarter starter = new CoStarter(c, cleaner);
        starter.start();
        return starter;
    }

}
