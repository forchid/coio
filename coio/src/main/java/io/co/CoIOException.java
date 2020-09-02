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

import java.io.IOException;

/**
 * The coroutine IO exception, also a runtime exception.
 * 
 * @author little-pan
 * @since 2019-05-12
 *
 */
public class CoIOException extends RuntimeException {
    
    private static final long serialVersionUID = -6895725371130489332L;

    public CoIOException(String message) {
        super(message);
    }
    
    public CoIOException(IOException cause) {
        super(cause);
    }
    
    public CoIOException(String message, IOException cause) {
        super(message, cause);
    }

}
