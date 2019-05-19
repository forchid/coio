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

import io.co.CoTimerTask;
import io.co.util.IoUtils;

import java.net.SocketTimeoutException;

import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class NioConnectionTimer extends CoTimerTask {
    
    public NioConnectionTimer(NioCoSocket source, int timeout){
        super(source.getCoScheduler().nextTimerSlot(), source, timeout);
    }
    
    @Override
    public void run() {
        if(this.isCanceled()){
            return;
        }
        
        final NioCoScheduler scheduler = (NioCoScheduler)this.scheduler;
        final NioCoSocket source = (NioCoSocket)source();
        final CoroutineRunner coRunner = source.coRunner();
        
        coRunner.setContext(new SocketTimeoutException("Connect timeout"));
        scheduler.resume(source);
        IoUtils.close(source);
    }
    
}
