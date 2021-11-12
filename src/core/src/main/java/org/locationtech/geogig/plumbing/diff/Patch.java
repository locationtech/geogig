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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.storage.text.TextRevObjectSerializer;

/**
 * A patch that can be applied onto a working tree.
 * 
 */
public class Patch {

    private List<FeatureTypeDiff> alteredTrees;

    /**
     * Feature types needed to use the patch. This include those used by features involved, and aso
     * feature type that have been modified
     */
    private Map<ObjectId, RevFeatureType> featureTypes;

    /**
     * features that have been edited
     */
    private List<FeatureDiff> modifiedFeatures;

    /**
     * features that have been added.
     */
    private List<FeatureInfo> addedFeatures;

    /**
     * features that have been removed
     */
    private List<FeatureInfo> removedFeatures;

    public Patch() {
        this(Collections.emptyMap());
    }

    public Patch(Map<ObjectId, RevFeatureType> typeCache) {
        modifiedFeatures = new ArrayList<>();
        removedFeatures = new ArrayList<>();
        addedFeatures = new ArrayList<>();
        alteredTrees = new ArrayList<>();
        featureTypes = new HashMap<>(typeCache);
    }

    /**
     * Returns a list of features modified by this patch
     * 
     * @return
     */
    public List<FeatureDiff> getModifiedFeatures() {
        return new ArrayList<>(modifiedFeatures);
    }

    /**
     * Returns a list of features added by this patch
     * 
     * @return
     */
    public List<FeatureInfo> getAddedFeatures() {
        return new ArrayList<>(addedFeatures);
    }

    /**
     * Returns a list of features modified by this patch
     * 
     * @return
     */
    public List<FeatureInfo> getRemovedFeatures() {
        return new ArrayList<>(removedFeatures);
    }

    /**
     * Adds a feature to the list of newly added ones
     * 
     * @param path the path of the added feature
     * @param feature the feature
     * @param featureType the feature type of the added feature
     */
    public void addAddedFeature(String path, RevFeature feature, RevFeatureType featureType) {
        addedFeatures.add(FeatureInfo.insert(feature, featureType.getId(), path));
        addFeatureType(featureType);
    }

    /**
     * Adds a feature to the list of removed ones
     * 
     * @param path the path of the removed feature
     * @param feature the feature
     * @param featureType the feature type of the removed feature
     */
    public void addRemovedFeature(String path, RevFeature feature, RevFeatureType featureType) {
        removedFeatures.add(FeatureInfo.insert(feature, featureType.getId(), path));
        addFeatureType(featureType);
    }

    /**
     * Adds an element to the list of modified ones
     * 
     * @param diff
     */
    public void addModifiedFeature(FeatureDiff diff) {
        modifiedFeatures.add(diff);
        addFeatureType(diff.getNewFeatureType());
        addFeatureType(diff.getOldFeatureType());
    }

    /**
     * returns all the feature types used in this patch
     * 
     * @return
     */
    public List<RevFeatureType> getFeatureTypes() {
        return new ArrayList<>(featureTypes.values());
    }

    public Map<ObjectId, RevFeatureType> featureTypes() {
        return new HashMap<>(this.featureTypes);
    }

    /**
     * Given an id, returns the feature type with that type, if it exist in the list of features
     * types affected by this patch
     * 
     * @param id
     * @return
     */
    public Optional<RevFeatureType> getFeatureTypeFromId(ObjectId id) {
        return Optional.ofNullable(featureTypes.get(id));
    }

    /**
     * returns all the feature types modified by this patch
     * 
     * @return
     */
    public List<FeatureTypeDiff> getAlteredTrees() {
        return new ArrayList<>(alteredTrees);
    }

    public void addAlteredTree(DiffEntry diff) {
        ObjectId oldFeatureType = diff.getOldObject() == null ? null
                : diff.getOldObject().getMetadataId();
        ObjectId newFeatureType = diff.getNewObject() == null ? null
                : diff.getNewObject().getMetadataId();
        String path = diff.path();
        alteredTrees.add(new FeatureTypeDiff(path, oldFeatureType, newFeatureType));
    }

    public void addAlteredTree(FeatureTypeDiff diff) {
        alteredTrees.add(diff);
    }

    /**
     * Adds a new feature type to the list of them used in this patch
     * 
     * @param featureType
     */
    public void addFeatureType(RevFeatureType featureType) {
        featureTypes.put(featureType.getId(), featureType);
    }

    public @Override boolean equals(Object o) {
        // TODO: this is a temporary simple comparison. Should be more elaborate
        if (!(o instanceof Patch)) {
            return false;
        }
        Patch p = (Patch) o;
        return p.toString().equals(toString());
    }

    public boolean isEmpty() {
        return addedFeatures.isEmpty() && modifiedFeatures.isEmpty() && removedFeatures.isEmpty();
    }

    /**
     * This method is not intended to serialize the patch, as it misses some needed information. To
     * serialize the patch, use the {@link PatchSerializer} class instead. Use this method to show
     * patch content in a human-readable format
     */
    public @Override String toString() {
        TextRevObjectSerializer serializer = TextRevObjectSerializer.INSTANCE;
        StringBuilder sb = new StringBuilder();
        for (FeatureInfo feature : addedFeatures) {
            String path = feature.getPath();
            sb.append("A\t" + path + "\t" + feature.getFeatureTypeId() + "\n");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            RevFeature revFeature = feature.getFeature();
            try {
                serializer.write(revFeature, output);
            } catch (IOException e) {
            }
            sb.append(output.toString());
            sb.append('\n');
        }
        for (FeatureInfo feature : removedFeatures) {
            String path = feature.getPath();
            sb.append("R\t" + path + "\t" + feature.getFeatureTypeId() + "\n");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            RevFeature revFeature = feature.getFeature();
            try {
                serializer.write(revFeature, output);
            } catch (IOException e) {
            }
            sb.append(output.toString());
            sb.append('\n');
        }
        for (FeatureDiff diff : modifiedFeatures) {
            sb.append("M\t" + diff.getPath() /*
                                              * + "\t" + diff.getOldFeatureType().getId().toString()
                                              * + "\t" + diff.getNewFeatureType().getId().toString()
                                              */ + "\n");
            sb.append(diff.toString() + "\n");
        }
        for (FeatureTypeDiff diff : alteredTrees) {
            sb.append(featureTypeDiffAsString(diff) + "\n");
        }
        return sb.toString();
    }

    private String featureTypeDiffAsString(FeatureTypeDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append(diff.toString() + "\n");
        if (!diff.getNewFeatureType().equals(ObjectId.NULL)
                && !diff.getOldFeatureType().equals(ObjectId.NULL)) {
            RevFeatureType oldFeatureType = getFeatureTypeFromId(diff.getOldFeatureType()).get();
            RevFeatureType newFeatureType = getFeatureTypeFromId(diff.getNewFeatureType()).get();
            List<PropertyDescriptor> oldDescriptors = oldFeatureType.descriptors();
            List<PropertyDescriptor> newDescriptors = newFeatureType.descriptors();
            BitSet updatedDescriptors = new BitSet(newDescriptors.size());
            for (int i = 0; i < oldDescriptors.size(); i++) {
                PropertyDescriptor oldDescriptor = oldDescriptors.get(i);
                int idx = newDescriptors.indexOf(oldDescriptor);
                if (idx != -1) {
                    updatedDescriptors.set(idx);
                } else {
                    Class<?> oldType = oldDescriptor.getBinding();
                    sb.append("R\t" + oldDescriptors.get(i).getName().getLocalPart() + "["
                            + oldType.getName() + "]");
                }

            }
            updatedDescriptors.flip(0, updatedDescriptors.length());
            for (int i = updatedDescriptors.nextSetBit(0); i >= 0; i = updatedDescriptors
                    .nextSetBit(i + 1)) {
                PropertyDescriptor newDescriptor = newDescriptors.get(i);
                Class<?> oldType = newDescriptor.getBinding();
                sb.append("A\t" + newDescriptors.get(i).getName().getLocalPart() + "["
                        + oldType.getName() + "]");
            }
        }

        return sb.toString();
    }

    /**
     * Returns the total number of elements in this patch, whether added, removed or modified
     * 
     * @return
     */
    public int count() {
        return addedFeatures.size() + removedFeatures.size() + modifiedFeatures.size()
                + alteredTrees.size();
    }

    /**
     * Returns the reversed version of the current patch. Applying this reversed patch has the
     * opposite effect to applying the original one, and can be used to undo changes
     * 
     * @return a reversed version of this patch
     */
    public Patch reversed() {
        Patch patch = new Patch();
        patch.removedFeatures = addedFeatures;
        patch.addedFeatures = removedFeatures;
        for (FeatureDiff diff : modifiedFeatures) {
            patch.modifiedFeatures.add(diff.reversed());
        }
        for (FeatureTypeDiff diff : alteredTrees) {
            patch.alteredTrees.add(diff.reversed());
        }
        return patch;
    }

}
