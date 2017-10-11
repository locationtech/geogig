/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import static com.google.common.base.Preconditions.checkState;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.geotools.data.DataUtilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.geogig.geotools.test.storage.MemoryDataStoreWithProvidedFIDSupport;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.RevFeatureBuilder;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.io.ParseException;

/**
 * A helper class to set repositories to a desired state to aid in integration testing.
 * <p>
 * The test data is as follows:
 *
 * <pre>
 * <code>
 *
 *                              ______ (11, 11)
 *                              |    /|
 *                            Line.2/ |
 *                              |  o (10,10) Point.2
 *                              | /   |
 *                              |/____| Polygon.2
 *                           (9, 9)
 *
 *
 *                 ______ (1, 1)
 *                 |    /|
 *               Line.1/ |
 *                 |  o (0,0) Point.1
 *                 | /   |
 *                 |/____| Polygon.1
 *              (-1, -1)
 *
 *
 *
 *     ______ (-9, -9)
 *     |    /|
 *   Line.3/ |
 *     |  o (-10,-10) Point.3
 *     | /   |
 *     |/____| Polygon.3
 * (-11, -11)
 * </code>
 * </pre>
 */
public class TestData {

    private static final Logger LOG = LoggerFactory.getLogger(TestData.class);

    public static final String pointsTypeSpec = "sp:String,ip:Integer,geom:Point:srid=4326";

    public static final String linesTypeSpec = "sp:String,ip:Integer,geom:LineString:srid=4326";

    public static final String polyTypeSpec = "sp:String,ip:Integer,pp:Polygon:srid=4326";

    public static final SimpleFeatureType pointsType, linesType, polysType;

    public static final SimpleFeature point1, point2, point3, point4;

    public static final SimpleFeature point1_modified, point2_modified, point3_modified;

    public static final SimpleFeature line1, line2, line3;

    public static final SimpleFeature poly1, poly2, poly3, poly4, poly1_modified1, poly1_modified2;

    static {
        try {
            pointsType = DataUtilities.createType("http://geogig.org", "Points", pointsTypeSpec);
            linesType = DataUtilities.createType("http://geogig.org", "Lines", linesTypeSpec);
            polysType = DataUtilities.createType("http://geogig.org", "Polygons", polyTypeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }

        point1 = feature(pointsType, "Point.1", "StringProp1_1", 1000, "POINT(0 0)");
        point2 = feature(pointsType, "Point.2", "StringProp1_2", 2000, "POINT(-10 -10)");
        point3 = feature(pointsType, "Point.3", "StringProp1_3", 3000, "POINT(10 10)");
        point4 = feature(pointsType, "Point.4", "StringProp1_4", 4000, "POINT(15 15)");

        point1_modified = feature(pointsType, "Point.1", "StringProp1_1", 1500, "POINT(0 0)");
        point2_modified = feature(pointsType, "Point.2", "StringProp1_2", 2000, "POINT(-15 -10)");
        point3_modified = feature(pointsType, "Point.3", "StringProp1_3_M", 3000, "POINT(10 10)");

        line1 = feature(linesType, "Line.1", "StringProp2_1", 1000, "LINESTRING (-1 -1, 1 1)");
        line2 = feature(linesType, "Line.2", "StringProp2_2", 2000, "LINESTRING (-11 -11, -9 -9)");
        line3 = feature(linesType, "Line.3", "StringProp2_3", 3000, "LINESTRING (9 9, 11 11)");

        poly1 = feature(polysType, "Polygon.1", "StringProp3_1", 1000,
                "POLYGON ((-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
        poly2 = feature(polysType, "Polygon.2", "StringProp3_2", 2000,
                "POLYGON ((-11 -11, -11 -9, -9 -9, -9 -11, -11 -11))");
        poly3 = feature(polysType, "Polygon.3", "StringProp3_3", 3000,
                "POLYGON ((9 9, 9 11, 11 11, 11 9, 9 9))");
        poly4 = feature(polysType, "Polygon.4", "StringProp3_4", 4000,
                "POLYGON ((4 4, 4 5, 5 5, 5 4, 4 4))");
        poly1_modified1 = feature(polysType, "Polygon.1", "StringProp3_1", 1000,
                "POLYGON ((-4 -4, -4 4, 4 4, 4 -4, -4 -4))");
        poly1_modified2 = feature(polysType, "Polygon.1", "StringProp3_1", 1000,
                "POLYGON ((-5 -5, -5 5, 5 5, 5 -5, -5 -5))");
    }

    private Repository repo;

    private GeogigTransaction transaction = null;

    public TestData(final GeoGIG repo) throws Exception {
        this.repo = repo.getOrCreateRepository();
    }

    public TestData(final Repository repo) throws Exception {
        this.repo = repo;
    }

    public Repository getRepo() {
        return repo;
    }

    public static MemoryDataStore newMemoryDataStore() {
        return new MemoryDataStoreWithProvidedFIDSupport();
    }

    public void setTransaction(GeogigTransaction transaction) {
        this.transaction = transaction;
    }

    private Context getContext() {
        if (transaction != null) {
            return transaction;
        }
        return repo.context();
    }

    public TestData init() {
        return init("John Doe", "JohnDoe@example.com");
    }

    public TestData init(final String userName, final String userEmail) {
        repo.command(InitOp.class).call();
        config("user.name", userName).config("user.email", userEmail);
        return this;
    }

    public TestData config(String key, String value) {
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName(key).setValue(value)
                .call();
        return this;
    }

    /**
     * Sets the repository to a default test status.
     * <p>
     * As long as the repository given to this class' constructor is empty, creates the following
     * repository layout:
     *
     * <pre>
     * <code>
     *             (adds Points/2, Lines/2, Polygons/2)
     *    branch1 o-------------------------------------
     *           /                                      \
     *          /                                        \  no ff merge
     *  master o------------------------------------------o-----------------o
     *          \  (initial commit has                                     / no ff merge
     *           \     Points/1, Lines/1, Polygons/1)                     /
     *            \                                                      /
     *             \                                                    /
     *     branch2  o--------------------------------------------------
     *             (adds Points/3, Lines/3, Polygons/3)
     *
     * </code>
     * </pre>
     */
    public TestData loadDefaultData() {
        checkout("master")//
                .addAndCommit("point1, line1, poly1", point1, line1, poly1)//
                .branchAndCheckout("branch1")//
                .addAndCommit("point2, line2, poly2", point2, line2, poly2)//
                .checkout("master")//
                .branchAndCheckout("branch2")//
                .addAndCommit("point3, line3, poly3", point3, line3, poly3)//
                .checkout("master")//
                .mergeNoFF("branch1", "merge branch branch1 onto master")//
                .mergeNoFF("branch2", "merge branch branch2 onto master");

        LOG.debug("HEAD: " + repo.command(RefParse.class).setName(Ref.HEAD).call().get());
        List<NodeRef> treeRefs = Lists
                .newArrayList(getContext().command(LsTreeOp.class).setReference(Ref.HEAD).call());
        checkState(3 == treeRefs.size());
        for (NodeRef r : treeRefs) {
            RevTree tree = getContext().objectDatabase().getTree(r.getObjectId());
            checkState(3 == tree.size());
        }
        return this;
    }

    public TestData mergeNoFF(String branchToMerge, String mergeCommitMessage) {
        ObjectId branchHead = getContext().command(RefParse.class).setName(branchToMerge).call()
                .get().getObjectId();
        MergeReport report = getContext().command(MergeOp.class).setNoFastForward(true)
                .setMessage(mergeCommitMessage).addCommit(branchHead).call();
        RevCommit mergeCommit = report.getMergeCommit();
        checkState(mergeCommit.getParentIds().size() == 2);
        LOG.debug(mergeCommit.toString());
        return this;
    }

    public TestData branchAndCheckout(final String newBranch) {
        return branch(newBranch).checkout(newBranch);
    }

    public TestData branch(String newBranch) {
        Ref ref = getContext().command(BranchCreateOp.class).setName(newBranch).call();
        checkState(newBranch.equals(ref.localName()));
        return this;
    }

    public TestData checkout(String branch) {
        getContext().command(CheckoutOp.class).setSource(branch).call();
        Ref head = getContext().command(RefParse.class).setName(Ref.HEAD).call().get();
        if (head instanceof SymRef) {
            String target = ((SymRef) head).getTarget();
            head = getContext().command(RefParse.class).setName(target).call().get();
        }
        String headBranch = head.localName();
        checkState(branch.equals(headBranch), "expected %s, got %s", branch, headBranch);
        return this;
    }

    public TestData commit(String commitMessage) {
        return commit(commitMessage, false);
    }

    public TestData commitAllowEmpty(String message) {
        return commit(message, true);
    }

    private TestData commit(String commitMessage, boolean allowEmpty) {
        RevCommit commit = getContext().command(CommitOp.class).setAllowEmpty(allowEmpty)
                .setMessage(commitMessage).call();
        LOG.debug(commit.toString());
        return this;
    }

    public TestData addAndCommit(String commitMessage, SimpleFeature... features) {
        return insert(features).add().commit(commitMessage);
    }

    public TestData insert(SimpleFeature... features) {
        WorkingTree workingTree = getContext().workingTree();
        Map<FeatureType, RevFeatureType> types = new HashMap<>();
        for (SimpleFeature sf : features) {
            SimpleFeatureType ft = sf.getType();
            RevFeatureType rft = types.get(ft);
            if (null == rft) {
                rft = RevFeatureTypeBuilder.build(ft);
                types.put(ft, rft);
                getContext().objectDatabase().put(rft);
            }
            String parentTreePath = ft.getName().getLocalPart();
            String path = NodeRef.appendChild(parentTreePath, sf.getID());
            FeatureInfo fi = FeatureInfo.insert(RevFeatureBuilder.build(sf), rft.getId(), path);
            workingTree.insert(fi);
        }
        return this;
    }

    public TestData remove(SimpleFeature... features) {
        WorkingTree workingTree = getContext().workingTree();
        for (SimpleFeature sf : features) {
            String parentTreePath = sf.getType().getName().getLocalPart();
            workingTree.delete(parentTreePath, sf.getID());
        }
        return this;
    }

    public TestData add() {
        getContext().command(AddOp.class).call();
        return this;
    }

    public static <E> List<E> toList(Iterator<E> logs) {
        List<E> logged = new ArrayList<E>();
        Iterators.addAll(logged, logs);
        return logged;
    }

    static SimpleFeature feature(SimpleFeatureType type, String id, Object... values) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    try {
                        value = new WKTReader2().read((String) value);
                    } catch (ParseException e) {
                        Throwables.propagate(e);
                    }
                }
            }
            builder.set(i, value);
        }
        return builder.buildFeature(id);
    }

    public static JsonObject toJSON(String jsonString) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
        return jsonReader.readObject();
    }

    public static JsonArray toJSONArray(String jsonString) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
        return jsonReader.readArray();
    }

    public static boolean jsonEquals(JsonArray expected, JsonArray actual, boolean strict) {
        return compare(expected, actual, strict);
    }

    /**
     * Compare two JSON objects. Extra fields are allowed on the actual object if strict is set to
     * {@code false}. Additionally, array ordering does not matter when strict is set to
     * {@code false}.
     *
     * @param expected expected object
     * @param actual actual object
     * @param strict whether or not to perform a strict comparison
     * @return {@code true} if the objects are equal
     */
    public static boolean jsonEquals(JsonObject expected, JsonObject actual, boolean strict) {
        Iterator<String> expectedKeys = expected.keySet().iterator();
        if (strict && expected.size() != actual.size()) {
            return false;
        }
        while (expectedKeys.hasNext()) {
            String key = expectedKeys.next();
            if (!actual.containsKey(key)) {
                return false;
            }
            JsonValue expectedObject = expected.get(key);
            JsonValue actualObject = actual.get(key);
            if (!compare(expectedObject, actualObject, strict)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compare(JsonValue expected, JsonValue actual, boolean strict) {
        switch (expected.getValueType()) {
        case OBJECT:
            if (actual.getValueType() == ValueType.OBJECT) {
                return jsonEquals((JsonObject) expected, (JsonObject) actual, strict);
            }
            break;
        case ARRAY:
            if (actual.getValueType() == ValueType.ARRAY) {
                JsonArray expectedArray = (JsonArray) expected;
                JsonArray actualArray = (JsonArray) actual;
                if (expectedArray.size() != actualArray.size()) {
                    return false;
                }
                if (strict) {
                    for (int i = 0; i < expectedArray.size(); i++) {
                        if (!compare(expectedArray.get(i), actualArray.get(i), strict)) {
                            return false;
                        }
                    }
                } else {
                    List<JsonValue> expectedSet = new LinkedList<JsonValue>();
                    List<JsonValue> actualSet = new LinkedList<JsonValue>();
                    for (int i = 0; i < expectedArray.size(); i++) {
                        expectedSet.add(expectedArray.get(i));
                        actualSet.add(actualArray.get(i));
                    }
                    Iterator<JsonValue> expectedIter = expectedSet.iterator();
                    while (expectedIter.hasNext()) {
                        boolean found = false;
                        JsonValue expectedObject = expectedIter.next();
                        for (JsonValue actualObject : actualSet) {
                            if (compare(expectedObject, actualObject, strict)) {
                                found = true;
                                actualSet.remove(actualObject);
                                break;
                            }
                        }
                        if (!found) {
                            return false;
                        }
                    }
                }
                return true;
            }
            break;
        case STRING:
            if (actual.getValueType() == ValueType.STRING) {
                return expected.equals(actual);
            }
            break;
        case NUMBER:
            if (actual.getValueType() == ValueType.NUMBER) {
                return toDouble(expected).equals(toDouble(actual));
            }
            break;
        default:
            return expected.equals(actual);
        }

        return false;
    }

    private static Double toDouble(Object input) {
        return ((JsonNumber) input).doubleValue();
    }

}
