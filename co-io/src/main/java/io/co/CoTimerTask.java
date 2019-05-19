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
package io.co;

/**
 * A coroutine timer runnable.
 * 
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class CoTimerTask implements Runnable {
    
    public final int id;
    
    protected final CoSocket source;
    protected final CoScheduler scheduler;
    protected Runnable task;
    
    protected boolean canceled;
    
    protected long runat;
    protected final long period;
    
    public CoTimerTask(int id, CoSocket source, long delay){
        this(id, source, null, delay,  0L);
    }
    
    public CoTimerTask(int id, CoSocket source, Runnable task, long delay){
        this(id, source, task, delay, 0L);
    }
    
    public CoTimerTask(int id, CoSocket source, Runnable task, long delay, long period){
        this.id = id;
        this.task = task;
        this.scheduler = source.getCoScheduler();
        this.source = source;
        this.runat  = System.currentTimeMillis() + delay;
        this.period = period;
    }
    
    public CoSocket source(){
        return this.source;
    }
    
    public long runat(){
        return this.runat;
    }
    
    public long period(){
        return this.period;
    }
    
    public boolean next(){
        if(isCanceled() || this.period <= 0L){
            this.cancel();
            return false;
        }
        this.runat = System.currentTimeMillis() + this.period;
        return true;
    }
    
    public void cancel(){
        this.canceled = true;
    }
    
    public boolean isCanceled(){
        return this.canceled;
    }
    
    @Override
    public void run() {
        if(this.isCanceled() || this.task == null) {
            return;
        }
        
        this.task.run();
        next();
    }
    
}
