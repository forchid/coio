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

import java.nio.channels.Channel;

import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * A channel wrapper with coroutine runner.
 * 
 * @author little-pan
 * @since 2019-05-13日
 *
 */
class CoRunnerChannel {
    
    final CoroutineRunner coRunner;
    final Channel channel;
    
    CoRunnerChannel(Coroutine co, Channel channel){
        this.coRunner = new CoroutineRunner(co);
        this.channel  = channel;
    }

}
