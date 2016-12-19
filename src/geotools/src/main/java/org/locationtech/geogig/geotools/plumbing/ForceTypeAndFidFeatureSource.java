/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.feature.DecoratingFeature;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.identity.FeatureIdImpl;
import org.locationtech.geogig.data.ForwardingFeatureCollection;
import org.locationtech.geogig.data.ForwardingFeatureIterator;
import org.locationtech.geogig.data.ForwardingFeatureSource;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.sort.SortBy;

class ForceTypeAndFidFeatureSource<T extends FeatureType, F extends Feature>
        extends ForwardingFeatureSource<T, F> {

    private T forceType;

    private String fidPrefix;

    private boolean forbidSorting;

    public ForceTypeAndFidFeatureSource(final FeatureSource<T, F> source, final T forceType,
            final String fidPrefix) {

        super(source);
        this.forceType = forceType;
        this.fidPrefix = fidPrefix;
    }

    /**
     * @param forbidSorting flag for {@link #getQueryCapabilities()} to return false on
     *        isOffsetSupported() to work around malfunctioning geotools datastores from
     */
    public void setForbidSorting(boolean forbidSorting) {
        this.forbidSorting = forbidSorting;
    }

    @Override
    public QueryCapabilities getQueryCapabilities() {
        final QueryCapabilities capabilities = super.getQueryCapabilities();
        if (!forbidSorting) {
            return capabilities;
        }
        return new QueryCapabilities() {
            @Override
            public boolean isOffsetSupported() {
                return false;
            }

            @Override
            public boolean supportsSorting(SortBy[] sortAttributes) {
                return false;
            }

            @Override
            public boolean isReliableFIDSupported() {
                return capabilities.isReliableFIDSupported();
            }

            @Override
            public boolean isUseProvidedFIDSupported() {
                return capabilities.isUseProvidedFIDSupported();
            }
        };
    }

    @Override
    public T getSchema() {
        return forceType;
    }

    @Override
    public FeatureCollection<T, F> getFeatures(Query query) throws IOException {

        final FeatureCollection<T, F> features = super.getFeatures(query);
        return new ForwardingFeatureCollection<T, F>(features) {

            @Override
            public FeatureIterator<F> features() {

                FeatureIterator<F> iterator = delegate.features();

                return new FidPrefixRemovingIterator<F>(iterator, fidPrefix,
                        (SimpleFeatureType) forceType);
            }

            @Override
            public T getSchema() {
                return forceType;
            }
        };
    }

    private static class FidPrefixRemovingIterator<F extends Feature>
            extends ForwardingFeatureIterator<F> {

        private final String fidPrefix;

        private SimpleFeatureType forcedType;

        public FidPrefixRemovingIterator(final FeatureIterator<F> iterator, final String fidPrefix,
                SimpleFeatureType forcedType) {
            super(iterator);
            checkNotNull(fidPrefix);
            checkNotNull(forcedType);
            this.fidPrefix = fidPrefix;
            this.forcedType = forcedType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public F next() {
            F next = super.next();
            String fid = ((SimpleFeature) next).getID();
            if (fid.startsWith(fidPrefix)) {
                fid = fid.substring(fidPrefix.length());
            }
            return (F) new FidAndFtOverrideFeature((SimpleFeature) next, fid, forcedType);
        }
    }

    private static final class FidAndFtOverrideFeature extends DecoratingFeature {

        private String fid;

        private SimpleFeatureType featureType;

        public FidAndFtOverrideFeature(SimpleFeature delegate, String fid,
                SimpleFeatureType featureType) {
            super(delegate);
            this.fid = fid;
            this.featureType = featureType;
        }

        @Override
        public SimpleFeatureType getType() {
            return featureType;
        }

        @Override
        public String getID() {
            return fid;
        }

        @Override
        public FeatureId getIdentifier() {
            return new FeatureIdImpl(fid);
        }
    }
}
