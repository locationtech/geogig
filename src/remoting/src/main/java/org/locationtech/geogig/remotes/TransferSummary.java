/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;

/**
 *
 */
public class TransferSummary {

    private ArrayListMultimap<String, RefDiff> RefDiffs = ArrayListMultimap.create();

    public Map<String, Collection<RefDiff>> getRefDiffs() {
        return RefDiffs.asMap();
    }

    public void add(final String remoteURL, final RefDiff changeResult) {
        checkNotNull(remoteURL);
        checkNotNull(changeResult);
        RefDiffs.put(remoteURL, changeResult);
    }

    public void addAll(final String remoteURL, final List<RefDiff> changes) {
        checkNotNull(remoteURL);
        checkNotNull(changes);
        for (RefDiff cr : changes) {
            checkNotNull(cr);
        }
        RefDiffs.putAll(remoteURL, changes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(TransferSummary.class) //
                .addValue(RefDiffs) //
                .toString();
    }

    public boolean isEmpty() {
        return RefDiffs.isEmpty();
    }

}
