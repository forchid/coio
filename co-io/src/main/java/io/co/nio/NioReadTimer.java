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

import io.co.TimeRunner;

/**
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class NioReadTimer extends TimeRunner {
    
    boolean timeout;
    
    public NioReadTimer(NioCoScheduler scheduler, NioCoSocket source, long runat){
        super(scheduler.nextTimerSlot(), scheduler, source, runat);
    }
    
    @Override
    public void run() {
        if(this.isCanceled()){
            return;
        }
        
        final NioCoScheduler scheduler = (NioCoScheduler)this.scheduler;
        final NioCoSocket source = (NioCoSocket)source();
        final CoRunnerChannel corChan = scheduler.runnerChannel(source);
        if(corChan == null){
            this.cancel();
            return;
        }
        
        this.timeout = true;
        scheduler.execute(corChan.coRunner, source);
    }
    
}
