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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.geotools.data.DataSourceException;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.memory.MemoryFeatureStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.ContentState;
import org.geotools.factory.Hints;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.io.ParseException;

/**
 * A helper class to set repositories to a desired state to aid in integration testing.
 *
 */
public class TestData {

    private static final Logger LOG = LoggerFactory.getLogger(TestData.class);

    public static final String pointsTypeSpec = "sp:String,ip:Integer,geom:Point:srid=4326";

    public static final String linesTypeSpec = "sp:String,ip:Integer,geom:LineString:srid=4326";

    public static final String polyTypeSpec = "sp:String,ip:Integer,pp:Polygon:srid=4326";

    public static final SimpleFeatureType pointsType, linesType, polysType;

    public static final SimpleFeature point1, point2, point3;

    public static final SimpleFeature line1, line2, line3;

    public static final SimpleFeature poly1, poly2, poly3;

    static {
        try {
            pointsType = DataUtilities.createType("http://geogig.org", "Points", pointsTypeSpec);
            linesType = DataUtilities.createType("http://geogig.org", "Lines", linesTypeSpec);
            polysType = DataUtilities.createType("http://geogig.org", "Polygons", polyTypeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }

        point1 = feature(pointsType, "Points.1", "StringProp1_1", 1000, "POINT(0 0)");
        point2 = feature(pointsType, "Points.2", "StringProp1_2", 2000, "POINT(-10 -10)");
        point3 = feature(pointsType, "Points.3", "StringProp1_3", 3000, "POINT(10 10)");

        line1 = feature(linesType, "Lines.1", "StringProp2_1", 1000, "LINESTRING (-1 -1, 1 1)");
        line2 = feature(linesType, "Lines.2", "StringProp2_2", 2000, "LINESTRING (-11 -11, -9 -9)");
        line3 = feature(linesType, "Lines.3", "StringProp2_3", 3000, "LINESTRING (9 9, 11 11)");

        poly1 = feature(polysType, "Polygons.1", "StringProp3_1", 1000,
                "POLYGON ((-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
        poly2 = feature(polysType, "Polygons.2", "StringProp3_2", 2000,
                "POLYGON ((-11 -11, -11 -9, -9 -9, -9 -11, -11 -11))");
        poly3 = feature(polysType, "Polygons.3", "StringProp3_3", 3000,
                "POLYGON ((9 9, 9 11, 11 11, 11 9, 9 9))");

    }

    private GeoGIG repo;

    public TestData(final GeoGIG repo) throws Exception {
        this.repo = repo;
    }

    public static MemoryDataStore newMemoryDataStore() {
        return new MemodyDataStoreWithProvidedFIDSupport();
    }

    public TestData init() {
        return init("John Doe", "JohnDoe@example.com");
    }

    public TestData init(final String userName, final String userEmail) {
        repo.command(InitOp.class);
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue(userName).call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue(userEmail).call();
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
     *             (adds Points.2, Lines.2, Polygons.2)
     *    branch1 o-------------------------------------
     *           /                                      \
     *          /                                        \  no ff merge
     *  master o------------------------------------------o-----------------o
     *          \  (initial commit has                                     / no ff merge
     *           \     Points.1, Lines.1, Polygons.1)                     /
     *            \                                                      /  
     *             \                                                    /
     *     branch2  o--------------------------------------------------
     *             (adds Points.3, Lines.3, Polygons.3)
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

        LOG.info("HEAD: " + repo.command(RefParse.class).setName(Ref.HEAD).call().get());
        List<NodeRef> treeRefs = Lists.newArrayList(repo.command(LsTreeOp.class)
                .setReference(Ref.HEAD).call());
        checkState(3 == treeRefs.size());
        for (NodeRef r : treeRefs) {
            RevTree tree = repo.getRepository().objectDatabase().getTree(r.getObjectId());
            checkState(3 == tree.size());
        }
        return this;
    }

    public TestData mergeNoFF(String branchToMerge, String mergeCommitMessage) {
        ObjectId branchHead = repo.command(RefParse.class).setName(branchToMerge).call().get()
                .getObjectId();
        Supplier<ObjectId> commit = Suppliers.ofInstance(branchHead);
        MergeReport report = repo.command(MergeOp.class).setNoFastForward(true)
                .setMessage(mergeCommitMessage).addCommit(commit).call();
        RevCommit mergeCommit = report.getMergeCommit();
        checkState(mergeCommit.getParentIds().size() == 2);
        LOG.info(mergeCommit.toString());
        return this;
    }

    public TestData branchAndCheckout(final String newBranch) {
        return branch(newBranch).checkout(newBranch);
    }

    public TestData branch(String newBranch) {
        Ref ref = repo.command(BranchCreateOp.class).setName(newBranch).call();
        checkState(newBranch.equals(ref.localName()));
        return this;
    }

    public TestData checkout(String branch) {
        repo.command(CheckoutOp.class).setSource(branch).call();
        Ref head = repo.command(RefParse.class).setName(Ref.HEAD).call().get();
        if (head instanceof SymRef) {
            String target = ((SymRef) head).getTarget();
            head = repo.command(RefParse.class).setName(target).call().get();
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
        RevCommit commit = repo.command(CommitOp.class).setAllowEmpty(allowEmpty)
                .setMessage(commitMessage).call();
        LOG.info(commit.toString());
        return this;
    }

    public TestData addAndCommit(String commitMessage, SimpleFeature... features) {
        return insert(features).add().commit(commitMessage);
    }

    public TestData insert(SimpleFeature... features) {
        WorkingTree workingTree = repo.getRepository().workingTree();
        for (SimpleFeature sf : features) {
            String parentTreePath = sf.getType().getName().getLocalPart();
            workingTree.insert(parentTreePath, sf);
        }
        return this;
    }

    public TestData add() {
        repo.command(AddOp.class).call();
        return this;
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

    /**
     * GeoTools' MemoryDataStore does not support {@code Hints.USE_PROVIDED_FID} at the time of
     * writing, hence this subclass decorates it to support it.
     *
     */
    private static class MemodyDataStoreWithProvidedFIDSupport extends MemoryDataStore {

        @Override
        public Map<String, SimpleFeature> features(String typeName) throws IOException {
            return super.features(typeName);
        }

        @Override
        protected ContentFeatureSource createFeatureSource(ContentEntry entry, Query query) {

            return new MemoryFeatureStore(entry, query) {
                @Override
                protected QueryCapabilities buildQueryCapabilities() {
                    return new QueryCapabilities() {
                        @Override
                        public boolean isUseProvidedFIDSupported() {
                            return true;
                        }
                    };
                }

                @Override
                protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(
                        Query query, int flags) throws IOException {
                    return new MemoryFeatureWriterWithProvidedFIDSupport(getState(), query);
                }

            };

        }

        private static class MemoryFeatureWriterWithProvidedFIDSupport implements
                FeatureWriter<SimpleFeatureType, SimpleFeature> {
            ContentState state;

            SimpleFeatureType featureType;

            Map<String, SimpleFeature> contents;

            Iterator<SimpleFeature> iterator;

            SimpleFeature live = null;

            SimpleFeature current = null; // current Feature returned to user

            public MemoryFeatureWriterWithProvidedFIDSupport(ContentState state, Query query)
                    throws IOException {
                this.state = state;
                featureType = state.getFeatureType();
                String typeName = featureType.getTypeName();
                MemodyDataStoreWithProvidedFIDSupport store = (MemodyDataStoreWithProvidedFIDSupport) state
                        .getEntry().getDataStore();
                contents = store.features(typeName);
                iterator = contents.values().iterator();

            }

            public SimpleFeatureType getFeatureType() {
                return featureType;
            }

            public SimpleFeature next() throws IOException, NoSuchElementException {
                if (hasNext()) {
                    // existing content
                    live = iterator.next();

                    try {
                        current = SimpleFeatureBuilder.copy(live);
                    } catch (IllegalAttributeException e) {
                        throw new DataSourceException("Unable to edit " + live.getID() + " of "
                                + featureType.getTypeName());
                    }
                } else {
                    // new content
                    live = null;

                    try {
                        current = SimpleFeatureBuilder.template(featureType, null);
                    } catch (IllegalAttributeException e) {
                        throw new DataSourceException("Unable to add additional Features of "
                                + featureType.getTypeName());
                    }
                }

                return current;
            }

            public void remove() throws IOException {
                if (contents == null) {
                    throw new IOException("FeatureWriter has been closed");
                }

                if (current == null) {
                    throw new IOException("No feature available to remove");
                }

                if (live != null) {
                    // remove existing content
                    iterator.remove();
                    live = null;
                    current = null;
                } else {
                    // cancel add new content
                    current = null;
                }
            }

            public void write() throws IOException {
                if (contents == null) {
                    throw new IOException("FeatureWriter has been closed");
                }

                if (current == null) {
                    throw new IOException("No feature available to write");
                }

                if (live != null) {
                    if (live.equals(current)) {
                        // no modifications made to current
                        //
                        live = null;
                        current = null;
                    } else {
                        // accept modifications
                        //
                        try {
                            live.setAttributes(current.getAttributes());
                        } catch (Exception e) {
                            throw new DataSourceException("Unable to accept modifications to "
                                    + live.getID() + " on " + featureType.getTypeName());
                        }

                        ReferencedEnvelope bounds = new ReferencedEnvelope();
                        bounds.expandToInclude(new ReferencedEnvelope(live.getBounds()));
                        bounds.expandToInclude(new ReferencedEnvelope(current.getBounds()));
                        live = null;
                        current = null;
                    }
                } else {
                    // add new content
                    String fid = current.getID();
                    if (Boolean.TRUE.equals(current.getUserData().get(Hints.USE_PROVIDED_FID))) {
                        if (current.getUserData().containsKey(Hints.PROVIDED_FID)) {
                            fid = (String) current.getUserData().get(Hints.PROVIDED_FID);
                            Map<Object, Object> userData = current.getUserData();
                            current = SimpleFeatureBuilder.build(current.getFeatureType(),
                                    current.getAttributes(), fid);
                            current.getUserData().putAll(userData);
                        }
                    }
                    contents.put(fid, current);
                    current = null;
                }
            }

            public boolean hasNext() throws IOException {
                if (contents == null) {
                    throw new IOException("FeatureWriter has been closed");
                }

                return (iterator != null) && iterator.hasNext();
            }

            public void close() {
                if (iterator != null) {
                    iterator = null;
                }

                if (featureType != null) {
                    featureType = null;
                }

                contents = null;
                current = null;
                live = null;
            }
        }

    }

}
