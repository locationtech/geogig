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

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

public @UtilityClass class Strings {

    public static boolean isNullOrEmpty(CharSequence string) {
        return null == string || string.length() == 0;
    }

    public static String nullToEmpty(CharSequence string) {
        return null == string ? "" : string.toString();
    }

    public static String padStart(@NonNull CharSequence string, int minLength, char padChar) {
        if (string.length() >= minLength)
            return string.toString();

        String s = String.valueOf(padChar);
        String padding = IntStream.range(0, minLength - string.length()).mapToObj(i -> s)
                .collect(Collectors.joining());
        return padding + string;
    }

    public static String padEnd(@NonNull CharSequence string, int minLength, char padChar) {
        if (string.length() >= minLength)
            return string.toString();

        String s = String.valueOf(padChar);
        String padding = IntStream.range(0, minLength - string.length()).mapToObj(i -> s)
                .collect(Collectors.joining());
        return string + padding;
    }

    public static String repeat(CharSequence string, int times) {
        return IntStream.range(0, times).mapToObj(i -> string).collect(Collectors.joining());
    }
}
