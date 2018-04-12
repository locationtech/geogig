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
import org.geotools.data.Query;
import org.geotools.factory.Hints;
import org.geotools.filter.expression.PropertyAccessor;
import org.geotools.filter.expression.PropertyAccessorFactory;
import org.geotools.util.Converters;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.filter.expression.PropertyName;

/**
 * A GeoTools {@link PropertyAccessorFactory} that knows how to extract feature property values out
 * of GeoGig materialized attributes in {@link Node}s coming from an index.
 * <p>
 * The GeoTools {@link PropertyName} expression implementation makes use of
 * {@link PropertyAccessor}s to evaluate an attribute value out of an object.
 * <p>
 * When using a fully materialized index (that's it, an index that contains all the extra attributes
 * necessary to satisfy a {@link Query}), a {@link MaterializedIndexFeatureIterator} is used to
 * avoid fetching {@link RevFeature}s from the {@link ObjectDatabase}, and this property accessor
 * implementation takes care of extracting attribute values from {@link Node#getExtraData() node
 * extra data}.
 *
 */
public class ExtraDataPropertyAccessorFactory implements PropertyAccessorFactory {

    static final ExtraDataPropertyAccesor EXTRA_DATA = new ExtraDataPropertyAccesor();

    @Override
    public PropertyAccessor createPropertyAccessor(Class<?> type, String xpath, Class<?> target,
            Hints hints) {
        return EXTRA_DATA;
    }

    static class ExtraDataPropertyAccesor implements PropertyAccessor {

        @Override
        public boolean canHandle(Object object, String xpath, Class<?> target) {
            return object instanceof Bounded;
        }

        @Override
        public <T> T get(Object object, String xpath, @Nullable Class<T> target)
                throws IllegalArgumentException {

            Bounded b = (Bounded) object;
            Object value = null;

            if (PrePostFilterSplitter.BOUNDS_META_PROPERTY.equals(xpath)) {
                value = b.bounds().orNull();
            } else {
                final Node node;
                if (b instanceof NodeRef) {
                    node = ((NodeRef) b).getNode();
                } else if (b instanceof Node) {
                    node = (Node) b;
                } else {
                    node = null;
                }
                if (node != null) {
                    if ("@id".equals(xpath)) {
                        value = node.getName();
                    } else {
                        value = IndexInfo.getMaterializedAttribute(xpath, node);
                    }
                }
                if (value != null && target != null && !target.isInstance(value)) {
                    value = Converters.convert(value, target);
                }
            }
            return (T) value;
        }

        @Override
        public <T> void set(Object object, String xpath, T value, Class<T> target)
                throws IllegalArgumentException {

            throw new UnsupportedOperationException();
        }

    }
}
