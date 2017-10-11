/* Copyright (c) 2012-2017 Boundless and others.
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

import org.locationtech.geogig.repository.Remote;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;

/**
 * A summary changes performed to a repository after calling {@link FetchOp} or {@link PushOp}
 * grouped by each {@link Remote} the command acted upon.
 */
public class TransferSummary {

    private ArrayListMultimap<String, RefDiff> refDiffs = ArrayListMultimap.create();

    public Map<String, Collection<RefDiff>> getRefDiffs() {
        return refDiffs.asMap();
    }

    public void add(final String remoteURL, final RefDiff changeResult) {
        checkNotNull(remoteURL);
        checkNotNull(changeResult);
        refDiffs.put(remoteURL, changeResult);
    }

    public void addAll(final String remoteURL, final List<RefDiff> changes) {
        checkNotNull(remoteURL);
        checkNotNull(changes);
        for (RefDiff cr : changes) {
            checkNotNull(cr);
        }
        refDiffs.putAll(remoteURL, changes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(TransferSummary.class) //
                .addValue(refDiffs) //
                .toString();
    }

    public boolean isEmpty() {
        return refDiffs.isEmpty();
    }

}
