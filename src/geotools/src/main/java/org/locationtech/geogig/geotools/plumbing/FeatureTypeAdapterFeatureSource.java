/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.AbstractReferenceSystem;
import org.locationtech.geogig.data.ForwardingFeatureCollection;
import org.locationtech.geogig.data.ForwardingFeatureIterator;
import org.locationtech.geogig.data.ForwardingFeatureSource;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.sort.SortBy;

class FeatureTypeAdapterFeatureSource<T extends FeatureType, F extends Feature>
        extends ForwardingFeatureSource<T, F> {

    private T featureType;

    private boolean forbidSorting;

    public FeatureTypeAdapterFeatureSource(final FeatureSource<T, F> source, final T featureType) {
        super(source);
        this.featureType = featureType;
    }

    @Override
    public T getSchema() {
        return featureType;
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
    public FeatureCollection<T, F> getFeatures(Query query) throws IOException {

        final FeatureCollection<T, F> features = super.getFeatures(query);
        return new ForwardingFeatureCollection<T, F>(features) {

            @Override
            public FeatureIterator<F> features() {
                if (((T)delegate.getSchema()).getDescriptors().size() != featureType.getDescriptors()
                        .size()) {
                    throw new GeoToolsOpException(
                            GeoToolsOpException.StatusCode.INCOMPATIBLE_FEATURE_TYPE);
                }

                GeometryDescriptor geomDescriptorOrg = ((T)delegate.getSchema()).getGeometryDescriptor();
                GeometryDescriptor geomDescriptorDest = featureType.getGeometryDescriptor();
                if (!geomDescriptorOrg.getType().getBinding()
                        .equals(geomDescriptorDest.getType().getBinding())) {
                    throw new GeoToolsOpException(
                            GeoToolsOpException.StatusCode.INCOMPATIBLE_FEATURE_TYPE);
                }

                AbstractReferenceSystem crsOrg = (AbstractReferenceSystem) ((T)delegate.getSchema())
                        .getCoordinateReferenceSystem();
                AbstractReferenceSystem crsDest = (AbstractReferenceSystem) featureType
                        .getCoordinateReferenceSystem();
                if (!crsOrg.equals(crsDest, false)) {
                    throw new GeoToolsOpException(
                            GeoToolsOpException.StatusCode.INCOMPATIBLE_FEATURE_TYPE);
                }

                FeatureIterator<F> iterator = delegate.features();
                SimpleFeatureBuilder builder = new SimpleFeatureBuilder(
                        (SimpleFeatureType) featureType);

                return new FeatureTypeConverterIterator<F>(iterator,
                        (SimpleFeatureBuilder) builder);
            }

            @Override
            public T getSchema() {
                return featureType;
            }
        };
    }

    private static class FeatureTypeConverterIterator<F extends Feature>
            extends ForwardingFeatureIterator<F> {

        private SimpleFeatureBuilder builder;

        public FeatureTypeConverterIterator(final FeatureIterator<F> iterator,
                SimpleFeatureBuilder builder) {
            super(iterator);
            checkNotNull(builder);
            this.builder = builder;
        }

        @SuppressWarnings("unchecked")
        @Override
        public F next() {
            F next = super.next();
            String fid = ((SimpleFeature) next).getID();
            Name geometryAttributeName = builder.getFeatureType().getGeometryDescriptor().getName();
            builder.set(geometryAttributeName, next.getDefaultGeometryProperty().getValue());
            for (AttributeDescriptor attribute : builder.getFeatureType()
                    .getAttributeDescriptors()) {
                Name name = attribute.getName();
                if (!name.equals(geometryAttributeName)) {
                    Property property = next.getProperty(name);
                    if (property == null) {
                        throw new GeoToolsOpException(
                                GeoToolsOpException.StatusCode.INCOMPATIBLE_FEATURE_TYPE);
                    }
                    builder.set(name, next.getProperty(name).getValue());
                }
            }
            return (F) builder.buildFeature(fid);
        }
    }

}
