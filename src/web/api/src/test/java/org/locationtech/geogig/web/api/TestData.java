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

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.vividsolutions.jts.io.ParseException;

/**
 * A helper class to set repositories to a desired state to aid in integration testing.
 *
 */
public class TestData {

    public static final String pointsTypeSpec = "sp:String,ip:Integer,geom:Point:srid=4326";

    public static final String linesTypeSpec = "sp:String,ip:Integer,geom:LineString:srid=4326";

    public static final String polyTypeSpec = "sp:String,ip:Integer,pp:Polygon:srid=4326";

    public static final SimpleFeatureType pointsType, linesType, polysType;

    public static final SimpleFeature points1, points2, points3;

    public static final SimpleFeature lines1, lines2, lines3;

    public static final SimpleFeature poly1, poly2, poly3;

    static {
        try {
            pointsType = DataUtilities.createType("http://geogig.org", "Points", pointsTypeSpec);
            linesType = DataUtilities.createType("http://geogig.org", "Lines", linesTypeSpec);
            polysType = DataUtilities.createType("http://geogig.org", "Polygons", polyTypeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }

        points1 = feature(pointsType, "Points.1", "StringProp1_1", 1000, "POINT(1 1)");
        points2 = feature(pointsType, "Points.2", "StringProp1_2", 2000, "POINT(2 2)");
        points3 = feature(pointsType, "Points.3", "StringProp1_3", 3000, "POINT(3 3)");

        lines1 = feature(linesType, "Lines.1", "StringProp2_1", 1000, "LINESTRING (1 1, 2 2)");
        lines2 = feature(linesType, "Lines.2", "StringProp2_2", 2000, "LINESTRING (3 3, 4 4)");
        lines3 = feature(linesType, "Lines.3", "StringProp2_3", 3000, "LINESTRING (5 5, 6 6)");

        poly1 = feature(polysType, "Polygons.1", "StringProp3_1", 1000,
                "POLYGON ((1 1, 2 2, 3 3, 4 4, 1 1))");
        poly2 = feature(polysType, "Polygons.2", "StringProp3_2", 2000,
                "POLYGON ((6 6, 7 7, 8 8, 9 9, 6 6))");
        poly3 = feature(polysType, "Polygons.3", "StringProp3_3", 3000,
                "POLYGON ((11 11, 12 12, 13 13, 14 14, 11 11))");

    }

    private GeoGIG repo;

    public TestData(final GeoGIG repo) throws Exception {
        this.repo = repo;
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
                .addAndCommit("points1, lines1, poly1", points1, lines1, poly1)//
                .branchAndCheckout("branch1")//
                .addAndCommit("points2, lines2, poly2", points2, lines2, poly2)//
                .checkout("master")//
                .branchAndCheckout("branch2")//
                .addAndCommit("points2, lines2, poly2", points3, lines3, poly3)//
                .checkout("master")//
                .mergeNoFF("branch1", "merge branch branch1 onto master")//
                .mergeNoFF("branch2", "merge branch branch2 onto master");
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
        RevCommit commit = repo.command(CommitOp.class).setAllowEmpty(false).call();
        return this;
    }

    public TestData commitAllowEmpty(String message) {
        repo.command(CommitOp.class).setAllowEmpty(true).call();
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

}
