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

import io.co.CoScheduler;
import io.co.CoSocket;
import static io.co.nio.NioCoScheduler.*;

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
    
    protected final CoSocket source;
    protected final CoScheduler scheduler;
    protected Runnable task;
    
    protected boolean canceled;
    
    protected long runat;
    protected final long period;
    
    public NioCoTimer(NioCoSocket source, long delay){
        this(source, null, delay,  0L);
    }
    
    public NioCoTimer(NioCoSocket source, Runnable task, long delay){
        this(source, task, delay, 0L);
    }
    
    public NioCoTimer(NioCoSocket source, Runnable task, long delay, long period){
        this.task = task;
        this.scheduler = source.getScheduler();
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
    
    public boolean isCanceled(){
        return this.canceled;
    }
    
    public void cancel() {
        this.canceled = true;
        debug("Cancel: %s", this);
    }
    
    @Override
    public void run() {
        debug("Running: %s", this);
        if(this.isCanceled()) {
            return;
        }
        
        if(this.task == null){
            final NioCoSocket sock = (NioCoSocket)this.source();
            this.next();
            sock.getScheduler().resume(sock);
            return;
        }
        
        this.task.run();
        this.next();
    }
    
    public String toString() {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final String runat  = df.format(new Date(this.runat));
        return String.format("%s[id=%s#%s, source=%s, canceled=%s, runat=%s, period=%sms]", 
                getClass(), this.id, this.hashCode(), this.source, this.canceled, runat, this.period);
    }

}
