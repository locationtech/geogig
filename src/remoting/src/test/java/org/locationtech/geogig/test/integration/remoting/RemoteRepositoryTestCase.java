/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static com.google.common.base.Optional.fromNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
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
import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.remotes.LsRemoteOp;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PushOp;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.LocalRemoteResolver;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.ContextBuilder;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.integration.TestContextBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public abstract class RemoteRepositoryTestCase {

    protected static final ProgressListener SIMPLE_PROGRESS = new DefaultProgressListener() {
        public @Override void setDescription(String msg, Object... args) {
            System.err.printf(msg + "\n", args);
        }
    };

    protected static final String REMOTE_NAME = "origin";

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

    protected Feature points2_modified;

    protected Feature points3;

    protected Feature points3_modified;

    protected static final String linesNs = "http://geogig.lines";

    protected static final String linesName = "Lines";

    protected static final String linesTypeSpec = "sp:String,ip:Integer,pp:LineString:srid=4326";

    protected static final Name linesTypeName = new NameImpl("http://geogig.lines", linesName);

    protected SimpleFeatureType linesType;

    protected Feature lines1;

    protected Feature lines1_modified;

    protected Feature lines2;

    protected Feature lines2_modified;

    protected Feature lines3;

    protected Feature lines3_modified;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    public static class OpenRemoteOverride extends OpenRemote {

        private Map<String, IRemoteRepo> remoteOverride;

        public OpenRemoteOverride setOverrides(Map<String, IRemoteRepo> remoteOverride) {
            this.remoteOverride = remoteOverride;
            return this;
        }

        public @Override IRemoteRepo _call() {
            String name = getRemote().getName();
            IRemoteRepo override = remoteOverride.get(name);
            Preconditions.checkNotNull(override, "remote override %s not provided", name);
            Remote remoteConfig = super.getRemote();
            doReturn(remoteConfig).when(override).getInfo();
            return override;
        }
    }

    protected class GeogigContainer {
        public GeoGIG geogig;

        public Repository repo;

        public File envHome;

        public Context injector;

        public Map<String, IRemoteRepo> remoteOverride = new HashMap<>();

        public GeogigContainer(final String workingDirectory) throws IOException {

            envHome = tempFolder.newFolder(workingDirectory);

            ContextBuilder injectorBuilder = createInjectorBuilder();
            GlobalContextBuilder.builder(injectorBuilder);
            injector = injectorBuilder.build();

            geogig = new GeoGIG(injector);
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

        public void addRemoteOverride(Remote remote, Repository override) {
            IRemoteRepo remoteRepo = spy(LocalRemoteResolver.resolve(remote, override));
            try {
                remoteRepo.open();
            } catch (RepositoryConnectionException e) {
                throw new RuntimeException(e);
            }
            doNothing().when(remoteRepo).close();
            this.remoteOverride.put(remote.getName(), remoteRepo);
        }

        public Context getInjector() {
            return injector;
        }

        private AbstractModule RemoteOpenOverrideModule = new AbstractModule() {
            @Override
            protected void configure() {
                Decorator decorator = new Decorator() {

                    @Override
                    public <I> I decorate(I subject) {
                        OpenRemote cmd = (OpenRemote) subject;
                        cmd = cmd.command(OpenRemoteOverride.class).setOverrides(remoteOverride);
                        return (I) cmd;
                    }

                    @Override
                    public boolean canDecorate(Object instance) {
                        boolean canDecorate = OpenRemote.class.equals(instance.getClass());
                        return canDecorate;
                    }
                };
                Multibinder.newSetBinder(binder(), Decorator.class).addBinding()
                        .toInstance(decorator);
            }
        };

        private ContextBuilder createInjectorBuilder() {
            Platform testPlatform = new TestPlatform(envHome) {
                @Override
                public long currentTimeMillis() {
                    return 1000;
                }
            };
            return new TestContextBuilder(testPlatform, RemoteOpenOverrideModule);
        }
    }

    public GeogigContainer localGeogig;

    public GeogigContainer remoteGeogig;

    public GeogigContainer upstreamGeogig;

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
        upstreamGeogig = new GeogigContainer("upstream");
        {
            String remoteURI = remoteGeogig.repo.getLocation().toString();
            Remote originInfo = localGeogig.geogig.command(RemoteAddOp.class).setName(REMOTE_NAME)
                    .setURL(remoteURI).call();
            Repository originRepo = remoteGeogig.geogig.getRepository();
            localGeogig.addRemoteOverride(originInfo, originRepo);

            originInfo = upstreamGeogig.geogig.command(RemoteAddOp.class).setName(REMOTE_NAME)
                    .setURL(remoteURI).call();
            upstreamGeogig.addRemoteOverride(originInfo, originRepo);
        }

        pointsType = DataUtilities.createType(pointsNs, pointsName, pointsTypeSpec);

        points1 = feature(pointsType, idP1, "StringProp1_1", new Integer(1000), "POINT(1 1)");
        points1_modified = feature(pointsType, idP1, "StringProp1_1a", new Integer(1001),
                "POINT(1 2)");
        points2 = feature(pointsType, idP2, "StringProp1_2", new Integer(2000), "POINT(2 2)");
        points2_modified = feature(pointsType, idP2, "StringProp1_2a", new Integer(2001),
                "POINT(2 3)");
        points3 = feature(pointsType, idP3, "StringProp1_3", new Integer(3000), "POINT(3 3)");
        points3_modified = feature(pointsType, idP3, "StringProp1_3a", new Integer(3001),
                "POINT(3 4)");

        linesType = DataUtilities.createType(linesNs, linesName, linesTypeSpec);

        lines1 = feature(linesType, idL1, "StringProp2_1", new Integer(1000),
                "LINESTRING (1 1, 2 2)");
        lines1_modified = feature(linesType, idL1, "StringProp2_1a", new Integer(1001),
                "LINESTRING (1 2, 2 2)");
        lines2 = feature(linesType, idL2, "StringProp2_2", new Integer(2000),
                "LINESTRING (3 3, 4 4)");
        lines2_modified = feature(linesType, idL2, "StringProp2_2a", new Integer(2001),
                "LINESTRING (3 4, 4 4)");
        lines3 = feature(linesType, idL3, "StringProp2_3", new Integer(3000),
                "LINESTRING (5 5, 6 6)");
        lines3_modified = feature(linesType, idL3, "StringProp2_3a", new Integer(3001),
                "LINESTRING (5 6, 6 6)");

        setUpInternal();
    }

    protected LsRemoteOp lsremoteOp() {
        return localGeogig.geogig.command(LsRemoteOp.class);
    }

    protected FetchOp fetchOp() throws RepositoryConnectionException {
        return localGeogig.geogig.command(FetchOp.class);
    }

    protected CloneOp cloneOp() {
        return localGeogig.geogig.command(CloneOp.class).setRemoteName(REMOTE_NAME);
    }

    protected PullOp pullOp() {
        return localGeogig.geogig.command(PullOp.class);
    }

    protected PushOp pushOp() throws RepositoryConnectionException {
        return localGeogig.geogig.command(PushOp.class);
    }

    @After
    public final void tearDown() throws Exception {
        setup = false;
        tearDownInternal();
        localGeogig.tearDown();
        remoteGeogig.tearDown();
        upstreamGeogig.tearDown();
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

    protected Feature feature(SimpleFeatureType type, String id, Object... values) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    value = geom((String) value);
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

        add(geogig);
        return objectId;
    }

    protected ObjectId insertAndAdd(Repository geogig, Feature f) throws Exception {
        ObjectId objectId = insert(geogig, f);

        add(geogig);
        return objectId;
    }

    protected void insertAndAdd(Repository geogig, Feature... f) throws Exception {
        insert(geogig, f);
        add(geogig);
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    protected ObjectId insert(GeoGIG geogig, Feature f) throws Exception {
        return insert(geogig.getRepository(), f);
    }

    protected ObjectId insert(Repository repo, Feature f) throws Exception {
        final WorkingTree workTree = repo.workingTree();
        Name name = f.getType().getName();
        String parentPath = name.getLocalPart();
        RevFeatureType type = RevFeatureType.builder().type(f.getType()).build();
        repo.objectDatabase().put(type);
        String path = NodeRef.appendChild(parentPath, f.getIdentifier().getID());
        FeatureInfo fi = FeatureInfo.insert(RevFeature.builder().build(f), type.getId(), path);
        workTree.insert(fi);
        return fi.getFeature().getId();
    }

    protected void insertAndAdd(GeoGIG geogig, Feature... features) throws Exception {
        insert(geogig, features);
        add(geogig);
    }

    protected void add(GeoGIG geogig) {
        add(geogig.getRepository());
    }

    protected void add(Repository repo) {
        repo.command(AddOp.class).call();
    }

    protected Geometry geom(String wkt) {
        try {
            return new WKTReader2().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    protected void insert(Repository repo, Iterable<? extends Feature> features) throws Exception {
        WorkingTree workingTree = repo.workingTree();

        FeatureType type = features.iterator().next().getType();

        repo.objectDatabase().put(RevFeatureType.builder().type(type).build());

        final String treePath = type.getName().getLocalPart();

        Iterable<FeatureInfo> featureInfos = Iterables.transform(features,
                (f) -> featureInfo(treePath, f));

        workingTree.insert(featureInfos.iterator(), new DefaultProgressListener());
    }

    protected void insert(GeoGIG geogig, Feature... features) throws Exception {
        for (Feature f : features) {
            insert(geogig, f);
        }
    }

    protected void insert(Repository geogig, Feature... features) throws Exception {
        for (Feature f : features) {
            insert(geogig, f);
        }
    }

    public FeatureInfo featureInfo(String treePath, Feature f) {
        final String path = NodeRef.appendChild(treePath, f.getIdentifier().getID());
        RevFeature feature = RevFeature.builder().build(f);
        FeatureType type = f.getType();
        RevFeatureType ftype = RevFeatureType.builder().type(type).build();
        return FeatureInfo.insert(feature, ftype.getId(), path);
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
            add(geogig);
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

    protected void delete(Repository repo, Iterable<? extends Feature> features) throws Exception {
        final WorkingTree workTree = repo.workingTree();

        Iterator<String> featurePaths = Iterators.transform(features.iterator(),
                (f) -> f.getType().getName().getLocalPart() + "/" + f.getIdentifier().toString());
        workTree.delete(featurePaths, new DefaultProgressListener());
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

    protected void createBranch(Repository repo, String branch) {
        repo.command(BranchCreateOp.class).setAutoCheckout(true).setName(branch)
                .setProgressListener(SIMPLE_PROGRESS).call();
    }

    protected void checkout(Repository repo, String branch) {
        repo.command(CheckoutOp.class).setSource(branch).call();
    }

    protected MergeReport mergeNoFF(Repository repo, String branch, String mergeMessage,
            boolean mergeOurs) {
        Ref branchRef = repo.command(RefParse.class).setName(branch).call().get();
        ObjectId updatesBranchTip = branchRef.getObjectId();
        MergeReport mergeReport = repo.command(MergeOp.class)//
                .setMessage(mergeMessage)//
                .setNoFastForward(true)//
                .addCommit(updatesBranchTip)//
                .setOurs(mergeOurs)//
                .setTheirs(!mergeOurs)//
                .setProgressListener(SIMPLE_PROGRESS)//
                .call();
        return mergeReport;
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

    public RevCommit commit(Repository repo, String msg) {
        return repo.command(CommitOp.class).setMessage(msg).call();
    }

    protected Optional<Ref> getRef(Repository repo, String refspec) {
        return repo.command(RefParse.class).setName(refspec).call();
    }

    protected void assertSummary(TransferSummary result, String remoteURL, @Nullable Ref before,
            @Nullable Ref after) {
        assertSummary(result, remoteURL, fromNullable(before), fromNullable(after));
    }

    protected void assertSummary(TransferSummary result, String remoteURL, Optional<Ref> before,
            Optional<Ref> after) {
        assertNotNull(result);
        Collection<RefDiff> diffs = result.getRefDiffs().get(remoteURL);
        assertNotNull(diffs);
        String name = before.or(after).get().getName();
        RefDiff diff = Maps.uniqueIndex(diffs, (d) -> d.oldRef().or(d.newRef()).get().getName())
                .get(name);
        assertNotNull(diff);
        assertEquals(before, diff.oldRef());
        assertEquals(after, diff.newRef());
    }

    protected List<RevCommit> log(Repository repo) {
        return Lists.newArrayList(repo.command(LogOp.class).call());
    }
}
