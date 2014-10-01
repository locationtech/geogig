/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.referencing.CRS;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.LsRemote;
import org.locationtech.geogig.api.plumbing.SendPack;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.FetchOp;
import org.locationtech.geogig.api.porcelain.PullOp;
import org.locationtech.geogig.api.porcelain.PushOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.test.integration.TestContextBuilder;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.vividsolutions.jts.io.ParseException;

public abstract class RemoteRepositoryTestCase {

    protected static final String idL1 = "Lines.1";

    protected static final String idL2 = "Lines.2";

    protected static final String idL3 = "Lines.3";

    protected static final String idP1 = "Points.1";

    protected static final String idP2 = "Points.2";

    protected static final String idP3 = "Points.3";

    protected static final String pointsNs = "http://geogig.points";

    protected static final String pointsName = "Points";

    protected static final String pointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326";

    protected static final Name pointsTypeName = new NameImpl("http://geogig.points", pointsName);

    protected SimpleFeatureType pointsType;

    protected Feature points1;

    protected Feature points1_modified;

    protected Feature points2;

    protected Feature points3;

    protected static final String linesNs = "http://geogig.lines";

    protected static final String linesName = "Lines";

    protected static final String linesTypeSpec = "sp:String,ip:Integer,pp:LineString:srid=4326";

    protected static final Name linesTypeName = new NameImpl("http://geogig.lines", linesName);

    protected SimpleFeatureType linesType;

    protected Feature lines1;

    protected Feature lines2;

    protected Feature lines3;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    protected class GeogigContainer {
        public GeoGIG geogig;

        public Repository repo;

        public File envHome;

        public Context injector;

        public GeogigContainer(final String workingDirectory) throws IOException {

            envHome = tempFolder.newFolder(workingDirectory);

            ContextBuilder injectorBuilder = createInjectorBuilder();
            GlobalContextBuilder.builder = injectorBuilder;
            injector = injectorBuilder.build();

            geogig = new GeoGIG(injector, envHome);
            repo = geogig.getOrCreateRepository();

            repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                    .setValue("Gabriel Roldan").call();
            repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                    .setValue("groldan@boundlessgeo.com").call();
        }

        public void tearDown() throws IOException {
            if (repo != null) {
                repo.close();
            }
            repo = null;
            injector = null;
        }

        public Context getInjector() {
            return injector;
        }

        private ContextBuilder createInjectorBuilder() {
            Platform testPlatform = new TestPlatform(envHome){
                @Override
                public long currentTimeMillis(){
                    return 1000;
                }
            };
            return new TestContextBuilder(testPlatform);
        }
    }

    public GeogigContainer localGeogig;

    public GeogigContainer remoteGeogig;

    public IRemoteRepo remoteRepo;

    // prevent recursion
    private boolean setup = false;

    @Before
    public final void setUp() throws Exception {
        if (setup) {
            throw new IllegalStateException("Are you calling super.setUp()!?");
        }

        setup = true;
        doSetUp();
    }

    protected final void doSetUp() throws IOException, SchemaException, ParseException, Exception {
        localGeogig = new GeogigContainer("localtestrepository");
        remoteGeogig = new GeogigContainer("remotetestrepository");

        LocalRemoteRepo remoteRepo = spy(new LocalRemoteRepo(remoteGeogig.getInjector(),
                remoteGeogig.envHome.getCanonicalFile(), localGeogig.repo));

        doNothing().when(remoteRepo).close();

        remoteRepo.setGeoGig(remoteGeogig.geogig);
        this.remoteRepo = remoteRepo;

        pointsType = DataUtilities.createType(pointsNs, pointsName, pointsTypeSpec);

        points1 = feature(pointsType, idP1, "StringProp1_1", new Integer(1000), "POINT(1 1)");
        points1_modified = feature(pointsType, idP1, "StringProp1_1a", new Integer(1001),
                "POINT(1 2)");
        points2 = feature(pointsType, idP2, "StringProp1_2", new Integer(2000), "POINT(2 2)");
        points3 = feature(pointsType, idP3, "StringProp1_3", new Integer(3000), "POINT(3 3)");

        linesType = DataUtilities.createType(linesNs, linesName, linesTypeSpec);

        lines1 = feature(linesType, idL1, "StringProp2_1", new Integer(1000),
                "LINESTRING (1 1, 2 2)");
        lines2 = feature(linesType, idL2, "StringProp2_2", new Integer(2000),
                "LINESTRING (3 3, 4 4)");
        lines3 = feature(linesType, idL3, "StringProp2_3", new Integer(3000),
                "LINESTRING (5 5, 6 6)");

        setUpInternal();
    }

    protected LsRemote lsremote() {
        LsRemote lsRemote = spy(localGeogig.geogig.command(LsRemote.class));

        doReturn(Optional.of(remoteRepo)).when(lsRemote).getRemoteRepo(any(Remote.class));

        return lsRemote;
    }

    protected FetchOp fetch() {
        FetchOp remoteRepoFetch = spy(localGeogig.geogig.command(FetchOp.class));

        doReturn(Optional.of(remoteRepo)).when(remoteRepoFetch).getRemoteRepo(any(Remote.class),
                any(DeduplicationService.class));
        LsRemote lsRemote = lsremote();
        doReturn(lsRemote).when(remoteRepoFetch).command(eq(LsRemote.class));

        return remoteRepoFetch;
    }

    protected CloneOp clone() {
        CloneOp clone = spy(localGeogig.geogig.command(CloneOp.class));
        FetchOp fetch = fetch();
        // when(clone.command(FetchOp.class)).thenReturn(fetch);
        doReturn(fetch).when(clone).command(eq(FetchOp.class));

        LsRemote lsRemote = lsremote();
        // when(clone.command(LsRemote.class)).thenReturn(lsRemote);
        doReturn(lsRemote).when(clone).command(eq(LsRemote.class));

        return clone;
    }

    protected PullOp pull() {
        PullOp pull = spy(localGeogig.geogig.command(PullOp.class));
        FetchOp fetch = fetch();
        // when(pull.command(eq(FetchOp.class))).thenReturn(fetch);
        doReturn(fetch).when(pull).command(eq(FetchOp.class));

        LsRemote lsRemote = lsremote();
        // when(pull.command(eq(LsRemote.class))).thenReturn(lsRemote);
        doReturn(lsRemote).when(pull).command(eq(LsRemote.class));

        return pull;
    }

    protected PushOp push() {
        SendPack sendPack = spy(localGeogig.geogig.command(SendPack.class));
        doReturn(Optional.of(remoteRepo)).when(sendPack).getRemoteRepo(any(Remote.class));

        PushOp push = spy(localGeogig.geogig.command(PushOp.class));
        doReturn(sendPack).when(push).command(eq(SendPack.class));

        FetchOp fetch = fetch();
        // when(push.command(FetchOp.class)).thenReturn(fetch);
        doReturn(fetch).when(push).command(eq(FetchOp.class));

        LsRemote lsRemote = lsremote();
        // when(push.command(LsRemote.class)).thenReturn(lsRemote);
        doReturn(lsRemote).when(push).command(eq(LsRemote.class));

        return push;
    }

    @After
    public final void tearDown() throws Exception {
        setup = false;
        tearDownInternal();
        localGeogig.tearDown();
        remoteGeogig.tearDown();
        localGeogig = null;
        remoteGeogig = null;
        System.gc();
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

    protected Feature feature(SimpleFeatureType type, String id, Object... values)
            throws ParseException {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    value = new WKTReader2().read((String) value);
                }
            }
            builder.set(i, value);
        }
        return builder.buildFeature(id);
    }

    protected List<RevCommit> populate(GeoGIG geogig, boolean oneCommitPerFeature,
            Feature... features) throws Exception {
        return populate(geogig, oneCommitPerFeature, Arrays.asList(features));
    }

    protected List<RevCommit> populate(GeoGIG geogig, boolean oneCommitPerFeature,
            List<Feature> features) throws Exception {

        List<RevCommit> commits = new ArrayList<RevCommit>();

        for (Feature f : features) {
            insertAndAdd(geogig, f);
            if (oneCommitPerFeature) {
                RevCommit commit = geogig.command(CommitOp.class).call();
                commits.add(commit);
            }
        }

        if (!oneCommitPerFeature) {
            RevCommit commit = geogig.command(CommitOp.class).call();
            commits.add(commit);
        }

        return commits;
    }

    /**
     * Inserts the Feature to the index and stages it to be committed.
     */
    protected ObjectId insertAndAdd(GeoGIG geogig, Feature f) throws Exception {
        ObjectId objectId = insert(geogig, f);

        geogig.command(AddOp.class).call();
        return objectId;
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    protected ObjectId insert(GeoGIG geogig, Feature f) throws Exception {
        final WorkingTree workTree = geogig.getRepository().workingTree();
        Name name = f.getType().getName();
        String parentPath = name.getLocalPart();
        Node ref = workTree.insert(parentPath, f);
        ObjectId objectId = ref.getObjectId();
        return objectId;
    }

    protected void insertAndAdd(GeoGIG geogig, Feature... features) throws Exception {
        insert(geogig, features);
        geogig.command(AddOp.class).call();
    }

    protected void insert(GeoGIG geogig, Feature... features) throws Exception {
        for (Feature f : features) {
            insert(geogig, f);
        }
    }

    /**
     * Deletes a feature from the index
     * 
     * @param f
     * @return
     * @throws Exception
     */
    protected boolean deleteAndAdd(GeoGIG geogig, Feature f) throws Exception {
        boolean existed = delete(geogig, f);
        if (existed) {
            geogig.command(AddOp.class).call();
        }

        return existed;
    }

    protected boolean delete(GeoGIG geogig, Feature f) throws Exception {
        final WorkingTree workTree = geogig.getRepository().workingTree();
        Name name = f.getType().getName();
        String localPart = name.getLocalPart();
        String id = f.getIdentifier().getID();
        boolean existed = workTree.delete(localPart, id);
        return existed;
    }

    protected <E> List<E> toList(Iterator<E> logs) {
        List<E> logged = new ArrayList<E>();
        Iterators.addAll(logged, logs);
        return logged;
    }

    protected <E> List<E> toList(Iterable<E> logs) {
        List<E> logged = new ArrayList<E>();
        Iterables.addAll(logged, logs);
        return logged;
    }

    /**
     * Computes the aggregated bounds of {@code features}, assuming all of them are in the same CRS
     */
    protected ReferencedEnvelope boundsOf(Feature... features) {
        ReferencedEnvelope bounds = null;
        for (int i = 0; i < features.length; i++) {
            Feature f = features[i];
            if (bounds == null) {
                bounds = (ReferencedEnvelope) f.getBounds();
            } else {
                bounds.include(f.getBounds());
            }
        }
        return bounds;
    }

    /**
     * Computes the aggregated bounds of {@code features} in the {@code targetCrs}
     */
    protected ReferencedEnvelope boundsOf(CoordinateReferenceSystem targetCrs, Feature... features)
            throws Exception {
        ReferencedEnvelope bounds = new ReferencedEnvelope(targetCrs);

        for (int i = 0; i < features.length; i++) {
            Feature f = features[i];
            BoundingBox fbounds = f.getBounds();
            if (!CRS.equalsIgnoreMetadata(targetCrs, fbounds)) {
                fbounds = fbounds.toBounds(targetCrs);
            }
            bounds.include(fbounds);
        }
        return bounds;
    }
}
