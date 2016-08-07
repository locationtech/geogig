/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.vividsolutions.jts.geom.Envelope;

public class RevObjectTestSupport {

    public static RevTree createTreesTree(ObjectStore source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTree tree = createTreesTreeBuilder(source, numSubTrees, featuresPerSubtre, metadataId)
                .build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createTreesTreeBuilder(ObjectStore source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTreeBuilder builder = RevTreeBuilder.canonical(source);
        for (int treeN = 0; treeN < numSubTrees; treeN++) {
            RevTree subtree = createFeaturesTreeBuilder(source, "subtree" + treeN,
                    featuresPerSubtre).build();
            source.put(subtree);
            builder.put(
                    Node.create("subtree" + treeN, subtree.getId(), metadataId, TYPE.TREE, null));
        }
        return builder;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source,
            final String namePrefix, final int numEntries) {
        return createFeaturesTreeBuilder(source, namePrefix, numEntries, 0, false);
    }

    public static RevTree createFeaturesTree(ObjectStore source, final String namePrefix,
            final int numEntries) {
        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries).build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source,
            final String namePrefix, final int numEntries, final int startIndex,
            boolean randomIds) {

        RevTreeBuilder tree = RevTreeBuilder.canonical(source);
        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
        return tree;
    }

    public static RevTree createFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries, startIndex,
                randomIds).build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createLargeFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries, final int startIndex,
            boolean randomIds) {

        RevTreeBuilder tree = RevTreeBuilder.canonical(source);

        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
        return tree;
    }

    public static RevTree createLargeFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder builder = createLargeFeaturesTreeBuilder(source, namePrefix, numEntries,
                startIndex, randomIds);
        RevTree tree = builder.build();
        source.put(tree);
        return tree;
    }

    public static List<Node> featureNodes(int fromIndexInclussive, int toIndexExclussive,
            boolean randomIds) {

        List<Node> nodes = new ArrayList<>(1 + (toIndexExclussive - fromIndexInclussive));
        for (int i = fromIndexInclussive; i < toIndexExclussive; i++) {
            nodes.add(featureNode("f", i, randomIds));
        }
        return nodes;
    }

    public static Node featureNode(String namePrefix, int index) {
        return featureNode(namePrefix, index, false);
    }

    private static Random RND = new Random();

    public static Node featureNode(String namePrefix, int index, boolean randomIds) {
        String name = namePrefix + String.valueOf(index);
        ObjectId oid;
        if (randomIds) {
            byte[] raw = new byte[ObjectId.NUM_BYTES];
            RND.nextBytes(raw);
            oid = ObjectId.createNoClone(raw);
        } else {// predictable id
            oid = RevObjects.forString(name);
        }
        Node ref = Node.create(name, oid, ObjectId.NULL, TYPE.FEATURE,
                new Envelope(index, index + 1, index, index + 1));
        return ref;
    }

    /**
     * Only for testing: allows to return a {@link RevFeature} with the specified id instead of the
     * one resulting from {@link HashObject}
     */
    public static RevFeature featureForceId(ObjectId forceId, Object... rawValues) {
        RevFeatureBuilder builder = RevFeatureBuilder.builder().addAll(rawValues);
        return new TestFeatureImpl(forceId, builder.build().getValues());
    }

    public static RevFeature feature(Object... rawValues) {
        RevFeatureBuilder builder = RevFeatureBuilder.builder().addAll(rawValues);
        return builder.build();
    }

    private static class TestFeatureImpl extends AbstractRevObject implements RevFeature {

        private final ImmutableList<Optional<Object>> values;

        /**
         * Constructs a new {@code RevFeature} with the provided {@link ObjectId} and set of values
         * 
         * @param id the {@link ObjectId} to use for this feature
         * @param values a list of values, with {@link Optional#absent()} representing a null value
         */
        TestFeatureImpl(ObjectId id, ImmutableList<Optional<Object>> values) {
            super(id);
            this.values = values;
        }

        @Override
        public ImmutableList<Optional<Object>> getValues() {
            return values;
        }

        @Override
        public TYPE getType() {
            return TYPE.FEATURE;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Feature[");
            builder.append(getId().toString());
            builder.append("; ");
            boolean first = true;
            for (Optional<Object> value : getValues()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }

                String valueString = String.valueOf(value.orNull());
                builder.append(valueString.substring(0, Math.min(10, valueString.length())));
            }
            builder.append(']');
            return builder.toString();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Optional<Object> get(int index) {
            // we're intentionally not enforcing a safe copy in this test-only code
            return values.get(index);
        }

        @Override
        public void forEach(final Consumer<Object> consumer) {
            values.forEach((o) -> consumer.accept(o.orNull()));
        }
    }

}
