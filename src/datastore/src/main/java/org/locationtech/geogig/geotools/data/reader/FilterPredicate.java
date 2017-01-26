package org.locationtech.geogig.geotools.data.reader;

import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;

import com.google.common.base.Predicate;

/**
 * Adapts a GeoTools {@link Filter} to a {@link Predicate} to be applied
 *
 */
final class FilterPredicate implements Predicate<SimpleFeature> {
    private Filter filter;

    public FilterPredicate(final Filter filter) {
        this.filter = filter;
    }

    @Override
    public boolean apply(SimpleFeature feature) {
        return filter.evaluate(feature);
    }
}