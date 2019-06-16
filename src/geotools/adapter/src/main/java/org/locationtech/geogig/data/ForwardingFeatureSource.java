/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import java.awt.RenderingHints.Key;
import java.io.IOException;
import java.util.Set;

import org.geotools.data.DataAccess;
import org.geotools.data.FeatureListener;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.ResourceInfo;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;

public class ForwardingFeatureSource<T extends FeatureType, F extends Feature>
        implements FeatureSource<T, F> {

    private FeatureSource<T, F> source;

    public ForwardingFeatureSource(FeatureSource<T, F> source) {
        this.source = source;
    }

    public @Override Name getName() {
        return source.getName();
    }

    public @Override ResourceInfo getInfo() {
        return source.getInfo();
    }

    public @Override DataAccess<T, F> getDataStore() {
        return source.getDataStore();
    }

    public @Override QueryCapabilities getQueryCapabilities() {
        return source.getQueryCapabilities();
    }

    public @Override void addFeatureListener(FeatureListener listener) {
        source.addFeatureListener(listener);
    }

    public @Override void removeFeatureListener(FeatureListener listener) {
        source.removeFeatureListener(listener);
    }

    public @Override FeatureCollection<T, F> getFeatures(Filter filter) throws IOException {
        return source.getFeatures(filter);
    }

    public @Override FeatureCollection<T, F> getFeatures(Query query) throws IOException {
        return source.getFeatures(query);
    }

    public @Override FeatureCollection<T, F> getFeatures() throws IOException {
        return source.getFeatures();
    }

    public @Override T getSchema() {
        return source.getSchema();
    }

    public @Override ReferencedEnvelope getBounds() throws IOException {
        return source.getBounds();
    }

    public @Override ReferencedEnvelope getBounds(Query query) throws IOException {
        return source.getBounds(query);
    }

    public @Override int getCount(Query query) throws IOException {
        return source.getCount(query);
    }

    public @Override Set<Key> getSupportedHints() {
        return source.getSupportedHints();
    }

}
