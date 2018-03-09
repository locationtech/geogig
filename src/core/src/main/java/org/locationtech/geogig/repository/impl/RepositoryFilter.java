/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Provides a filter for sparse repositories. A default filter can be applied to all feature types,
 * and specific filters can be applied to individual feature types.
 */
public class RepositoryFilter {

    private Map<String, Filter> repositoryFilters;

    private List<FilterDescription> filterDescriptions;

    /**
     * Provides a text description of a particular filter.
     */
    public class FilterDescription {
        private String featurePath;

        private String filterType;

        private String filter;

        /**
         * Constructs a new {@code FilterDescription} with the provided values.
         * 
         * @param featurePath the path of the features this filter applies to, use "default" as a
         *        fall back filter
         * @param filterType the type of filter, for example "CQL"
         * @param filter the filter text
         */
        public FilterDescription(String featurePath, String filterType, String filter) {
            this.featurePath = featurePath;
            this.filterType = filterType;
            this.filter = filter;
        }

        /**
         * @return the path of the features this filter applies to
         */
        public String getFeaturePath() {
            return featurePath;
        }

        /**
         * @return the format of the filter
         */
        public String getFilterType() {
            return filterType;
        }

        /**
         * @return the filter in string form
         */
        public String getFilter() {
            return filter;
        }
    }

    /**
     * Constructs a new {@code RepositoryFilter}.
     */
    public RepositoryFilter() {
        repositoryFilters = new HashMap<String, Filter>();
        filterDescriptions = new LinkedList<FilterDescription>();
    }

    /**
     * @return an immutable copy of the filter descriptions
     */
    public ImmutableList<FilterDescription> getFilterDescriptions() {
        return ImmutableList.copyOf(filterDescriptions);
    }

    /**
     * Adds a new filter to the repository.
     * 
     * @param featurePath the path of the features to filter, "default" for a fall back filter
     * @param filterType the format of the filter text, for example "CQL"
     * @param filterText the filter text
     */
    public void addFilter(String featurePath, String filterType, String filterText) {
        Preconditions.checkState(featurePath != null && filterType != null && filterText != null,
                "Missing filter parameter.");
        if (filterType.equals("CQL")) {
            try {
                Filter newFilter = CQL.toFilter(filterText);
                repositoryFilters.put(featurePath, newFilter);
                filterDescriptions.add(new FilterDescription(featurePath, filterType, filterText));
            } catch (CQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Determines if the provided object is filtered in this repository.
     * 
     * @param type the feature type
     * @param featurePath the path of the feature (without the feature ID)
     * @param object the object to filter
     * @return true if the object lies within the filter, false otherwise
     */
    public boolean filterObject(RevFeatureType type, String featurePath, RevObject object) {
        if (object.getType() == TYPE.FEATURE) {
            RevFeature revFeature = (RevFeature) object;
            FeatureBuilder builder = new FeatureBuilder(type);
            Feature feature = builder.build("TEMP_ID", revFeature);

            Filter typeFilter = repositoryFilters.get(featurePath);
            if (typeFilter == null) {
                typeFilter = repositoryFilters.get("default");
            }
            if (typeFilter == null || typeFilter.evaluate(feature)) {
                return true;
            }
        }
        return false;
    }
}
