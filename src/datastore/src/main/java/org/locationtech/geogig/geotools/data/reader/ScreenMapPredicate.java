/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.NodeRef;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Predicate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Filters out {@link Bounded} ({@link NodeRef node refs} and {@link Bucket buckets}) based on a
 * geotools {@link ScreenMap}
 *
 */
class ScreenMapPredicate implements Predicate<Bounded> {

    static final class Stats {
        private long skippedTrees, skippedBuckets, skippedFeatures;

        private long acceptedTrees, acceptedBuckets, acceptedFeatures;

        void add(final Bounded b, final boolean skip) {
            NodeRef n = b instanceof NodeRef ? (NodeRef) b : null;
            Bucket bucket = b instanceof Bucket ? (Bucket) b : null;
            if (skip) {
                if (bucket == null) {
                    if (n.getType() == TYPE.FEATURE) {
                        skippedFeatures++;
                    } else {
                        skippedTrees++;
                    }
                } else {
                    skippedBuckets++;
                }
            } else {
                if (bucket == null) {
                    if (n.getType() == TYPE.FEATURE) {
                        acceptedFeatures++;
                    } else {
                        acceptedTrees++;
                    }
                } else {
                    acceptedBuckets++;
                }
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "skipped/accepted: Features(%,d/%,d) Buckets(%,d/%,d) Trees(%,d/%,d)",
                    skippedFeatures, acceptedFeatures, skippedBuckets, acceptedBuckets,
                    skippedTrees, acceptedTrees);
        }
    }

    private ScreenMap screenMap;

    private Envelope envelope = new Envelope();

    private ScreenMapPredicate.Stats stats = new Stats();

    public ScreenMapPredicate(ScreenMap screenMap) {
        this.screenMap = screenMap;
    }

    public ScreenMapPredicate.Stats stats() {
        return stats;
    }

    @Override
    public boolean apply(@Nullable Bounded b) {
        if (b == null) {
            return false;
        }
        envelope.setToNull();
        b.expand(envelope);
        if (envelope.isNull()) {
            return true;
        }
        boolean skip;
        try {
            if (b instanceof NodeRef && ((NodeRef) b).getType() == TYPE.FEATURE) {
                skip = screenMap.checkAndSet(envelope);
            } else {
                skip = screenMap.get(envelope);
            }
        } catch (TransformException e) {
            e.printStackTrace();
            return true;
        }
        stats.add(b, skip);
        return !skip;
    }
}