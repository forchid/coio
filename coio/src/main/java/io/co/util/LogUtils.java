/*
 * Copyright (c) 2020, little-pan, All rights reserved.
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

package io.co.util;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class LogUtils {

    private LogUtils() {}

    public static final int DEBUG = 1, INFO = 2, WARN = 3, ERROR = 4;
    static final String TIME_FORMAT_DEFAULT = "yyyy-MM-dd HH:mm:ss.SSS";

    static final boolean debug = Boolean.getBoolean("io.co.debug");
    static final int LEVEL = Integer.getInteger("io.co.logLevel", debug? DEBUG: INFO);
    static final String ENCODING = System.getProperty("io.co.logCharset", "UTF-8");
    static final String TIME_FORMAT = System.getProperty("io.co.logTimeFormat", TIME_FORMAT_DEFAULT);

    static final PrintStream out, err;
    static {
        String file = System.getProperty("io.co.logFile");
        if (file != null) {
            try {
                out = new PrintStream(file, ENCODING);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            out = System.out;
        }

        boolean failed = true;
        try {
            file = System.getProperty("io.co.errorFile");
            if (file != null) {
                try {
                    err = new PrintStream(file, ENCODING);
                } catch (Exception e) {
                    throw new ExceptionInInitializerError(e);
                }
            } else {
                err = System.err;
            }
            failed = false;
        } finally {
            if (failed && out != System.out) IoUtils.close(out);
        }
    }

    public static void debug(final String format, Object ...args){
        log(DEBUG, format, args);
    }

    public static void debug(final String message, final Throwable cause){
        log(DEBUG, message, cause);
    }

    public static void debug(final PrintStream out, String format, Object ...args){
        log(DEBUG, out, format, args);
    }

    public static void info(final String format, Object ...args){
        log(INFO, format, args);
    }

    public static void info(final String message, final Throwable cause){
        log(INFO, message, cause);
    }

    public static void info(final PrintStream out, String format, Object ...args){
        log(INFO, out, format, args);
    }

    public static void warn(final String format, Object ...args){
        log(WARN, format, args);
    }

    public static void warn(final String message, final Throwable cause){
        log(WARN, message, cause);
    }

    public static void warn(final PrintStream out, String format, Object ...args){
        log(WARN, out, format, args);
    }

    public static void error(final String format, Object ...args){
        log(ERROR, format, args);
    }

    public static void error(final String message, final Throwable cause){
        log(ERROR, message, cause);
    }

    public static void error(final PrintStream out, String format, Object ...args){
        log(ERROR, out, format, args);
    }

    public static void log(int level, final String format, Object ...args) {
        PrintStream logger = level >= ERROR? err: out;
        log(level, logger, format, args);
    }

    public static void log(int level, final String message, final Throwable cause){
        if (level >= LEVEL) {
            PrintStream logger = level >= ERROR? err: out;
            if (cause != null) {
                log(level, logger, message, cause);
            }else{
                log(level, logger, message);
            }
        }
    }

    public static void log(int level, final PrintStream out, String format, Object ...args){
        if (level >= LEVEL) {
            final DateFormat df = new SimpleDateFormat(TIME_FORMAT);
            String message = format;
            int argc = args.length;
            Throwable cause = null;
            if(argc > 0){
                if (argc == 1 && args[0] instanceof Throwable) {
                    cause = (Throwable)args[0];
                } else {
                    message = String.format(format, args);
                }
            }
            final String levelName;
            switch (level) {
                case DEBUG:
                    levelName = "DEBUG";
                    break;
                case INFO:
                    levelName = "INFO";
                    break;
                case WARN:
                    levelName = "WARN";
                    break;
                case ERROR:
                    levelName = "ERROR";
                    break;
                default:
                    if (level < DEBUG) {
                        levelName = "DEBUG";
                    } else {
                        levelName = "ERROR";
                    }
                    break;
            }
            final String thread = Thread.currentThread().getName();
            final String time = df.format(new Date());
            String log = String.format("%s[%-5s][%s] %s", time, levelName, thread, message);
            if (cause == null) {
                out.println(log);
            } else {
                synchronized (out) {
                    out.println(log);
                    cause.printStackTrace(out);
                }
            }
        }
    }

    public static boolean isDebugEnabled() {
        return DEBUG >= LEVEL;
    }

    public static boolean isInfoEnabled() {
        return INFO >= LEVEL;
    }

    public static boolean isWarnEnabled() {
        return WARN >= LEVEL;
    }

    public static boolean isErrorEnabled() {
        return ERROR >= LEVEL;
    }

}
