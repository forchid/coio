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
package io.co.nio;

import io.co.CoChannel;
import io.co.CoContext;

import static io.co.util.LogUtils.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author little-pan
 * @since 2019-05-19
 *
 */
public class NioCoTimer implements Runnable {
    
    int id = -1;
    
    protected final NioScheduler scheduler;
    protected final CoContext context;
    protected Runnable task;
    
    protected boolean canceled;
    
    protected long runat;
    protected final long period;
    
    public NioCoTimer(CoContext context, NioScheduler scheduler, long delay){
        this(context, scheduler, null, delay,  0L);
    }
    
    public NioCoTimer(CoContext context, NioScheduler scheduler,
                      Runnable task, long delay) {
        this(context, scheduler, task, delay, 0L);
    }

    public NioCoTimer(NioScheduler scheduler, Runnable task, long delay, long period) {
        this(null, scheduler, task, delay, period);
    }
    
    public NioCoTimer(CoContext context, NioScheduler scheduler,
                      Runnable task, long delay, long period) {
        if (context == null && task == null) {
            throw new NullPointerException();
        }
        this.scheduler = scheduler;
        this.context = context;
        this.task    = task;
        this.runat   = System.currentTimeMillis() + delay;
        this.period  = period;
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
    
    public boolean isCanceled() {
        if (this.canceled) {
            return true;
        }

        if (this.context != null) {
            AutoCloseable cleaner = this.context.cleaner();
            if (cleaner instanceof CoChannel) {
                CoChannel ch = (CoChannel) cleaner;
                return (!ch.isOpen());
            }
        }

        return false;
    }
    
    public void cancel() {
        if (this.isCanceled()) {
            return;
        }
        
        this.scheduler.cancel(this);
        this.canceled = true;
        
        debug("Cancel: %s", this);
    }
    
    @Override
    public void run() {
        debug("Running: %s", this);
        if(this.isCanceled()) {
            return;
        }
        
        if (this.task == null) {
            CoContext context = this.context;
            this.next();
            this.scheduler.resume(context);
            return;
        }
        
        this.task.run();
        this.next();
    }
    
    public String toString() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String runat  = df.format(new Date(this.runat));
        return String.format("%s[id=%s#%s, context=%s, canceled=%s, runat=%s, period=%sms]",
                getClass(), this.id, this.hashCode(), this.context, this.canceled, runat, this.period);
    }

}
