/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.geotools.data.DataSourceException;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.memory.MemoryFeatureStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.ContentState;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * GeoTools' MemoryDataStore does not support {@code Hints.USE_PROVIDED_FID} at the time of writing,
 * hence this subclass decorates it to support it.
 *
 */
public class MemoryDataStoreWithProvidedFIDSupport extends MemoryDataStore {

    @Override
    protected ContentFeatureSource createFeatureSource(ContentEntry entry, Query query) {

        return new MemoryFeatureStore(entry, query) {
            @Override
            protected QueryCapabilities buildQueryCapabilities() {
                return new QueryCapabilities() {
                    @Override
                    public boolean isUseProvidedFIDSupported() {
                        return true;
                    }
                };
            }

            @Override
            protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(Query query,
                    int flags) throws IOException {
                return new MemoryFeatureWriterWithProvidedFIDSupport(getState(), query);
            }

        };

    }

    @Override
    public void dispose() {
        // don't dispose the store, it will wipe the in-memory contents
    }


    private static class MemoryFeatureWriterWithProvidedFIDSupport
            implements FeatureWriter<SimpleFeatureType, SimpleFeature> {
        @SuppressWarnings("unused")
        ContentState state;

        SimpleFeatureType featureType;

        Map<String, SimpleFeature> contents;

        Iterator<SimpleFeature> iterator;

        SimpleFeature live = null;

        SimpleFeature current = null; // current Feature returned to user

        public MemoryFeatureWriterWithProvidedFIDSupport(ContentState state, Query query)
                throws IOException {
            this.state = state;
            featureType = state.getFeatureType();
            String typeName = featureType.getTypeName();
            MemoryDataStoreWithProvidedFIDSupport store = (MemoryDataStoreWithProvidedFIDSupport) state
                    .getEntry().getDataStore();
            contents = store.entry(typeName).getMemory();
            iterator = contents.values().iterator();

        }

        public SimpleFeatureType getFeatureType() {
            return featureType;
        }

        public SimpleFeature next() throws IOException, NoSuchElementException {
            if (hasNext()) {
                // existing content
                live = iterator.next();

                try {
                    current = SimpleFeatureBuilder.copy(live);
                } catch (IllegalAttributeException e) {
                    throw new DataSourceException(
                            "Unable to edit " + live.getID() + " of " + featureType.getTypeName());
                }
            } else {
                // new content
                live = null;

                try {
                    current = SimpleFeatureBuilder.template(featureType, null);
                } catch (IllegalAttributeException e) {
                    throw new DataSourceException(
                            "Unable to add additional Features of " + featureType.getTypeName());
                }
            }

            return current;
        }

        public void remove() throws IOException {
            if (contents == null) {
                throw new IOException("FeatureWriter has been closed");
            }

            if (current == null) {
                throw new IOException("No feature available to remove");
            }

            if (live != null) {
                // remove existing content
                iterator.remove();
                live = null;
                current = null;
            } else {
                // cancel add new content
                current = null;
            }
        }

        public void write() throws IOException {
            if (contents == null) {
                throw new IOException("FeatureWriter has been closed");
            }

            if (current == null) {
                throw new IOException("No feature available to write");
            }

            if (live != null) {
                if (live.equals(current)) {
                    // no modifications made to current
                    //
                    live = null;
                    current = null;
                } else {
                    // accept modifications
                    //
                    try {
                        live.setAttributes(current.getAttributes());
                    } catch (Exception e) {
                        throw new DataSourceException("Unable to accept modifications to "
                                + live.getID() + " on " + featureType.getTypeName());
                    }

                    ReferencedEnvelope bounds = new ReferencedEnvelope();
                    bounds.expandToInclude(new ReferencedEnvelope(live.getBounds()));
                    bounds.expandToInclude(new ReferencedEnvelope(current.getBounds()));
                    live = null;
                    current = null;
                }
            } else {
                // add new content
                String fid = current.getID();
                if (Boolean.TRUE.equals(current.getUserData().get(Hints.USE_PROVIDED_FID))) {
                    if (current.getUserData().containsKey(Hints.PROVIDED_FID)) {
                        fid = (String) current.getUserData().get(Hints.PROVIDED_FID);
                        Map<Object, Object> userData = current.getUserData();
                        current = SimpleFeatureBuilder.build(current.getFeatureType(),
                                current.getAttributes(), fid);
                        current.getUserData().putAll(userData);
                    }
                }
                contents.put(fid, current);
                current = null;
            }
        }

        public boolean hasNext() throws IOException {
            if (contents == null) {
                throw new IOException("FeatureWriter has been closed");
            }

            return (iterator != null) && iterator.hasNext();
        }

        public void close() {
            if (iterator != null) {
                iterator = null;
            }

            if (featureType != null) {
                featureType = null;
            }

            contents = null;
            current = null;
            live = null;
        }
    }

}
