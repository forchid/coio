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

import com.offbynull.coroutines.user.Coroutine;
import io.co.CoStarter;
import io.co.Scheduler;

import java.util.concurrent.ExecutionException;

import static io.co.util.LogUtils.*;

public class SchedulerTest {

    public static void main(String[] args) {
        int times = Integer.getInteger("times", 1000000);
        SchedulerTest test = new SchedulerTest();
        test.testAwait(0, times);
        test.testAwait(1, 1000);
        test.testAwait(10, 100);
        test.testAwait(100, 10);
        test.testAwait(1000, 1);

        test.testCompute(1, false);
        test.testCompute(2, false);
        test.testCompute(10, false);
        test.testCompute(20, false);
        test.testCompute(1, true);
        test.testCompute(2, true);
        test.testCompute(10, true);
        test.testCompute(20, true);
    }

    void testAwait(int millis, int times) {
        long ta = System.currentTimeMillis();
        Scheduler scheduler = new NioScheduler();
        Coroutine co = c -> {
            long last = System.currentTimeMillis();
            for (int i = 0; i < times; ++i) {
                scheduler.await(c, millis);
                long curr = System.currentTimeMillis();
                if (curr - last >= 5000) {
                    last = curr;
                    debug("at %s", i);
                }
            }
            scheduler.shutdown();
        };
        CoStarter.start(co, scheduler);
        scheduler.run();
        long tb = System.currentTimeMillis();
        info("await(%sms) times %s, time %sms", millis, times, tb - ta);
    }

    void testCompute(int n, boolean testFail) {
        info("testCompute(): calc %s! when test-fail %s", n, testFail);
        Scheduler scheduler = new NioScheduler();
        Coroutine co = c -> {
            try {
                info("calc %s! ->", n);
                long m = scheduler.compute(c, () -> {
                    long f = 1;
                    for (int i = 1; i <= n; ++i) {
                        f *= i;
                        // Simulate thread blocking-or-busy operations
                        Thread.sleep(100);
                        if (testFail) {
                            throw new Exception("Test");
                        }
                    }
                    info("calc %s! <-", n);
                    return f;
                });
                if (testFail) throw new AssertionError();
                scheduler.compute(c, () -> info("%s! = %s", n, m));
                info("calc %s! OK", n);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!testFail|| !(cause instanceof Exception)) {
                    throw e;
                }
            } finally {
                scheduler.shutdown();
            }
        };
        CoStarter.start(co, scheduler);

        Coroutine timer = c -> {
            int i = 0;
            while (true) {
                info("Timer runs at %s", i++);
                scheduler.await(c, 1000);
            }
        };
        CoStarter.start(timer, scheduler);

        scheduler.run();
    }

}
