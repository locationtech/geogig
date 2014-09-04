/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import com.google.common.base.Objects;

public final class VersionedFormat {
    private final String version;

    private final String format;

    public VersionedFormat(String format, String version) {
        this.format = format;
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getFormat() {
        return format;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VersionedFormat) {
            VersionedFormat that = (VersionedFormat) o;
            return this.version.equals(that.version) && this.format.equals(that.format);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version, format);
    }

    @Override
    public String toString() {
        return format + ";v=" + version;
    }
}
