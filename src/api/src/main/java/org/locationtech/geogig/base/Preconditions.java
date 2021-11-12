/* Copyright (c) 2021 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.base;

import java.util.Arrays;
import java.util.IllegalFormatException;

import lombok.experimental.UtilityClass;

public @UtilityClass class Preconditions {

    public static void checkArgument(boolean b) {
        if (!b) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(boolean b, String msgFormat, Object... msgargs) {
        if (!b) {
            throw new IllegalArgumentException(format(msgFormat, msgargs));
        }
    }

    public static void checkState(boolean b) {
        if (!b) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(boolean b, String msgFormat, Object... msgargs) {
        if (!b) {
            throw new IllegalStateException(format(msgFormat, msgargs));
        }
    }

    private static String format(String msgFormat, Object... msgargs) {
        try {
            return String.format(msgFormat, msgargs);
        } catch (IllegalFormatException e) {
            String msg = "Error formatting string '" + msgFormat + "' with args "
                    + Arrays.toString(msgargs);
            throw new Error(msg, e);
        }
    }
}
