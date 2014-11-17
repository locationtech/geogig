/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * A container for blame information. It just stores the commit of the last modification for each
 * attribute of a given feature
 * 
 */
public class BlameReport {

    private List<String> attributes;

    private HashMap<String, ValueAndCommit> changes;

    public BlameReport(RevFeatureType featureType) {
        attributes = Lists.newArrayList();
        for (PropertyDescriptor attribute : featureType.sortedDescriptors()) {
            attributes.add(attribute.getName().getLocalPart());
        }
        this.changes = new HashMap<String, ValueAndCommit>();

    }

    /**
     * Returns true if there is a commit associated to each attribute in this report
     * 
     * @return
     */
    public boolean isComplete() {
        return this.changes.size() == attributes.size();
    }

    /**
     * Reports an attribute as changed by a commit. If that attribute is not present in the report,
     * it will be added, and marked as added by the passed commit. If it is already added, calling
     * this method has no effect.
     * 
     * @param attribute the attribute changed
     * @param the value of the attribute
     * @param commit the commit that changed the passed attribute
     */
    public void addDiff(String attribute, Optional<?> value, RevCommit commit) {
        if (attributes.contains(attribute)) {
            if (!changes.containsKey(attribute)) {
                changes.put(attribute, new ValueAndCommit(value, commit));
            }
        }
    }

    /**
     * Returns the map of changes
     * 
     */
    public Map<String, ValueAndCommit> getChanges() {
        return ImmutableMap.copyOf(changes);
    }

    /**
     * Sets all the missing attributes as having been modified for the last time by the passed
     * commit.
     * 
     * @param commit
     */
    public void setFirstVersion(RevFeature feature, RevCommit commit) {
        ImmutableList<Optional<Object>> values = feature.getValues();
        for (int i = 0; i < attributes.size(); i++) {
            String attr = attributes.get(i);
            if (!changes.containsKey(attr)) {
                Optional<Object> value = values.get(i);
                changes.put(attr, new ValueAndCommit(value, commit));
            }
        }

    }

}
