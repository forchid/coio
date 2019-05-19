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

/**
 * @author little-pan
 * @since 2019-05-19
 *
 */
public class DefaultNioCoTimer extends CoTimerTask {
    
    public DefaultNioCoTimer(NioCoSocket source, long delay) {
        this(source, null, delay, 0L);
    }
    
    public DefaultNioCoTimer(NioCoSocket source, Runnable task, long delay) {
        this(source, task, delay, 0L);
    }

    
    public DefaultNioCoTimer(NioCoSocket source, Runnable task, long delay, long period) {
        super(source.getCoScheduler().nextTimerSlot(), source, task, delay, period);
    }
    
    @Override
    public void run() {
        if(this.isCanceled()) {
            return;
        }
        
        try {
            if(this.task == null){
                final NioCoSocket sock = (NioCoSocket)this.source();
                sock.getCoScheduler().resume(sock);
                return;
            }
            
            this.task.run();
        } finally {
            next();
        }
    }

}
