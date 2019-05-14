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
 * A timing runnable.
 * 
 * @author little-pan
 * @since 2019-05-14
 *
 */
public class TimeRunner implements Runnable {
    
    public final int id;
    
    protected final CoSocket source;
    protected final CoScheduler scheduler;
    protected Runnable task;
    
    protected boolean canceled;
    
    protected long runat;
    protected final long period;
    
    public TimeRunner(int id, CoScheduler scheduler, CoSocket source, long runat){
        this(id, null, scheduler, source, runat, 0L);
    }
    
    public TimeRunner(int id, Runnable task, CoScheduler scheduler, CoSocket source, long runat){
        this(id, task, scheduler, source, runat, 0L);
    }
    
    public TimeRunner(int id, Runnable task, CoScheduler scheduler, CoSocket source, long runat, long period){
        this.id = id;
        this.task = task;
        this.scheduler = scheduler;
        this.source = source;
        this.runat  = runat;
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
        if(this.task == null || this.isCanceled()){
            return;
        }
        this.task.run();
    }
    
}
