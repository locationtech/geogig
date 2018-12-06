/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.Nonnegative;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.MethodSorters;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.impl.RevObjectFactoryImpl;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.mockito.Mockito;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.util.InternationalString;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

/**
 * Abstract test suite to assess the conformance of {@link RevObjectFactory} implementations to the
 * interface contract.
 * <p>
 * Each {@link RevObjectFactory} implementation shall have a conformance test class that inherits
 * from this one and provides the concrete implementation by overriding {@link #newFactory()}.
 * 
 * @since 1.4
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class RevObjectFactoryConformanceTest {

    private static final RevObjectFactory DEFAULT = new RevObjectFactoryImpl();

    private static final ObjectId id1 = ObjectId
            .valueOf("1000000a1000000b1000000c1000000d1000000e"),
            id2 = ObjectId.valueOf("2000000a2000000b2000000c2000000d2000000e"),
            id3 = ObjectId.valueOf("3000000a3000000b3000000c3000000d3000000e"),
            id4 = ObjectId.valueOf("4000000a4000000b4000000c4000000d4000000e"),
            id5 = ObjectId.valueOf("5000000a5000000b5000000c5000000d5000000e"),
            id6 = ObjectId.valueOf("6000000a6000000b6000000c6000000d6000000e"),
            id7 = ObjectId.valueOf("7000000a7000000b7000000c7000000d7000000e"),
            id8 = ObjectId.valueOf("8000000a8000000b8000000c8000000d8000000e");

    private static final RevPerson person1 = DEFAULT.createPerson("Gabe", "gabe@example.com", 10000,
            -3);

    private static final RevPerson person2 = DEFAULT.createPerson("Dave", null, 50000, -6);

    private RevObjectFactory factory;

    public @Rule ExpectedException ex = ExpectedException.none();

    public @Before void setUp() throws Exception {
        factory = newFactory();
    }

    public @After void tearDown() throws Exception {
    }

    protected abstract RevObjectFactory newFactory();

    private void testCommit(ObjectId id, ObjectId treeId, List<ObjectId> parents, RevPerson author,
            RevPerson committer, String message) {

        RevCommit actual = factory.createCommit(id, treeId, parents, author, committer, message);
        assertNotNull(actual);

        final RevCommit expected = DEFAULT.createCommit(id, treeId, parents, author, committer,
                message);
        RevObjectTestUtil.deepEquals(expected, actual);
    }

    public @Test final void createCommitNoParents() throws IOException {
        List<ObjectId> parents = Collections.emptyList();
        String message = "sample commit message";
        testCommit(id1, id2, parents, person1, person2, message);
    }

    public @Test final void createCommitSingleParent() throws IOException {
        List<ObjectId> parents = Collections.singletonList(id3);
        String message = "sample commit message";
        testCommit(id1, id2, parents, person1, person2, message);
    }

    public @Test final void createCommitTwoParents() throws IOException {
        List<ObjectId> parents = newArrayList(id3, id4);
        String message = "sample commit message";
        testCommit(id1, id2, parents, person1, person2, message);
    }

    public @Test final void createCommitSeveralParents() throws IOException {
        List<ObjectId> parents = newArrayList(id2, id3, id4, id5);
        String message = "sample commit message";
        testCommit(id1, id2, parents, person1, person2, message);
    }

    public @Test final void createCommitNullId() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCommit(null, id2, Collections.emptyList(), person1, person2, "message");
    }

    public @Test final void createCommitNullTreeId() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("treeId");
        testCommit(id1, null, Collections.emptyList(), person1, person2, "message");
    }

    public @Test final void createCommitNullParents() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("parents");
        testCommit(id1, id2, null, person1, person2, "message");
    }

    public @Test final void createCommitNullElementInParentsList() throws IOException {
        List<ObjectId> parents = newArrayList(id3, null, id4);
        ex.expect(NullPointerException.class);
        ex.expectMessage("null parent at index");
        testCommit(id1, id2, parents, person1, person2, "message");
    }

    public @Test final void createCommitNullAuthor() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("author");
        testCommit(id1, id2, Collections.emptyList(), null, person2, "message");
    }

    public @Test final void createCommitNullCommitter() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("committer");
        testCommit(id1, id2, Collections.emptyList(), person1, null, "message");
    }

    public @Test final void createCommitNullMessage() throws IOException {
        ex.expect(NullPointerException.class);
        ex.expectMessage("message");
        testCommit(id1, id2, Collections.emptyList(), person1, person2, null);
    }

    private Bucket testCreateBucket(ObjectId bucketTree, int bucketIndex, double x1, double x2,
            double y1, double y2) {
        return testCreateBucket(bucketTree, bucketIndex, new Envelope(x1, x2, y1, y2));
    }

    private Bucket testCreateBucket(ObjectId bucketTree, int bucketIndex, Envelope bounds) {
        Bucket actual = bucket(bucketTree, bucketIndex, bounds);
        Bucket expected = DEFAULT.createBucket(bucketTree, bucketIndex, bounds);
        RevObjectTestUtil.deepEquals(expected, actual);
        return actual;
    }

    private Bucket bucket(ObjectId bucketTree, int bucketIndex) {
        return bucket(bucketTree, bucketIndex, null);
    }

    private Bucket bucket(ObjectId bucketTree, int bucketIndex, Envelope bounds) {
        Bucket actual = factory.createBucket(bucketTree, bucketIndex, bounds);
        assertNotNull(actual);
        return actual;
    }

    public @Test final void createBucketNullBounds() {
        testCreateBucket(id1, 0, null);
        testCreateBucket(id1, 31, null);
    }

    public @Test final void createBucketBounds() {
        testCreateBucket(id1, 31, -1.1, 1.0000001, -10.1, 10.00001);
    }

    public @Test final void createBucketNegativeIndex() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("negative index");
        testCreateBucket(id1, -1, null);
    }

    public @Test final void createBucketNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("bucketTree");
        testCreateBucket(null, 1, null);
    }

    public void testCreateNode(String name, ObjectId oid, ObjectId metadataId, TYPE type,
            @Nullable Envelope bounds, @Nullable Map<String, Object> extraData) {

        Node actual = createNode(name, oid, metadataId, type, bounds, extraData);
        assertNotNull(actual);
        Node expected = DEFAULT.createNode(name, oid, metadataId, type, bounds, extraData);
        RevObjectTestUtil.deepEquals(expected, actual);
    }

    public @Test void createNodeNullBoundsNullExtradata() {
        testCreateNode("feature1", id1, id2, TYPE.FEATURE, null, null);
        testCreateNode("feature2", id2, ObjectId.NULL, TYPE.FEATURE, null, null);
        testCreateNode("tree1", id1, id2, TYPE.TREE, null, null);
        testCreateNode("tree2", id2, ObjectId.NULL, TYPE.TREE, null, null);
    }

    public @Test void createNodeNullExtradata() {
        Envelope bounds = new Envelope(-179.999999999, 179.99999999, -90.0000000001, 90.000000001);
        testCreateNode("feature1", id1, id2, TYPE.FEATURE, bounds, null);
        testCreateNode("feature2", id2, ObjectId.NULL, TYPE.FEATURE, bounds, null);
        testCreateNode("tree1", id1, id2, TYPE.TREE, bounds, null);
        testCreateNode("tree2", id2, ObjectId.NULL, TYPE.TREE, bounds, null);
    }

    public @Test void createNode() {
        Envelope bounds = new Envelope(-179.999999999, 179.99999999, -90.0000000001, 90.000000001);
        Map<String, Object> extraData = createExtraData(0);
        testCreateNode("feature1", id1, id2, TYPE.FEATURE, bounds, extraData);
        testCreateNode("feature2", id2, ObjectId.NULL, TYPE.FEATURE, bounds, extraData);
        testCreateNode("tree1", id1, id2, TYPE.TREE, bounds, extraData);
        testCreateNode("tree2", id2, ObjectId.NULL, TYPE.TREE, bounds, extraData);
    }

    public @Test void createNodeNullName() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("name");
        testCreateNode(null, id1, id2, TYPE.FEATURE, null, null);
    }

    public @Test void createNodeNullObjectId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("objectId");
        testCreateNode("id", null, id2, TYPE.FEATURE, null, null);
    }

    public @Test void createNodeNullMetadataId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("metadataId");
        testCreateNode("id", id1, null, TYPE.FEATURE, null, null);
    }

    public @Test void createNodeNullType() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("type");
        TYPE type = null;
        testCreateNode("id", id1, id2, type, null, null);
    }

    public @Test void createNodeIllegalType() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Invalid object type");
        TYPE type = TYPE.COMMIT;
        testCreateNode("id", id1, id2, type, null, null);
    }

    private RevTree testCreateLeafTree(ObjectId id, long size, List<Node> trees,
            List<Node> features) {
        RevTree actual = factory.createTree(id, size, trees, features);
        assertNotNull(actual);
        RevTree expected = DEFAULT.createTree(id, size, trees, features);
        assertNotNull(expected);
        assertEquals(id, expected.getId());
        assertEquals(size, expected.size());
        assertEquals(trees.size(), expected.numTrees());
        assertEquals(trees.size(), expected.treesSize());
        assertEquals(features.size(), expected.featuresSize());
        assertEquals(0, expected.bucketsSize());
        RevObjectTestUtil.deepEquals(expected, actual);
        return actual;
    }

    private RevTree testCreateTree(ObjectId id, @Nonnegative long size, int childTreeCount,
            Bucket... buckets) {
        SortedSet<Bucket> set = buckets == null ? null : new TreeSet<>(Arrays.asList(buckets));
        return testCreateTree(id, size, childTreeCount, set);
    }

    private RevTree testCreateTree(ObjectId id, @Nonnegative long size, int childTreeCount,
            SortedSet<Bucket> buckets) {

        RevTree actual = factory.createTree(id, size, childTreeCount, buckets);
        assertNotNull(actual);
        RevTree expected = DEFAULT.createTree(id, size, childTreeCount, buckets);
        assertNotNull(expected);
        assertEquals(id, expected.getId());
        assertEquals(size, expected.size());
        assertEquals(0, expected.treesSize());
        assertEquals(0, expected.featuresSize());
        assertEquals(childTreeCount, expected.numTrees());
        assertEquals(buckets.size(), expected.bucketsSize());
        RevObjectTestUtil.deepEquals(expected, actual);
        return actual;
    }

    public @Test final void createTreeLeafEmpty() {
        testCreateLeafTree(id1, 0, Collections.emptyList(), Collections.emptyList());
    }

    public @Test final void createTreeLeafNegativeSize() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("negative size");
        testCreateLeafTree(id1, -1, Collections.emptyList(), Collections.emptyList());
    }

    public @Test final void createTreeLeafNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCreateLeafTree(null, 0, Collections.emptyList(), Collections.emptyList());
    }

    public @Test final void createTreeLeafNullTrees() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("trees");
        testCreateLeafTree(id1, 0, null, Collections.emptyList());
    }

    public @Test final void createTreeLeafNullFeatures() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("features");
        testCreateLeafTree(id1, 0, Collections.emptyList(), null);
    }

    public @Test final void createTreeLeafNullElementInTrees() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("null node in trees at index 1");
        List<Node> trees = newArrayList(treeNode("t1", id1, id5), null, treeNode("t2", id2, id5));
        testCreateLeafTree(id1, 0, trees, Collections.emptyList());
    }

    public @Test final void createTreeLeafNullElementInFeatures() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("null node in features at index 1");
        List<Node> features = newArrayList(featureNode("f1", id1), null, featureNode("f2", id2));
        testCreateLeafTree(id1, 0, Collections.emptyList(), features);
    }

    public @Test final void createTreeLeafFeatureNodeInTrees() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("trees contains FEATURE node at index 2");
        List<Node> trees = newArrayList(treeNode("t1", id1, id5), treeNode("t2", id2, id5), //
                featureNode("f1", id3)// WRONG
        );
        testCreateLeafTree(id1, 0, trees, Collections.emptyList());
    }

    public @Test final void createTreeLeafTreeNodeInFeatures() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("features contains TREE node at index 2");
        List<Node> features = newArrayList(featureNode("f1", id1), featureNode("f2", id2), //
                treeNode("t1", id3, id5)// WRONG
        );
        testCreateLeafTree(id1, 0, Collections.emptyList(), features);
    }

    public @Test final void createTreeLeaf() {
        List<Node> trees = newArrayList(treeNode("t1", id1, id5), treeNode("t2", id2, id5));
        List<Node> features = newArrayList(featureNode("f1", id1), featureNode("f2", id2),
                featureNode("f3", id3), featureNode("f4", id4), featureNode("f5", id5));
        testCreateLeafTree(id1, 0, trees, features);
    }

    public @Test final void createTreeLeafFeatureNodesWithExtraData() {
        List<Node> features = newArrayList(featureNodeFull("f1", id1, 1),
                featureNodeFull("f2", id2, 2));
        testCreateLeafTree(id1, 0, Collections.emptyList(), features);
    }

    public @Test final void createTreeLeafFeatureNodesSomeWithExtraData() {
        List<Node> features = newArrayList(//
                featureNodeFull("f1", id1, 1), // with extra data
                featureNode("f2", id2), // without extra data
                featureNodeFull("f3", id3, 3), // with extra data
                featureNode("f4", id4));// without extra data
        testCreateLeafTree(id5, 10, Collections.emptyList(), features);
    }

    public @Test final void createTreeLeafTreeNodesWithExtraData() {
        List<Node> trees = newArrayList(treeNodeFull("t1", id1, id2, 1),
                treeNodeFull("t2", id3, id4, 2), treeNodeFull("t3", id5, id6, 3),
                treeNodeFull("t4", id7, id8, 4));
        testCreateLeafTree(id5, 10, trees, Collections.emptyList());
    }

    public @Test final void createTreeLeafTreeNodesSomeWithExtraData() {
        List<Node> trees = newArrayList(//
                treeNodeFull("t1", id1, id2, 1), // with extra data
                treeNode("t2", id3, id4), // without extra data
                treeNodeFull("t3", id5, id6, 3), // with extra data
                treeNode("t4", id7, id8));// without extra data
        testCreateLeafTree(id5, 10, trees, Collections.emptyList());
    }

    public @Test final void createTreeLeafTreeAndFeatureNodesWithExtraData() {
        List<Node> trees = newArrayList(treeNode("t1", id1, id4), treeNode("t2", id2, id5));
        List<Node> features = newArrayList(featureNodeFull("f1", id1, 1),
                featureNodeFull("f2", id2, 2), featureNodeFull("f3", id3, 3),
                featureNodeFull("f4", id4, 4), featureNodeFull("f5", id5, 5));
        testCreateLeafTree(id1, 0, trees, features);
    }

    public @Test final void createTreeLeafTreeSomeWithExtraData() {
        List<Node> trees = newArrayList(//
                treeNodeFull("t1", id1, id2, 1), // with extra data
                treeNode("t2", id3, id4), // without extra data
                treeNodeFull("t3", id5, id6, 3), // with extra data
                treeNode("t4", id7, id8));// without extra data
        List<Node> features = newArrayList(//
                featureNodeFull("f1", id1, 1), // with extra data
                featureNode("f2", id2), // without extra data
                featureNodeFull("f3", id3, 3), // with extra data
                featureNode("f4", id4));// without extra data
        testCreateLeafTree(id5, 10, trees, features);
    }

    public @Test final void createTreeBuckets() {
        testCreateTree(id5, 1000, 11, bucket(id4, 4), bucket(id3, 3), bucket(id2, 2),
                bucket(id1, 1));
    }

    public @Test final void createTreeBucketsNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCreateTree(null, 1000, 11, bucket(id4, 4), bucket(id3, 3), bucket(id2, 2),
                bucket(id1, 1));
    }

    public @Test final void createTreeBucketsNullBuckets() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("buckets");
        testCreateTree(id5, 1000, 11, (SortedSet<Bucket>) null);
    }

    public @Test final void createTreeBucketsNullElementInBuckets() {
        final SortedSet<Bucket> buckets = new TreeSet<>();
        buckets.add(bucket(id4, 4));
        buckets.add(bucket(id2, 2));
        SortedSet<Bucket> spied = Mockito.spy(buckets);
        Mockito.doReturn(Iterators.concat(buckets.iterator(), Iterators.singletonIterator(null)))
                .when(spied).iterator();
        ex.expect(NullPointerException.class);
        ex.expectMessage("null bucket");
        testCreateTree(id5, 1000, 11, spied);
    }

    public @Test final void createTreeBucketsNegativeSize() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("negative size");
        testCreateTree(id5, -1, 11, bucket(id4, 4), bucket(id3, 3), bucket(id2, 2), bucket(id1, 1));
    }

    public @Test final void createTreeBucketsNegativeChildTreeCount() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("negative child tree count");
        testCreateTree(id5, 1000, -1, bucket(id4, 4), bucket(id3, 3), bucket(id2, 2),
                bucket(id1, 1));
    }

    public RevTag testCreateTag(ObjectId id, String name, ObjectId commitId, String message,
            RevPerson tagger) {

        RevTag actual = factory.createTag(id, name, commitId, message, tagger);
        assertNotNull(actual);
        RevTag expected = DEFAULT.createTag(id, name, commitId, message, tagger);
        assertNotNull(expected);
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCommitId(), actual.getCommitId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getMessage(), actual.getMessage());
        assertEquals(expected.getTagger(), actual.getTagger());
        assertEquals(expected, actual);
        return actual;
    }

    public @Test final void createTag() {
        testCreateTag(id1, "v1.0", id2, "some version message", person1);
    }

    public @Test final void createTagNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCreateTag(null, "v1.0", id2, "some version message", person1);
    }

    public @Test final void createTagNullCommitId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("commitId");
        testCreateTag(id1, "v1.0", null, "some version message", person1);
    }

    public @Test final void createTagNullName() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("name");
        testCreateTag(id1, null, id2, "some version message", person1);
    }

    public @Test final void createTagNullMessage() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("message");
        testCreateTag(id1, "v1.0", id2, null, person1);
    }

    public @Test final void createTagNullAuthor() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("tagger");
        testCreateTag(id1, "v1.0", id2, "some version message", null);
    }

    private final RevFeatureType testCreateFeatureType(ObjectId id, String typeSpec) {
        SimpleFeatureType type = null;
        if (typeSpec != null) {
            try {
                type = DataUtilities.createType("roads", typeSpec);
            } catch (SchemaException e) {
                throw new RuntimeException(e);
            }
        }
        return testCreateFeatureType(id, type);
    }

    private RevFeatureType testCreateFeatureType(ObjectId id, SimpleFeatureType type) {
        RevFeatureType actual = factory.createFeatureType(id, type);
        assertNotNull(actual);
        RevFeatureType expected = DEFAULT.createFeatureType(id, type);
        assertNotNull(expected);
        RevObjectTestUtil.deepEquals(expected, actual);
        return actual;
    }

    public @Test final void createFeatureTypeNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCreateFeatureType(null, "sp:String,ip:Integer,pp:Point:srid=4326");
    }

    public @Test final void createFeatureTypeNullFeatureType() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("type");
        testCreateFeatureType(id1, (SimpleFeatureType) null);
    }

    public @Test final void createFeatureType() {
        testCreateFeatureType(id1, "sp:String,ip:Integer,pp:Point:srid=4326");
    }

    public @Test final void createFeatureTypeAllSupportedAttributeTypes() {
        FieldType[] fieldTypes = FieldType.values();
        StringBuilder typeSpec = new StringBuilder();
        for (FieldType ft : fieldTypes) {
            if (typeSpec.length() > 0) {
                typeSpec.append(',');
            }
            if (ft != FieldType.NULL && ft != FieldType.UNKNOWN) {
                typeSpec.append(ft + "_type:").append(ft.getBinding().getName());
            }
        }
        final String spec = typeSpec.toString();

        final RevFeatureType revFType = testCreateFeatureType(id1, spec);
        for (FieldType ft : fieldTypes) {
            if (ft == FieldType.NULL || ft == FieldType.UNKNOWN) {
                continue;
            }
            String attName = ft + "_type";
            PropertyDescriptor descriptor = revFType.type().getDescriptor(attName);
            assertNotNull(descriptor);
            assertEquals(attName, ft.getBinding(), descriptor.getType().getBinding());
        }
    }

    public @Test final void createFeatureTypeUnsupportedAttributeType() {
        Class<?> unsupportedBinding = TimeZone.class;
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("unsupported type: " + unsupportedBinding.getName());
        testCreateFeatureType(id1, "prop:" + unsupportedBinding.getName());
    }

    public @Test final void createFeatureTypeCardinality() {
        FeatureTypeFactory ftf = CommonFactoryFinder.getFeatureTypeFactory(null);

        Name attName1 = new NameImpl("int_Type");
        Name attName2 = new NameImpl("string_Type");
        Class<?> binding = Integer.class;
        boolean isIdentifiable = false;
        boolean isAbstract = false;
        List<Filter> restrictions = null;
        AttributeType superType = null;
        InternationalString description = null;
        AttributeType attribute1 = ftf.createAttributeType(attName1, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);
        AttributeType attribute2 = ftf.createAttributeType(attName2, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);

        int minOccurs = 2;
        int maxOccurs = 5;
        boolean isNillable = true;
        Object defaultValue = null;
        AttributeDescriptor descriptor1 = ftf.createAttributeDescriptor(attribute1,
                attribute1.getName(), minOccurs, maxOccurs, isNillable, defaultValue);
        minOccurs = 0;
        maxOccurs = 10;
        AttributeDescriptor descriptor2 = ftf.createAttributeDescriptor(attribute2,
                attribute2.getName(), minOccurs, maxOccurs, isNillable, defaultValue);

        SimpleFeatureTypeBuilder sftb = new SimpleFeatureTypeBuilder(ftf);
        sftb.add(descriptor1);
        sftb.add(descriptor2);
        sftb.setName(new NameImpl("SimpleName"));
        SimpleFeatureType featureType = sftb.buildFeatureType();
        testCreateFeatureType(id1, featureType);
    }

    public @Test final void createFeatureTypeNillability() {
        FeatureTypeFactory ftf = CommonFactoryFinder.getFeatureTypeFactory(null);

        Name attName1 = new NameImpl("int_Type");
        Name attName2 = new NameImpl("string_Type");
        Class<?> binding = Integer.class;
        boolean isIdentifiable = false;
        boolean isAbstract = false;
        List<Filter> restrictions = null;
        AttributeType superType = null;
        InternationalString description = null;
        AttributeType attribute1 = ftf.createAttributeType(attName1, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);
        AttributeType attribute2 = ftf.createAttributeType(attName2, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);

        int minOccurs = 0;
        int maxOccurs = 1;
        boolean isNillable = true;
        Object defaultValue = null;
        AttributeDescriptor descriptor1 = ftf.createAttributeDescriptor(attribute1,
                attribute1.getName(), minOccurs, maxOccurs, isNillable, defaultValue);

        isNillable = false;
        AttributeDescriptor descriptor2 = ftf.createAttributeDescriptor(attribute2,
                attribute2.getName(), minOccurs, maxOccurs, isNillable, defaultValue);

        SimpleFeatureTypeBuilder sftb = new SimpleFeatureTypeBuilder(ftf);
        sftb.add(descriptor1);
        sftb.add(descriptor2);
        sftb.setName(new NameImpl("SimpleName"));
        SimpleFeatureType featureType = sftb.buildFeatureType();
        testCreateFeatureType(id1, featureType);
    }

    public @Test final void createFeatureTypeNamesWithNamespaces() {
        FeatureTypeFactory ftf = CommonFactoryFinder.getFeatureTypeFactory(null);

        Name attName1 = new NameImpl("http://geogig.org/test/att1", "int_Type");
        Name attName2 = new NameImpl("http://geogig.org/test/att2", "string_Type");
        Class<?> binding = Integer.class;
        boolean isIdentifiable = false;
        boolean isAbstract = false;
        List<Filter> restrictions = null;
        AttributeType superType = null;
        InternationalString description = null;
        AttributeType attribute1 = ftf.createAttributeType(attName1, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);
        AttributeType attribute2 = ftf.createAttributeType(attName2, binding, isIdentifiable,
                isAbstract, restrictions, superType, description);

        int minOccurs = 0;
        int maxOccurs = 1;
        boolean isNillable = false;
        Object defaultValue = null;

        Name propName1 = new NameImpl("http://geogig.org/test/prop1", "int_Property");
        Name propName2 = new NameImpl("http://geogig.org/test/prop2", "string_Property");
        AttributeDescriptor descriptor1 = ftf.createAttributeDescriptor(attribute1, propName1,
                minOccurs, maxOccurs, isNillable, defaultValue);

        AttributeDescriptor descriptor2 = ftf.createAttributeDescriptor(attribute2, propName2,
                minOccurs, maxOccurs, isNillable, defaultValue);

        Name typeName = new NameImpl("http://geogig.org/test/type", "QualifiedTypeName");
        SimpleFeatureTypeBuilder sftb = new SimpleFeatureTypeBuilder(ftf);
        sftb.add(descriptor1);
        sftb.add(descriptor2);
        sftb.setName(typeName);
        SimpleFeatureType featureType = sftb.buildFeatureType();
        testCreateFeatureType(id1, featureType);
    }

    private void testCreateValueArray(List<Object> values) {
        ValueArray actual = factory.createValueArray(values);
        ValueArray expected = DEFAULT.createValueArray(values);
        RevObjectTestUtil.deepEquals(expected, actual);
    }

    private void testCreateValueArray(Object[] values) {
        ValueArray actual = factory.createValueArray(values);
        ValueArray expected = DEFAULT.createValueArray(values);
        RevObjectTestUtil.deepEquals(expected, actual);
    }

    private void testCreateFeature(ObjectId id, List<Object> values) {
        RevFeature actual = factory.createFeature(id, values);
        assertNotNull(actual);
        RevFeature expected = DEFAULT.createFeature(id, values);
        assertNotNull(expected);
        assertEquals(id, expected.getId());
        RevObjectTestUtil.deepEquals(expected, actual);
    }

    public @Test final void createValueArrayList() {
        List<Object> values = createValuesAllSupportedTypes();
        testCreateValueArray(values);
    }

    public @Test final void createValueArrayNoArrayElements() {
        List<Object> values = createValuesAllSupportedTypesExceptArrays();
        testCreateValueArray(values);
    }

    public @Test final void createValueArrayAllSupportedTypes() {
        Object[] values = createValuesAllSupportedTypes().toArray();
        testCreateValueArray(values);
    }

    public @Test final void createFeatureNoArrayElements() {
        List<Object> values = createValuesAllSupportedTypesExceptArrays();
        testCreateFeature(id1, values);
    }

    public @Test final void createFeatureAllSupportedTypes() {
        List<Object> values = createValuesAllSupportedTypes();
        testCreateFeature(id1, values);
    }

    public @Test final void createFeatureNullId() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("id");
        testCreateFeature(null, Collections.emptyList());
    }

    public @Test final void createFeatureNullValues() {
        ex.expect(NullPointerException.class);
        ex.expectMessage("values");
        testCreateFeature(id1, null);
    }

    public @Test final void createFeatureEmptyValues() {
        testCreateFeature(id1, Collections.emptyList());
    }

    private Node featureNode(String name, ObjectId oid) {
        return createNode(name, oid, ObjectId.NULL, TYPE.FEATURE, null, null);
    }

    private Node featureNodeFull(String name, ObjectId oid, int i) {
        Envelope env = new Envelope(-i, i, -i, i);
        Map<String, Object> md = createExtraData(i);
        return createNode(name, oid, ObjectId.NULL, TYPE.FEATURE, env, md);
    }

    private Node treeNode(String name, ObjectId oid, ObjectId mdId) {
        return createNode(name, oid, mdId, TYPE.TREE, null, null);
    }

    private Node treeNodeFull(String name, ObjectId oid, ObjectId mdId, int i) {
        Envelope env = new Envelope(-i, i, -i, i);
        Map<String, Object> ed = createExtraData(i);
        return createNode(name, oid, mdId, TYPE.TREE, env, ed);
    }

    private Node createNode(String name, ObjectId oid, ObjectId metadataId, TYPE type,
            Envelope bounds, Map<String, Object> extraData) {
        Node actual = factory.createNode(name, oid, metadataId, type, bounds, extraData);
        return actual;
    }

    private Map<String, Object> createExtraData(int nodeIndex) {
        Map<String, Object> map1, map2, extraData;
        map1 = new HashMap<>();
        map2 = new TreeMap<>();

        map1.put("long", Long.valueOf(nodeIndex));
        map2.put("long", Long.valueOf(nodeIndex));

        map1.put("int", Integer.valueOf(1000 + nodeIndex));
        map2.put("int", Integer.valueOf(1000 + nodeIndex));

        map1.put("string", "hello " + nodeIndex);
        map2.put("string", "hello " + nodeIndex);

        Geometry geom = geom(
                String.format("LINESTRING(%d 1, 1.1 %d.1, 100 1000)", nodeIndex, nodeIndex));
        map1.put("geom", geom);
        map2.put("geom", geom);

        extraData = ImmutableMap.of("I", (Object) "am", "a", (Object) "different", "map than",
                (Object) map1, "and", (Object) map2);

        return extraData;
    }

    private Geometry geom(String wkt) {
        Geometry value;
        try {
            value = new WKTReader2().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private List<Object> createValuesAllSupportedTypesExceptArrays() {
        List<Object> values = createValuesAllSupportedTypes();
        values = values.stream().filter(v -> v != null && !v.getClass().isArray())
                .collect(Collectors.toList());
        return values;
    }

    private List<Object> createValuesAllSupportedTypes() {
        FieldType[] types = FieldType.values();
        List<Object> values = new ArrayList<>();
        for (FieldType ft : types) {
            if (ft != FieldType.UNKNOWN) {
                values.add(RevObjectTestUtil.sampleValue(ft));
            }
        }
        return values;
    }

}
