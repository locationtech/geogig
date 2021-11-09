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

import java.util.function.Predicate;

import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;

/**
 * Adapts a GeoTools {@link Filter} to a {@link Predicate} to be applied
 *
 */
final class PostFilter implements Predicate<SimpleFeature> {
    private Filter filter;

    public PostFilter(final Filter filter) {
        this.filter = filter;
    }

    public @Override boolean test(SimpleFeature feature) {
        return filter.evaluate(feature);
    }

    public static Predicate<SimpleFeature> forFilter(Filter postFilter) {
        return new PostFilter(postFilter);
    }
}