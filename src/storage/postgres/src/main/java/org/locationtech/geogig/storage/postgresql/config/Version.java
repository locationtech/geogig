/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Server version, given by it's major, minor, and patch numbers
 * 
 * @see PGStorage#getServerVersion
 * @since 1.2
 */
public class Version implements Comparable<Version> {

    public static final Version V9_4_0 = new Version(9, 4, 0);

    public static final Version V9_5_0 = new Version(9, 5, 0);

    public static final Version V9_6_0 = new Version(9, 6, 0);

    public static final Version V10_0_0 = new Version(10, 0, 0);

    public final int major;

    public final int minor;

    public final int patch;

    Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public @Override int compareTo(Version o) {
        int compare = Integer.compare(major, o.major);
        if (0 == compare) {
            compare = Integer.compare(minor, o.minor);
            if (0 == compare) {
                compare = Integer.compare(patch, o.patch);
            }
        }
        return compare;
    }

    public @Override boolean equals(Object o) {
        if (!(o instanceof Version)) {
            return false;
        }
        Version v = (Version) o;
        return major == v.major && minor == v.minor && patch == v.patch;
    }

    public @Override int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    public @Override String toString() {
        return String.format("%d.%d.%d", major, minor, patch);
    }

    public boolean lowerThan(Version v) {
        Preconditions.checkNotNull(v);
        return compareTo(v) < 0;
    }

    public boolean lowerOrEqualTo(Version v) {
        Preconditions.checkNotNull(v);
        return compareTo(v) <= 0;
    }

    public boolean greatherThan(Version v) {
        Preconditions.checkNotNull(v);
        return compareTo(v) > 0;
    }

    public boolean greatherOrEqualTo(Version v) {
        Preconditions.checkNotNull(v);
        return compareTo(v) >= 0;
    }

    public static Version valueOf(final String versionQueryResult) {
        // version string may not be a simple x.y.x value. Let's just take it from the
        // front and
        // stop at the first
        // non-digit, non-decimal-point character
        final String regex = "[^0-9.]";
        // replace first non-digit, non-decimal point with XXX
        String marked = versionQueryResult.replaceFirst(regex, "XXX");
        if (marked.contains("XXX")) {
            // no throw away everything starting with XXX
            marked = marked.substring(0, marked.indexOf("XXX"));
        }
        // (s) -> Integer.parseInt(s)
        Function<String, Integer> fn =  new Function<String, Integer>() {
            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }};

        final List<Integer> versions = Lists.transform(Splitter.on('.').splitToList(marked),
                fn);
        // version string can be either
        // {major}.{minor}.{patch}
        // or (since PostgreSQL 10)
        // {major}.{minor}
        Preconditions.checkState(versions.size() == 3 || versions.size() == 2,
                "Expected version format x.y.z or x.y, got " + versionQueryResult);
        int major = versions.get(0).intValue();
        int minor = versions.get(1).intValue();
        // patch may not be present. If not, just use 0
        int patch = (versions.size() == 3) ? versions.get(2).intValue() : 0;
        return new Version(major, minor, patch);
    }
}
