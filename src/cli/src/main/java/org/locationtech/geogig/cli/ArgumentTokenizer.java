/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * Tokenizes a string with arguments, breaking in blank spaces except when they are quoted
 * 
 */
public class ArgumentTokenizer {

    public static String[] tokenize(String s) {
        Iterable<String> tokens = Splitter.on(new UnquotedSpace()).split(s);
        return Iterables.toArray(tokens, String.class);
    }

    private static class UnquotedSpace extends CharMatcher {

        private boolean inQuotes = false;

        @Override
        public boolean matches(char c) {
            if ('"' == c) {
                inQuotes = !inQuotes;
            }
            if (inQuotes) {
                return false;
            }
            return (' ' == c);
        }

    }

}
