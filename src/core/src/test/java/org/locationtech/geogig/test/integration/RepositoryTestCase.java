/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CheckoutResult;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.FeatureToDelete;
import org.locationtech.geogig.test.TestRepository;
import org.locationtech.geogig.transaction.GeogigTransaction;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public abstract class RepositoryTestCase {

    public static final String idL1 = "Lines.1";

    public static final String idL2 = "Lines.2";

    public static final String idL3 = "Lines.3";

    public static final String idP1 = "Points.1";

    public static final String idP2 = "Points.2";

    public static final String idP3 = "Points.3";

    public static final String idPG1 = "Polygon.1";

    public static final String idPG2 = "Polygon.2";

    public static final String idPG3 = "Polygon.3";

    public static final String pointsNs = "http://geogig.points";

    public static final String pointsName = "Points";

    public static final String pointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326";

    protected static final String modifiedPointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326,extra:String";

    public static final Name pointsTypeName = Name.valueOf("http://geogig.points", pointsName);

    protected FeatureType pointsType;

    protected FeatureType modifiedPointsType;

    protected Feature points1;

    protected Feature points1_modified;

    protected Feature points1B;

    protected Feature points1B_modified;

    protected Feature points2;

    protected Feature points3;

    protected Feature points2_modified;

    protected Feature points3_modified;

    public static final String linesNs = "http://geogig.lines";

    public static final String linesName = "Lines";

    public static final String linesTypeSpec = "sp:String,ip:Integer,pp:LineString:srid=4326";

    public static final Name linesTypeName = Name.valueOf("http://geogig.lines", linesName);

    public FeatureType linesType;

    public Feature lines1;

    Feature lines1_modified;

    public Feature lines2;

    public Feature lines3;

    public static final String polyNs = "http://geogig.polygon";

    public static final String polyName = "Polygon";

    public static final String polyTypeSpec = "sp:String,ip:Integer,pp:Polygon:srid=4326";

    public static final Name polyTypeName = Name.valueOf("http://geogig.polygon", polyName);

    public FeatureType polyType;

    public Feature poly1;

    public Feature poly2;

    public Feature poly3;

    protected Repository repo;

    public @Rule TestRepository testRepository = new TestRepository();

    // prevent recursion
    private boolean setup = false;

    @Before
    public final void setUp() throws Exception {
        if (setup) {
            throw new IllegalStateException("Are you calling super.setUp()!?");
        }
        setup = true;
        beforeSetup();
        doSetUp();
    }

    /**
     * To be overridden if some subclass needs to set up some state before {@link #doSetUp()}
     */
    protected void beforeSetup() {
        //
    }

    @After
    public final void tearDown() throws Exception {
        setup = false;
        tearDownInternal();
        TestRepository.closeAndDelete(repo);
    }

    protected final void doSetUp() throws IOException, ParseException, Exception {
        repo = testRepository.repository();
        assertTrue(repo.isOpen());
        assertNotNull(repo.context().configDatabase());
        assertNotNull(repo.context().objectDatabase());
        assertNotNull(repo.context().graphDatabase());
        assertNotNull(repo.context().conflictsDatabase());
        assertNotNull(repo.context().blobStore());

        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("Gabriel Roldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@test.com").call();

        pointsType = FeatureTypes.createType(pointsNs + "#" + pointsName,
                pointsTypeSpec.split(","));
        modifiedPointsType = FeatureTypes.createType(pointsNs + "#" + pointsName,
                modifiedPointsTypeSpec.split(","));

        points1 = feature(pointsType, idP1, "StringProp1_1", Integer.valueOf(1000), "POINT(1 1)");
        points1_modified = feature(pointsType, idP1, "StringProp1_1a", Integer.valueOf(1001),
                "POINT(1 2)");
        points1B = feature(modifiedPointsType, idP1, "StringProp1_1", Integer.valueOf(1000),
                "POINT(1 1)", "ExtraString");

        points1B_modified = feature(modifiedPointsType, idP1, "StringProp1_1a",
                Integer.valueOf(1001), "POINT(1 2)", "ExtraStringB");

        points2 = feature(pointsType, idP2, "StringProp1_2", Integer.valueOf(2000), "POINT(2 2)");
        points3 = feature(pointsType, idP3, "StringProp1_3", Integer.valueOf(3000), "POINT(3 3)");

        linesType = FeatureTypes.createType(linesNs + "#" + linesName, linesTypeSpec.split(","));

        lines1 = feature(linesType, idL1, "StringProp2_1", Integer.valueOf(1000),
                "LINESTRING (1 1, 2 2)");
        lines2 = feature(linesType, idL2, "StringProp2_2", Integer.valueOf(2000),
                "LINESTRING (3 3, 4 4)");
        lines3 = feature(linesType, idL3, "StringProp2_3", Integer.valueOf(3000),
                "LINESTRING (5 5, 6 6)");

        polyType = FeatureTypes.createType(polyNs + "#" + polyName, polyTypeSpec.split(","));

        poly1 = feature(polyType, idPG1, "StringProp3_1", Integer.valueOf(1000),
                "POLYGON ((1 1, 2 2, 3 3, 4 4, 1 1))");
        poly2 = feature(polyType, idPG2, "StringProp3_2", Integer.valueOf(2000),
                "POLYGON ((6 6, 7 7, 8 8, 9 9, 6 6))");
        poly3 = feature(polyType, idPG3, "StringProp3_3", Integer.valueOf(3000),
                "POLYGON ((11 11, 12 12, 13 13, 14 14, 11 11))");

        points2_modified = feature(pointsType, idP2, "StringProp1_2a", Integer.valueOf(2001),
                "POINT(2 3)");

        points3_modified = feature(pointsType, idP3, "StringProp1_3a", Integer.valueOf(3001),
                "POINT(3 4)");

        lines1_modified = feature(linesType, idL1, "StringProp2_1a", Integer.valueOf(1001),
                "LINESTRING (1 2, 2 2)");

        setUpInternal();
    }

    /**
     * Called as the last step in {@link #setUp()}
     */
    protected abstract void setUpInternal() throws Exception;

    /**
     * Called before {@link #tearDown()}, subclasses may override as appropriate
     */
    protected void tearDownInternal() throws Exception {
        //
    }

    public Repository getRepository() {
        return repo;
    }

    public FeatureInfo featureInfo(Feature f) {
        FeatureType type = f.getType();
        String treePath = type.getName().getLocalPart();
        return featureInfo(treePath, f);
    }

    public FeatureInfo featureInfo(String treePath, Feature f) {
        final String path = NodeRef.appendChild(treePath, f.getId());
        RevFeature feature = RevFeature.builder().build(f);
        FeatureType type = f.getType();
        RevFeatureType ftype = RevFeatureType.builder().type(type).build();
        repo.context().objectDatabase().put(ftype);
        return FeatureInfo.insert(feature, ftype.getId(), path);
    }

    protected FeatureInfo featureInfo(FeatureType type, String id, Object... values) {
        RevFeature feature = RevFeature.builder().build(feature(type, id, values));
        RevFeatureType ftype = RevFeatureType.builder().type(type).build();
        repo.context().objectDatabase().put(ftype);
        String path = NodeRef.appendChild(type.getName().getLocalPart(), id);
        return FeatureInfo.insert(feature, ftype.getId(), path);
    }

    protected Iterator<FeatureInfo> asFeatureInfos(Iterator<Feature> features) {
        return Iterators.transform(features, (f) -> featureInfo(f));
    }

    protected Feature feature(FeatureType type, String id, Object... values) {
        Feature f = Feature.build(id, type);
        f.setId(id);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i).isGeometryDescriptor()) {
                if (value instanceof String) {
                    value = geom((String) value);
                }
            }
            f.setAttribute(i, value);
        }
        return f;
    }

    protected Geometry geom(String wkt) {
        try {
            return new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<RevCommit> populate(boolean oneCommitPerFeature, Feature... features)
            throws Exception {
        return populate(oneCommitPerFeature, Arrays.asList(features));
    }

    protected List<RevCommit> populate(boolean oneCommitPerFeature, List<Feature> features)
            throws Exception {

        List<RevCommit> commits = new ArrayList<RevCommit>();

        for (Feature f : features) {
            insertAndAdd(f);
            if (oneCommitPerFeature) {
                commits.add(commit(f.getId()));
            }
        }

        if (!oneCommitPerFeature) {
            String msg = features.stream().map(Feature::getId).collect(Collectors.joining(","));
            commits.add(commit(msg));
        }

        return commits;
    }

    protected RevCommit commit(String message) {
        return commit(repo.context(), message);
    }

    protected RevCommit commit(Context context, String message) {
        RevCommit commit = context.command(CommitOp.class).setMessage(message).call();
        return commit;
    }

    /**
     * Inserts the Feature to the index and stages it to be committed.
     */
    public ObjectId insertAndAdd(Feature f) throws Exception {
        return insertAndAdd(null, f);
    }

    /**
     * Inserts the Feature to the index and stages it to be committed.
     */
    public ObjectId insertAndAdd(GeogigTransaction transaction, Feature f) throws Exception {
        ObjectId objectId = insert(transaction, f);

        if (transaction != null) {
            transaction.command(AddOp.class).call();
        } else {
            repo.command(AddOp.class).call();
        }
        return objectId;
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    public ObjectId insert(Feature f) throws Exception {
        return insert(null, f);
    }

    public List<FeatureInfo> insert(List<Feature> features) {
        Map<FeatureType, RevFeatureType> types = new HashMap<>();

        List<FeatureInfo> rfIds = new ArrayList<>();
        List<FeatureInfo> infos = Lists.transform(features, (f) -> {
            final String path = NodeRef.appendChild(f.getType().getName().getLocalPart(),
                    f.getId());
            FeatureInfo fi;
            if (f instanceof FeatureToDelete) {
                fi = FeatureInfo.delete(path);
            } else {
                RevFeatureType rft = types.get(f.getType());
                if (rft == null) {
                    rft = RevFeatureType.builder().type(f.getType()).build();
                    repo.context().objectDatabase().put(rft);
                    types.put(f.getType(), rft);
                }
                fi = FeatureInfo.insert(RevFeature.builder().build(f), rft.getId(), path);
                rfIds.add(fi);
            }
            return fi;
        });
        repo.context().workingTree().insert(infos.iterator(), DefaultProgressListener.NULL);
        return rfIds;
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    public ObjectId insert(GeogigTransaction transaction, Feature f) throws Exception {
        final WorkingTree workTree = (transaction != null ? transaction.workingTree()
                : repo.context().workingTree());

        FeatureInfo feature = featureInfo(f);
        workTree.insert(feature);
        ObjectId objectId = feature.getFeature().getId();
        return objectId;
    }

    public void insertAndAdd(Feature... features) throws Exception {
        insertAndAdd(null, features);
    }

    public void insertAndAdd(GeogigTransaction transaction, Feature... features) throws Exception {
        insert(transaction, features);
        repo.command(AddOp.class).call();
    }

    public void insert(Feature... features) throws Exception {
        insert(null, features);
    }

    public void insert(GeogigTransaction transaction, Feature... features) throws Exception {
        for (Feature f : features) {
            insert(transaction, f);
        }
    }

    /**
     * Deletes a feature from the index
     * 
     * @param f
     * @return
     * @throws Exception
     */
    public boolean deleteAndAdd(Feature f) throws Exception {
        return deleteAndAdd(null, f);
    }

    /**
     * Deletes a feature from the index
     * 
     * @param f
     * @return
     * @throws Exception
     */
    public boolean deleteAndAdd(@Nullable GeogigTransaction transaction, Feature f)
            throws Exception {
        boolean existed = delete(transaction, f);
        if (existed) {
            if (transaction != null) {
                transaction.command(AddOp.class).call();
            } else {
                repo.command(AddOp.class).call();
            }
        }

        return existed;
    }

    public boolean delete(Feature f) throws Exception {
        return delete(null, f);
    }

    public boolean delete(@Nullable GeogigTransaction transaction, Feature f) throws Exception {
        final WorkingTree workTree = (transaction != null ? transaction.workingTree()
                : repo.context().workingTree());
        Name name = f.getType().getName();
        String localPart = name.getLocalPart();
        String id = f.getId();
        boolean existed = workTree.delete(localPart, id);
        return existed;
    }

    public <E> List<E> toList(Iterator<E> logs) {
        List<E> logged = new ArrayList<E>();
        Iterators.addAll(logged, logs);
        return logged;
    }

    public <E> List<E> toList(Iterable<E> logs) {
        List<E> logged = new ArrayList<E>();
        Iterables.addAll(logged, logs);
        return logged;
    }

    /**
     * Computes the aggregated bounds of {@code features}, assuming all of them are in the same CRS
     */
    public Envelope boundsOf(Feature... features) {
        Envelope bounds = null;
        for (int i = 0; i < features.length; i++) {
            Feature f = features[i];
            if (bounds == null) {
                bounds = f.getDefaultGeometryBounds();
            } else {
                bounds.expandToInclude(f.getDefaultGeometryBounds());
            }
        }
        return bounds;
    }

    public static Map<String, String> asMap(String... kvp) {
        Preconditions.checkArgument(kvp.length % 2 == 0, "An even number of arguments is expected");
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kvp.length; i += 2) {
            map.put(kvp[i], kvp[i + 1]);
        }
        return map;
    }

    public void add() {
        repo.command(AddOp.class).call();
    }

    protected CheckoutResult checkout(String branchName) {
        return checkout(repo.context(), branchName);
    }

    protected CheckoutResult checkout(Context context, String branchName) {
        return context.command(CheckoutOp.class).setSource(branchName).call();
    }

    protected Ref branch(String branchName) {
        return repo.command(BranchCreateOp.class).setName(branchName).call();
    }

}
