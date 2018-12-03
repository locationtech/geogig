/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Defines the differences between 2 versions of the a given feature
 * 
 */
public class FeatureDiff {

    private Map<PropertyDescriptor, AttributeDiff> diffs;

    private String path;

    private RevFeatureType newFeatureType;

    private RevFeatureType oldFeatureType;

    private RevFeature newRevFeature;

    private RevFeature oldRevFeature;

    public FeatureDiff(String path, Map<PropertyDescriptor, AttributeDiff> diffs,
            RevFeatureType oldFeatureType, RevFeatureType newFeatureType) {
        this.path = path;
        this.diffs = Maps.newHashMap(diffs);
        this.newFeatureType = newFeatureType;
        this.oldFeatureType = oldFeatureType;
    }

    /**
     * 
     * @param path the full path to the feature, including its name
     * @param newRevFeature the new version of the feature
     * @param oldRevFeature the old version of the feature
     * @param newRevFeatureType the new version of the feature type
     * @param oldRevFeatureType the old version of the feature type
     * @param all - true if all attributes should be added regardless of change
     */
    public FeatureDiff(String path, @Nullable RevFeature newRevFeature,
            @Nullable RevFeature oldRevFeature, @Nullable RevFeatureType newRevFeatureType,
            @Nullable RevFeatureType oldRevFeatureType, boolean all) {

        this.path = path;
        this.newRevFeature = newRevFeature;
        this.oldRevFeature = oldRevFeature;
        this.newFeatureType = newRevFeatureType;
        this.oldFeatureType = oldRevFeatureType;
        diffs = new HashMap<PropertyDescriptor, AttributeDiff>();

        if (newRevFeature == null) {
            Preconditions.checkArgument(oldRevFeature != null, "A feature must be provided");
            Preconditions.checkArgument(oldRevFeatureType != null,
                    "Old feature type must be provided.");

            ImmutableList<PropertyDescriptor> oldAttributes = oldRevFeatureType.descriptors();

            for (int i = 0; i < oldAttributes.size(); i++) {
                Optional<Object> oldValue = oldRevFeature.get(i);
                PropertyDescriptor descriptor = oldAttributes.get(i);
                if (Geometry.class.isAssignableFrom(descriptor.getType().getBinding())) {
                    diffs.put(descriptor, new GeometryAttributeDiff((Geometry) oldValue.orNull(),
                            (Geometry) null));
                } else {
                    diffs.put(oldAttributes.get(i),
                            new GenericAttributeDiffImpl(oldValue.orNull(), null));
                }
            }
        } else if (oldRevFeature == null) {
            Preconditions.checkArgument(newRevFeatureType != null,
                    "New feature type must be provided.");

            ImmutableList<PropertyDescriptor> newAttributes = newRevFeatureType.descriptors();

            for (int i = 0; i < newAttributes.size(); i++) {
                Optional<Object> newValue = newRevFeature.get(i);
                PropertyDescriptor descriptor = newAttributes.get(i);
                if (Geometry.class.isAssignableFrom(descriptor.getType().getBinding())) {
                    diffs.put(descriptor, new GeometryAttributeDiff((Geometry) null,
                            (Geometry) newValue.orNull()));
                } else {
                    diffs.put(newAttributes.get(i),
                            new GenericAttributeDiffImpl(null, newValue.orNull()));
                }
            }
        } else {
            ImmutableList<PropertyDescriptor> oldAttributes = oldRevFeatureType.descriptors();
            ImmutableList<PropertyDescriptor> newAttributes = newRevFeatureType.descriptors();
            BitSet updatedAttributes = new BitSet(newRevFeature.size());
            for (int i = 0; i < oldAttributes.size(); i++) {
                Optional<Object> oldValue = oldRevFeature.get(i);
                int idx = newAttributes.indexOf(oldAttributes.get(i));
                if (idx != -1) {
                    Optional<Object> newValue = newRevFeature.get(idx);
                    if (!oldValue.equals(newValue) || all) {
                        if (Geometry.class
                                .isAssignableFrom(oldAttributes.get(i).getType().getBinding())) {
                            diffs.put(oldAttributes.get(i), new GeometryAttributeDiff(
                                    (Geometry) oldValue.orNull(), (Geometry) newValue.orNull()));
                        } else {
                            diffs.put(oldAttributes.get(i), new GenericAttributeDiffImpl(
                                    oldValue.orNull(), newValue.orNull()));
                        }
                    }
                    updatedAttributes.set(idx);
                } else {
                    if (Geometry.class
                            .isAssignableFrom(oldAttributes.get(i).getType().getBinding())) {
                        diffs.put(oldAttributes.get(i), new GeometryAttributeDiff(
                                (Geometry) oldValue.orNull(), (Geometry) null));
                    } else {
                        diffs.put(oldAttributes.get(i),
                                new GenericAttributeDiffImpl(oldValue.orNull(), null));
                    }
                }
            }
            updatedAttributes.flip(0, newRevFeature.size());
            for (int i = updatedAttributes.nextSetBit(0); i >= 0; i = updatedAttributes
                    .nextSetBit(i + 1)) {
                PropertyDescriptor descriptor = newAttributes.get(i);
                if (Geometry.class.isAssignableFrom(descriptor.getType().getBinding())) {
                    diffs.put(descriptor, new GeometryAttributeDiff((Geometry) null,
                            (Geometry) newRevFeature.get(i).orNull()));
                } else {
                    diffs.put(descriptor,
                            new GenericAttributeDiffImpl(null, newRevFeature.get(i).orNull()));
                }
            }
        }

    }

    public boolean hasDifferences() {
        return diffs.size() != 0;
    }

    public Map<PropertyDescriptor, AttributeDiff> getDiffs() {
        return ImmutableMap.copyOf(diffs);
    }

    /**
     * Returns the full path to the feature, including its name
     * 
     * @return
     */
    public String getPath() {
        return path;
    }

    /**
     * The feature type of the new version of the feature
     * 
     * @return
     */
    public RevFeatureType getNewFeatureType() {
        return newFeatureType;
    }

    /**
     * The feature type of the old version of the feature
     * 
     * @return
     */
    public RevFeatureType getOldFeatureType() {
        return oldFeatureType;
    }

    public RevFeature getNewFeature() {
        return newRevFeature;
    }

    public RevFeature getOldFeature() {
        return oldRevFeature;
    }

    /**
     * Returns a human-readable representation of this class. To get a string that can be used to
     * serialize this object, use the asText() method
     */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
        Iterator<Entry<PropertyDescriptor, AttributeDiff>> iter = entries.iterator();
        while (iter.hasNext()) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iter.next();
            PropertyDescriptor pd = entry.getKey();
            AttributeDiff ad = entry.getValue();
            sb.append(pd.getName() + "\t" + ad.toString() + "\n");
        }
        return sb.toString();

    }

    /**
     * Returns a serialized text version of this object
     * 
     * @return
     */
    public String asText() {

        StringBuilder sb = new StringBuilder();
        Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
        Iterator<Entry<PropertyDescriptor, AttributeDiff>> iter = entries.iterator();
        while (iter.hasNext()) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iter.next();
            PropertyDescriptor pd = entry.getKey();
            AttributeDiff ad = entry.getValue();
            sb.append(pd.getName().toString() + "\t" + ad.asText() + "\n");
        }
        return sb.toString();

    }

    /**
     * Returns a reversed version of the feature difference
     * 
     * @return
     */
    public FeatureDiff reversed() {
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
        for (Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = entries
                .iterator(); iterator.hasNext();) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iterator.next();
            map.put(entry.getKey(), entry.getValue().reversed());

        }
        return new FeatureDiff(path, map, newFeatureType, oldFeatureType);
    }

    @Override
    public boolean equals(Object o) {
        // TODO: this is a temporary simple comparison. Should be more elaborate
        if (!(o instanceof FeatureDiff)) {
            return false;
        }
        FeatureDiff f = (FeatureDiff) o;
        return f.asText().equals(asText());
        // return f.diffs.equals(diffs) && f.path.equals(path);
    }

    /**
     * Checks whether a FeatureDiff conflicts with this one
     * 
     * @param featureDiff the featureDiff to check against this one
     */
    public boolean conflicts(FeatureDiff featureDiff) {
        Map<PropertyDescriptor, AttributeDiff> otherDiffs = featureDiff.diffs;
        for (PropertyDescriptor pd : otherDiffs.keySet()) {
            if (diffs.containsKey(pd)) {
                AttributeDiff ad = diffs.get(pd);
                AttributeDiff otherAd = otherDiffs.get(pd);
                if (ad.conflicts(otherAd)) {
                    return true;
                }
            }
        }
        return false;
    }
}
