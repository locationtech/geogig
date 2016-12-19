/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

/**
 * Associates a format name and version with a java class.
 * 
 * @since 1.0
 */
public final class VersionedFormat {
    private final String version;

    private final String format;

    private Class<?> clazz;

    /**
     * Construct a new {@code VersionedFormat} with the provided parameters.
     * 
     * @param format the format of the class
     * @param version the version of the format
     * @param clazz the class
     */
    public VersionedFormat(String format, String version, Class<?> clazz) {
        checkNotNull(format);
        checkNotNull(version);
        this.format = format;
        this.version = version;
        this.clazz = clazz;
    }

    /**
     * @return the version of the format
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the format name
     */
    public String getFormat() {
        return format;
    }

    /**
     * Equality checks based on {@code format} and {@code version}
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof VersionedFormat) {
            VersionedFormat that = (VersionedFormat) o;
            return this.version.equals(that.version) && this.format.equals(that.format);
        } else {
            return false;
        }
    }

    /**
     * Hash code based on {@code format} and {@code version}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(version, format);
    }

    /**
     * Combines {@code format} and {@code version} into a single string.
     */
    @Override
    public String toString() {
        return format + ";v=" + version;
    }

    /**
     * @return the class associated with this format
     */
    public Class<?> getImplementingClass() {
        return clazz;
    }
}
