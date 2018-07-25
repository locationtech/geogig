/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.File;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.Nullable;



/**
 * Provides an interface for a set of parameters keyed by a string value. Supports implementations
 * where a single key can be used multiple times.
 */
public interface ParameterSet {

    /**
     * Returns the value of the parameter with the given key.
     * 
     * @param key the key to search for
     * @return the value for the key
     * @throws InvalidArgumentException if the key was not found
     */
    public String getRequiredValue(String key);

    /**
     * Returns the value of the parameter with the given key, or {@code null} if the key wasn't
     * found. The first match will be returned.
     * 
     * @param key the key to search for
     * @return the value, or {@code null}
     */
    @Nullable
    public String getFirstValue(String key);

    /**
     * Returns the value of the parameter with the given key, or the default value if the key wasn't
     * found. The first match will be returned.
     * 
     * @param key the key to search for
     * @param defaultValue the value to return if the key is not found
     * @return the value, or {@code defaultValue} if the key was not found
     */
    public String getFirstValue(String key, String defaultValue);

    /**
     * Returns all values that match the specified key, or null if no values were found.
     * 
     * @param key the key to search for
     * @return an array of values, or {@code null}
     */
    @Nullable
    public String[] getValuesArray(String key);

    /**
     * Returns binary uploaded data as a File, or {@code null}.
     *
     * @return A File representation of uploaded binary data, or {@code null}.
     */
    @Nullable
    public File getUploadedFile();

    public static ParameterSet concat(final ParameterSet first, final ParameterSet second) {
        return new ParameterSet() {

            @Override
            public String getRequiredValue(String key) {
                String value = getFirstValue(key);
                if (value == null) {
                    throw new CommandSpecException(
                            String.format("Required parameter '%s' was not provided.", key));
                }
                return value;
            }

            @Override
            @Nullable
            public String getFirstValue(String key) {
                return getFirstValue(key, null);
            }

            @Override
            public String getFirstValue(String key, String defaultValue) {
                String value = first.getFirstValue(key);
                if (value == null) {
                    value = second.getFirstValue(key, defaultValue);
                }
                return value;
            }

            @Override
            @Nullable
            public String[] getValuesArray(String key) {
                String[] firstValues = first.getValuesArray(key);
                String[] secondValues = second.getValuesArray(key);
                return (String[]) ArrayUtils.addAll(firstValues, secondValues);
            }

            @Override
            @Nullable
            public File getUploadedFile() {
                File file = first.getUploadedFile();
                if (file == null) {
                    file = second.getUploadedFile();
                }
                return file;
            }
        };
    }

}
