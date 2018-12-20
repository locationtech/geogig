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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Predicate;

/**
 * Filters out {@link Bounded} ({@link NodeRef node refs} and {@link Bucket buckets}) based on a
 * geotools {@link ScreenMap}
 *
 */
public class ScreenMapPredicate implements Predicate<Bounded> {

    public static class Stats {
        void add(final Bounded b, final boolean skip) {
        }

        public @Override String toString() {
            return "No Stats";
        }
    }

    static final class StatsCollector extends Stats {
        public final AtomicLong skippedTrees = new AtomicLong(), skippedBuckets = new AtomicLong(),
                skippedFeatures = new AtomicLong();

        public final AtomicLong acceptedTrees = new AtomicLong(),
                acceptedBuckets = new AtomicLong(), acceptedFeatures = new AtomicLong();

        protected @Override void add(final Bounded b, final boolean skip) {
            NodeRef n = b instanceof NodeRef ? (NodeRef) b : null;
            Bucket bucket = b instanceof Bucket ? (Bucket) b : null;
            if (skip) {
                if (bucket == null) {
                    if (n.getType() == TYPE.FEATURE) {
                        skippedFeatures.incrementAndGet();
                    } else {
                        skippedTrees.incrementAndGet();
                    }
                } else {
                    skippedBuckets.incrementAndGet();
                }
            } else {
                if (bucket == null) {
                    if (n.getType() == TYPE.FEATURE) {
                        acceptedFeatures.incrementAndGet();
                    } else {
                        acceptedTrees.incrementAndGet();
                    }
                } else {
                    acceptedBuckets.incrementAndGet();
                }
            }
        }

        public @Override String toString() {
            return String.format(
                    "skipped/accepted: Features(%,d/%,d) Buckets(%,d/%,d) Trees(%,d/%,d)",
                    skippedFeatures.longValue(), acceptedFeatures.longValue(),
                    skippedBuckets.longValue(), acceptedBuckets.longValue(),
                    skippedTrees.longValue(), acceptedTrees.longValue());
        }
    }

    private Lock lock = new ReentrantLock();

    private Supplier<Envelope> envelope = Envelope::new;

    private ScreenMap screenMap;

    private ScreenMapPredicate.Stats stats = new Stats();

    private boolean filterTrees = false;

    public ScreenMapPredicate(ScreenMap screenMap) {
        this.screenMap = screenMap;
    }

    public ScreenMapPredicate collectStats() {
        this.stats = new StatsCollector();
        return this;
    }

    public ScreenMapPredicate filterTrees() {
        this.filterTrees = true;
        return this;
    }

    public ScreenMapPredicate optimizeForSingleThreadedCalls() {
        this.lock = new NoOpLock();
        final Envelope reuse = new Envelope();
        this.envelope = () -> {
            reuse.init();
            return reuse;
        };
        return this;
    }

    public ScreenMapPredicate.Stats stats() {
        return stats;
    }

    @Override
    public boolean apply(@Nullable Bounded b) {
        if (b == null) {
            return false;
        }
        Envelope envelope = this.envelope.get();
        b.expand(envelope);
        if (envelope.isNull()) {
            return true;
        }

        boolean skip = false;
        // canSimplify is thread-safe
        if (screenMap.canSimplify(envelope)) {
            // these aren't thread safe
            lock.lock();
            try {
                if (filterTrees
                        || b instanceof NodeRef && ((NodeRef) b).getType() == TYPE.FEATURE) {
                    skip = screenMap.checkAndSet(envelope);
                } else {
                    skip = screenMap.get(envelope);
                }
            } catch (TransformException e) {
                e.printStackTrace();
                return true;
            } finally {
                lock.unlock();
            }
        }

        stats.add(b, skip);
        return !skip;
    }

    private static class NoOpLock implements Lock {
        //@formatter:off
        public @Override void lock() {}
        public @Override void lockInterruptibly() {}
        public @Override boolean tryLock() {return true;}
        public @Override boolean tryLock(long time, TimeUnit unit) {return true;}
        public @Override void unlock() {}
        public @Override Condition newCondition() {throw new UnsupportedOperationException();}
        //@formatter:on
    }
}