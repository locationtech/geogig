/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.porcelain.ConfigOp.ConfigAction.CONFIG_LIST;
import static org.locationtech.geogig.porcelain.ConfigOp.ConfigScope.LOCAL;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.merge.ConflictsCountOp;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.index.IndexUtils;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolverTestUtil;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geopkg.GeoPackageWebAPITestSupport;
import org.locationtech.geogig.test.TestData;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.matchers.CompareMatcher;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.matchers.HasXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;
import cucumber.runtime.java.guice.ScenarioScoped;

/**
 *
 */
@ScenarioScoped
@StepDefAnnotation
public class WebAPICucumberHooks {

    public FunctionalTestContext context = null;

    private static final Map<String, String> NSCONTEXT = ImmutableMap.of("atom",
            "http://www.w3.org/2005/Atom");

    // set of repository names to track during Scenario execution
    private final Set<String> openedRepos = new HashSet<>();

    @Inject
    public WebAPICucumberHooks(FunctionalTestContext context) {
        this.context = context;
    }

    @cucumber.api.java.Before
    public void before() throws Exception {
        if (TestRepoURIBuilderProvider.getURIBuilder() == null) {
            TestRepoURIBuilderProvider.setURIBuilder(TestRepoURIBuilder.createDefault());
        }

        // before each Scenario, clear out the opened repository set
        openedRepos.clear();
        context.before();
    }

    @cucumber.api.java.After
    public void after() {
        // Restore available resolvers
        RepositoryResolverTestUtil.clearDisabledResolverList();
        // try to close any repositories used while executing a Scenario
        for (String repoName : openedRepos) {
            Repository repo = context.getRepo(repoName);
            if (repo != null) {
                repo.close();
            }
        }
        context.after();
    }

    ///////////////// repository initialization steps ////////////////////

    @Given("^There is an empty multirepo server$")
    public void setUpEmptyMultiRepo() {
    }

    @Given("^There is a default multirepo server$")
    public void setUpDefaultMultiRepo() throws Exception {
        setUpEmptyMultiRepo();
        openedRepos.addAll(context.setUpDefaultMultiRepoServer());
    }

    @Given("I have \"([^\"]*)\" that is not managed$")
    public void setupExtraUnMangedRepo(String repoName) throws Exception {
        context.createUnmanagedRepo(repoName)
                .init("geogigUser", "repo1_Owner@geogig.org")
                .loadDefaultData()
                .getRepo().close();
        openedRepos.add(repoName);
    }

    @Given("^There is a default multirepo server with remotes$")
    public void setUpDefaultMultiRepoWithRemotes() throws Exception {
        setUpEmptyMultiRepo();
        openedRepos.addAll(context.setUpDefaultMultiRepoServerWithRemotes(false));
    }

    @Given("^There is a default multirepo server with http remotes$")
    public void setUpDefaultMultiRepoWithHttpRemotes() throws Exception {
        setUpEmptyMultiRepo();
        openedRepos.addAll(context.setUpDefaultMultiRepoServerWithRemotes(true));
    }

    @Given("^There is a default multirepo server with a shallow clone$")
    public void setUpDefaultMultiRepoWithShallowClone() throws Exception {
        setUpEmptyMultiRepo();
        openedRepos.addAll(context.setUpDefaultMultiRepoServerWithShallowClone());
    }

    @Given("^There are three repos with remotes$")
    public void twoGoodRepos() throws Throwable {
        Repository repo2 = context.createRepo("repo2").init("geogigUser", "repo_Owner@geogig.org")
                .getRepo();

        Repository repo3 = context.createRepo("repo3").init("geogigUser", "repo_Owner@geogig.org")
                .getRepo();

        String repo2Url = repo2.getLocation().toString();
        repo3.command(RemoteAddOp.class).setName("repo2").setURL(repo2Url).call();

        Repository repo4 = context.createRepo("repo4").init("geogigUser", "repo_Owner@geogig.org")
                .getRepo();

        repo4.command(RemoteAddOp.class).setName("repo2").setURL(repo2Url).call();

        repo2.close();
        repo3.close();
        repo4.close();
        openedRepos.add("repo1");
        openedRepos.add("repo2");
        openedRepos.add("repo3");
        openedRepos.add("repo4");
    }

    @Given("^There is an empty repository named ([^\"]*)$")
    public void setUpEmptyRepo(String name) throws Throwable {
        context.createRepo(name).init("webuser", "webuser@test.com").getRepo().close();
        openedRepos.add(name);
    }

    @Given("^There is a repository with multiple branches named ([^\"]*)$")
    public void setUpMultipleBranches(String name) throws Throwable {
        Repository repo = context.createRepo(name).init("webuser", "webuser@test.com").getRepo();
        TestData data = new TestData(repo);
        data.addAndCommit("Added Point.1", TestData.point1);
        data.branch("non_conflicting");
        data.branch("conflicting");
        data.addAndCommit("Modified Point.1", TestData.point1_modified);
        data.branch("master_original");
        data.checkout("conflicting");
        data.remove(TestData.point1);
        data.add();
        data.commit("Removed Point.1");
        data.checkout("non_conflicting");
        data.addAndCommit("Added Point.2", TestData.point2);
        data.checkout("master");
        repo.close();
        openedRepos.add(name);
    }

    @Given("There is a repo with some data")
    public void setUpRepoWithData() throws Exception {
        Repository repo = context.createRepo("repo1").init("myuser", "the_user@testing.com")
                .getRepo();
        TestData data = new TestData(repo);
        data.addAndCommit("Added Point.1", TestData.point1);
    }

    @Then("We change, add and commit some more data")
    public void addMoreDataIntoRepo() throws Exception {
        Repository repo = context.getRepo("repo1");
        TestData data = new TestData(repo);
        data.addAndCommit("modify point1; add point2, line1, poly1", data.point1_modified,
                data.point2, data.line1, data.poly1);
    }

    /**
     * Checks that the repository named {@code repositoryName}, at it's commit {@code headRef}, has
     * the expected features as given by the {@code expectedFeatures} {@link DataTable}.
     * <p>
     * The {@code DataTable} top cells represent feature tree paths, and their cells beneath each
     * feature tree path, the feature ids expected for each layer.
     * <p>
     * A {@code question mark} indicates a wild card feature where the feature id may not be known.
     * <p>
     * Example:
     * 
     * <pre>
     * <code>
     *     |  Points   |  Lines   |  Polygons   | 
     *     |  Points.1 |  Lines.1 |  Polygons.1 | 
     *     |  Points.2 |  Lines.2 |  Polygons.2 | 
     *     |  ?        |          |             |
     *</code>
     * </pre>
     * 
     * @param repositoryName
     * @param headRef
     * @param expectedFeatures
     * @throws Throwable
     */
    private void verifyRepositoryContents(String repositoryName, String headRef, String txId,
            DataTable expectedFeatures, boolean index) {
        SetMultimap<String, String> expected = HashMultimap.create();
        {
            List<Map<String, String>> asMaps = expectedFeatures.asMaps(String.class, String.class);
            for (Map<String, String> featureMap : asMaps) {
                for (Entry<String, String> entry : featureMap.entrySet()) {
                    if (entry.getValue().length() > 0) {
                        expected.put(entry.getKey(), context.replaceVariables(entry.getValue()));
                    }
                }
            }
        }

        SetMultimap<String, String> actual = context.listRepo(repositoryName, headRef, txId, index);

        Map<String, Collection<String>> actualMap = actual.asMap();
        Map<String, Collection<String>> expectedMap = expected.asMap();

        for (String featureType : actualMap.keySet()) {
            assertTrue(expectedMap.containsKey(featureType));
            Collection<String> actualFeatureCollection = actualMap.get(featureType);
            Collection<String> expectedFeatureCollection = expectedMap.get(featureType);
            for (String actualFeature : actualFeatureCollection) {
                if (expectedFeatureCollection.contains(actualFeature)) {
                    expectedFeatureCollection.remove(actualFeature);
                } else if (expectedFeatureCollection.contains("?")) {
                    expectedFeatureCollection.remove("?");
                } else {
                    fail();
                }
            }
            assertEquals(0, expectedFeatureCollection.size());
            expectedMap.remove(featureType);
        }
        assertEquals(0, expectedMap.size());

        // add the repo to the set so it can be closed
        openedRepos.add(repositoryName);
    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" in the (@[^\"]*) transaction should have the following features:$")
    public void verifyRepositoryContentsTx(String repositoryName, String headRef, String txId,
            DataTable expectedFeatures) throws Throwable {
        verifyRepositoryContents(repositoryName, headRef, txId, expectedFeatures, false);

    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" should have the following features:$")
    public void verifyRepositoryContents(String repositoryName, String headRef,
            DataTable expectedFeatures) throws Throwable {
        verifyRepositoryContents(repositoryName, headRef, null, expectedFeatures, false);
    }

    private Optional<ObjectId> resolveIndexTreeId(String repositoryName, String headRef,
            @Nullable String attributeName) {
        Repository repo = context.getRepo(repositoryName);
        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(repo.context(), headRef);
        if (typeTreeRef == null) {
            return Optional.absent();
        }
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(repo.indexDatabase(), treeName,
                attributeName);
        if (indexInfos.isEmpty()) {
            return Optional.absent();
        }
        Preconditions.checkState(indexInfos.size() == 1,
                "Multiple indexes found for given tree ref.");
        Optional<ObjectId> indexTreeId = repo.indexDatabase().resolveIndexedTree(indexInfos.get(0),
                typeTreeRef.getObjectId());
        return indexTreeId;
    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" index on the \"([^\"]*)\" attribute should have the following features:$")
    public void verifyIndexContents(String repositoryName, String headRef, String attributeName,
            DataTable expectedFeatures) throws Throwable {
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(repositoryName, headRef, attributeName);
        assertTrue(indexTreeId.isPresent());
        verifyRepositoryContents(repositoryName, indexTreeId.get().toString(), null,
                expectedFeatures, true);
    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" index should have the following features:$")
    public void verifyIndexContents(String repositoryName, String headRef,
            DataTable expectedFeatures) throws Throwable {
        verifyIndexContents(repositoryName, headRef, null, expectedFeatures);
    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" should not have an index$")
    public void noIndexAtCommit(String repositoryName, String headRef) throws Throwable {
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(repositoryName, headRef, null);
        assertFalse(indexTreeId.isPresent());
        // add the repo to the set so it can be closed
        openedRepos.add(repositoryName);
    }

    @SuppressWarnings("unchecked")
    @Then("^the ([^\"]*) repository's \"([^\"]*)\" index should track the extra attribute \"([^\"]*)\"$")
    public void verifyIndexExtraAttributes(String repositoryName, String headRef,
            String attributeName) throws Throwable {
        Repository repo = context.getRepo(repositoryName);
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(repositoryName, headRef, null);
        assertTrue(indexTreeId.isPresent());
        RevTree indexTree = repo.indexDatabase().getTree(indexTreeId.get());
        Set<org.locationtech.geogig.model.Node> nodes = RevObjectTestSupport.getTreeNodes(indexTree,
                repo.indexDatabase());
        for (org.locationtech.geogig.model.Node n : nodes) {
            Map<String, Object> extraData = n.getExtraData();
            assertTrue(extraData.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
            Map<String, Object> attributeData = (Map<String, Object>) extraData
                    .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
            assertTrue(attributeData.containsKey(attributeName));
        }
        // add the repo to the set so it can be closed
        openedRepos.add(repositoryName);
    }

    @SuppressWarnings("unchecked")
    @Then("^the ([^\"]*) repository's \"([^\"]*)\" index should not track the extra attribute \"([^\"]*)\"$")
    public void verifyIndexNotExtraAttributes(String repositoryName, String headRef,
            String attributeName) throws Throwable {
        Repository repo = context.getRepo(repositoryName);
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(repositoryName, headRef, null);
        assertTrue(indexTreeId.isPresent());
        RevTree indexTree = repo.indexDatabase().getTree(indexTreeId.get());
        Set<org.locationtech.geogig.model.Node> nodes = RevObjectTestSupport.getTreeNodes(indexTree,
                repo.indexDatabase());
        for (org.locationtech.geogig.model.Node n : nodes) {
            Map<String, Object> extraData = n.getExtraData();
            if (extraData.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA)) {
                Map<String, Object> attributeData = (Map<String, Object>) extraData
                        .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
                assertFalse(attributeData.containsKey(attributeName));
            }
        }
        // add the repo to the set so it can be closed
        openedRepos.add(repositoryName);
    }

    @Then("^the ([^\"]*) repository's \"([^\"]*)\" index bounds should be \"([^\"]*)\"$")
    public void verifyIndexBounds(String repositoryName, String headRef, String bbox)
            throws Throwable {
        Repository repo = context.getRepo(repositoryName);
        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(repo.context(), headRef);
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(repo.indexDatabase(), treeName,
                null);
        assertEquals(1, indexInfos.size());
        Map<String, Object> metadata = indexInfos.get(0).getMetadata();
        assertTrue(metadata.containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        Envelope indexBounds = (Envelope) metadata.get(IndexInfo.MD_QUAD_MAX_BOUNDS);
        Envelope expected = SpatialOps.parseNonReferencedBBOX(bbox);
        final double EPSILON = 0.00001;
        assertEquals(expected.getMinX(), indexBounds.getMinX(), EPSILON);
        assertEquals(expected.getMaxX(), indexBounds.getMaxX(), EPSILON);
        assertEquals(expected.getMinY(), indexBounds.getMinY(), EPSILON);
        assertEquals(expected.getMaxY(), indexBounds.getMaxY(), EPSILON);
    }

    @Given("^There are multiple branches on the \"([^\"]*)\" repo$")
    public void There_are_multiple_branches(String repoName) throws Throwable {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        data.addAndCommit("Added Point.1", TestData.point1);
        ObjectId master = repo.command(RefParse.class).setName("master").call().get().getObjectId();
        repo.command(UpdateRef.class).setName(Ref.REMOTES_PREFIX + "origin/master_remote")
                .setNewValue(master).call();
        data.branchAndCheckout("branch1");
        data.addAndCommit("Added Point.2", TestData.point2);
        ObjectId branch1 = repo.command(RefParse.class).setName("branch1").call().get()
                .getObjectId();
        repo.command(UpdateRef.class).setName(Ref.REMOTES_PREFIX + "origin/branch1_remote")
                .setNewValue(branch1).call();
        data.branchAndCheckout("branch2");
        data.addAndCommit("Added Line.1", TestData.line1);
        ObjectId branch2 = repo.command(RefParse.class).setName("branch2").call().get()
                .getObjectId();
        repo.command(UpdateRef.class).setName(Ref.REMOTES_PREFIX + "origin/branch2_remote")
                .setNewValue(branch2).call();
        data.checkout("master");
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^There is a tag called \"([^\"]*)\" on the \"([^\"]*)\" repo pointing to \"([^\"]*)\" with the \"([^\"]*)\" message$")
    public void There_is_a_tag(String tagName, String repoName, String target, String message) {
        Repository repo = context.getRepo(repoName);
        target = context.replaceVariables(target);
        repo.command(TagCreateOp.class).setName(tagName).setCommitId(ObjectId.valueOf(target))
                .setMessage(message).call();
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^There are conflicts on the \"([^\"]*)\" repo in the (@[^\"]*) transaction$")
    public void There_are_conflict(String repoName, String txId) throws Throwable {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        GeogigTransaction transaction = repo.command(TransactionResolve.class)
                .setId(UUID.fromString(context.getVariable(txId))).call().get();
        data.setTransaction(transaction);
        data.addAndCommit("Added Point.1", TestData.point1);
        data.branch("branch1");
        data.addAndCommit("Modified Point.1", TestData.point1_modified);
        data.checkout("branch1");
        data.remove(TestData.point1);
        data.add();
        data.commit("Removed Point.1");
        data.checkout("master");
        try {
            data.mergeNoFF("branch1", "Merge branch1");
        } catch (MergeConflictsException e) {
            // Expected
        }
        assertEquals(1, transaction.command(ConflictsCountOp.class).call().longValue());
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^There should be no conflicts on the \"([^\"]*)\" repo in the (@[^\"]*) transaction$")
    public void There_should_be_no_conflicts(String repoName, String txId) throws Throwable {
        Repository repo = context.getRepo(repoName);
        GeogigTransaction transaction = repo.command(TransactionResolve.class)
                .setId(UUID.fromString(context.getVariable(txId))).call().get();

        assertEquals(0, transaction.command(ConflictsCountOp.class).call().longValue());
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^There is a feature with multiple authors on the \"([^\"]*)\" repo$")
    public void There_is_a_feature_with_multiple_authors(String repoName) throws Throwable {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        data.config("user.name", "Author1");
        data.config("user.email", "author1@test.com");
        data.addAndCommit("Added Point.1", TestData.point1);
        data.config("user.name", "Author2");
        data.config("user.email", "author2@test.com");
        data.addAndCommit("Modified Point.1", TestData.point1_modified);
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^I have checked out \"([^\"]*)\" on the \"([^\"]*)\" repo$")
    public void I_have_checked_out(String branch, String repoName) throws Throwable {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        data.checkout(branch);
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    private SimpleFeature parseFeature(String featureName) throws Exception {
        SimpleFeature feature;
        if (featureName.equals("Point.1")) {
            feature = TestData.point1;
        } else if (featureName.equals("Point.2")) {
            feature = TestData.point2;
        } else if (featureName.equals("Point.3")) {
            feature = TestData.point3;
        } else if (featureName.equals("Point.1_modified")) {
            feature = TestData.point1_modified;
        } else if (featureName.equals("Point.2_modified")) {
            feature = TestData.point2_modified;
        } else if (featureName.equals("Line.1")) {
            feature = TestData.line1;
        } else if (featureName.equals("Line.2")) {
            feature = TestData.line2;
        } else if (featureName.equals("Line.3")) {
            feature = TestData.line3;
        } else if (featureName.equals("Polygon.1")) {
            feature = TestData.poly1;
        } else if (featureName.equals("Polygon.2")) {
            feature = TestData.poly2;
        } else if (featureName.equals("Polygon.3")) {
            feature = TestData.poly3;
        } else {
            throw new Exception("Unknown Feature");
        }
        return feature;
    }

    @Given("^I have unstaged \"([^\"]*)\" on the \"([^\"]*)\" repo in the \"([^\"]*)\" transaction$")
    public TestData I_have_unstaged(String feature, String repoName, String txId) throws Throwable {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        if (!txId.isEmpty()) {
            GeogigTransaction transaction = repo.command(TransactionResolve.class)
                    .setId(UUID.fromString(context.getVariable(txId))).call().get();
            data.setTransaction(transaction);
        }
        data.insert(parseFeature(feature));
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
        return data;
    }

    @Given("^I have staged \"([^\"]*)\" on the \"([^\"]*)\" repo in the \"([^\"]*)\" transaction$")
    public void I_have_staged(String feature, String repoName, String txId) throws Throwable {
        TestData data = I_have_unstaged(feature, repoName, txId);
        data.add();
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^I have committed \"([^\"]*)\" on the \"([^\"]*)\" repo in the \"([^\"]*)\" transaction$")
    public void I_have_committed(String feature, String repoName, String txId) throws Throwable {
        TestData data = I_have_unstaged(feature, repoName, txId);
        data.add();
        data.commit("Added " + feature);
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^I have removed \"([^\"]*)\" on the \"([^\"]*)\" repo in the \"([^\"]*)\" transaction$")
    public void I_have_removed(String feature, String repoName, String txId) throws Exception {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        if (!txId.isEmpty()) {
            GeogigTransaction transaction = repo.command(TransactionResolve.class)
                    .setId(UUID.fromString(context.getVariable(txId))).call().get();
            data.setTransaction(transaction);
        }
        data.remove(parseFeature(feature));
        data.add();
        data.commit("Removed " + feature);
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    @Given("^The graph database on the \"([^\"]*)\" repo has been truncated$")
    public void Truncate_graph_database(String repoName) throws Throwable {
        Repository repo = context.getRepo(repoName);
        repo.graphDatabase().truncate();
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    /**
     * Executes a request using the HTTP Method and resource URI given in {@code methodAndURL}.
     * <p>
     * Any variable name in the resource URI will be first replaced by its value.
     * <p>
     * Variable names can be given as <code>{@variable}</code>, and shall previously be set through
     * {@link #saveResponseXPathValueAsVariable(String, String)} using <code>@variable</code>
     * format.
     * 
     * @param methodAndURL HTTP method and URL to call, e.g. {@code GET /repo1/command?arg1=value},
     *        {@code PUT /repo1/init}, etc.
     */
    @When("^I call \"([^\"]*)\"$")
    public void callURL(final String methodAndURL) {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        HttpMethod method = HttpMethod.valueOf(httpMethod);
        context.call(method, resourceUri);
    }

    /**
     * Creates a transaction on the given repository and stores the transaction id in the given
     * variable for later use.
     * 
     * @param variableName the variable name to store the transaction id.
     * @param repoName the repository on which to create the transaction.
     */
    @Given("^I have a transaction as \"([^\"]*)\" on the \"([^\"]*)\" repo$")
    public void beginTransactionAsVariable(final String variableName, final String repoName) {
        GeogigTransaction transaction = context.getRepo(repoName).command(TransactionBegin.class)
                .call();

        context.setVariable(variableName, transaction.getTransactionId().toString());
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    /**
     * Ends the given transaction on the given repository.
     * 
     * @param variableName the variable where the transaction id is stored.
     * @param repoName the repository on which the transaction was created.
     */
    @When("^I end the transaction with id \"([^\"]*)\" on the \"([^\"]*)\" repo$")
    public void endTransaction(final String variableName, final String repoName) {
        Repository repo = context.getRepo(repoName);
        GeogigTransaction transaction = repo.command(TransactionResolve.class)
                .setId(UUID.fromString(context.getVariable(variableName))).call().get();
        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    /**
     * Saves the value of an XPath expression over the last response's XML as a variable.
     * <p>
     * {@link #callURL(String)} will decode the variable and replace it by its value before issuing
     * the request.
     * 
     * @param xpathExpression the expression to evalue from the last response
     * @param variableName the name of the variable to save the xpath expression value as
     */
    @Then("^I save the response \"([^\"]*)\" as \"([^\"]*)\"$")
    public void saveResponseXPathValueAsVariable(final String xpathExpression,
            final String variableName) {

        String xml = context.getLastResponseText();

        String xpathValue = evaluateXpath(xml, xpathExpression);

        context.setVariable(variableName, xpathValue);
    }

    private String evaluateXpath(String xml, final String xpathExpression) {
        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(NSCONTEXT);

        String xpathValue = xpathEngine.evaluate(xpathExpression,
                new StreamSource(new StringReader(xml)));
        return xpathValue;
    }

    @Then("^the response status should be '(\\d+)'$")
    public void checkStatusCode(final int statusCode) {
        assertStatusCode(statusCode);
    }

    private void assertStatusCode(final int statusCode) {
        HttpStatus status = HttpStatus.valueOf(context.getLastResponseStatus());
        HttpStatus expected = HttpStatus.valueOf(statusCode);
        assertEquals(format("Expected status code %s, but got %s", expected, status), statusCode,
                status.value());
    }

    @Then("^the response ContentType should be \"([^\"]*)\"$")
    public void checkContentType(final String expectedContentType) {
        String actualContentType = context.getLastResponseContentType();
        assertTrue(actualContentType.contains(expectedContentType));
    }

    /**
     * Checks that the response allowed methods match the given list.
     * <p>
     * Note the list of allowed methods in the response is only set when a 304 (method not allowed)
     * status code is set.
     * 
     * @param csvMethodList comma separated list of expected HTTP method names
     */
    @Then("^the response allowed methods should be \"([^\"]*)\"$")
    public void checkResponseAllowedMethods(final String csvMethodList) {

        Set<String> expected = Sets
                .newHashSet(Splitter.on(',').omitEmptyStrings().splitToList(csvMethodList));

        Set<String> allowedMethods = context.getLastResponseAllowedMethods();

        assertSetsContainTheSameElements(expected, allowedMethods);
    }

    private void assertSetsContainTheSameElements(Set<String> expected, Set<String> actual) {
        // if expected is null, actual better be as well
        if (expected == null) {
            assertNull(actual);
            return;
        }
        // if expected is not null, actual better not be
        assertNotNull(actual);
        // check sizes
        assertEquals(expected.size(), actual.size());
        // make sure all elements in one are in the other
        assertTrue(expected.containsAll(actual));
        assertTrue(actual.containsAll(expected));
    }

    @Then("^the xml response should contain \"([^\"]*)\"$")
    public void checkResponseContainsXPath(final String xpathExpression) {

        final String xml = context.getLastResponseText();
        assertXpathPresent(xpathExpression, xml);
    }

    private void assertXpathPresent(final String xpathExpression, final String xml) {
        assertThat(xml, HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(NSCONTEXT));
    }

    @Then("^the response xml matches$")
    public void checkXmlResponseMatches(final String domString) throws Throwable {

        final String xml = context.getLastResponseText();
        assertThat(xml, CompareMatcher.isIdenticalTo(domString).ignoreComments().ignoreWhitespace()
                .withNamespaceContext(NSCONTEXT));
    }

    /**
     * Checks that the given xpath expression is found exactly the expected times in the response
     * xml
     */
    @Then("^the xml response should contain \"([^\"]*)\" (\\d+) times$")
    public void checkXPathCadinality(final String xpathExpression, final int times) {
        List<Node> nodes = getDomNodes(xpathExpression);
        assertEquals(times, nodes.size());
    }

    @Then("^the response body should contain \"([^\"]*)\"$")
    public void checkResponseTextContains(String substring) {
        substring = context.replaceVariables(substring);
        final String responseText = context.getLastResponseText();
        assertThat(responseText, containsString(substring));
    }

    @Then("^the response body should not contain \"([^\"]*)\"$")
    public void checkResponseTextNotContains(String substring) {
        substring = context.replaceVariables(substring);
        final String responseText = context.getLastResponseText();
        assertThat(responseText, not(containsString(substring)));
    }

    @Then("^the variable \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkVariableEquals(String variable, String expectedValue) {
        variable = context.replaceVariables(variable);
        expectedValue = context.replaceVariables(expectedValue);
        assertEquals(variable, expectedValue);
    }

    @Then("^the xml response should not contain \"([^\"]*)\"$")
    public void responseDoesNotContainXPath(final String xpathExpression) {

        final String xml = context.getLastResponseText();
        assertThat(xml, xml,
                not(HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(NSCONTEXT)));
    }

    @Then("^the xpath \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkXPathEquals(String xpath, String expectedValue) {
        expectedValue = context.replaceVariables(expectedValue);
        final String xml = context.getLastResponseText();
        assertXpathEquals(xpath, expectedValue, xml);
    }

    private void assertXpathEquals(String xpath, String expectedValue, final String xml) {
        assertThat(xml, xml, EvaluateXPathMatcher.hasXPath(xpath, equalTo(expectedValue))
                .withNamespaceContext(NSCONTEXT));
    }

    @Then("^there is an xpath \"([^\"]*)\" that equals \"([^\"]*)\"$")
    public void checkOneXPathEquals(String xpath, String expectedValue) {
        expectedValue = context.replaceVariables(expectedValue);
        List<Node> nodes = getDomNodes(xpath);
        boolean match = false;
        for (Node node : nodes) {
            if (node.getTextContent().equals(expectedValue)) {
                match = true;
                break;
            }
        }
        assertTrue(match);
    }

    @Then("^the xpath \"([^\"]*)\" contains \"([^\"]*)\"$")
    public void checkXPathValueContains(final String xpath, final String substring) {
        final String xml = context.getLastResponseText();
        assertXpathContains(xpath, substring, xml);
    }

    private void assertXpathContains(final String xpath, final String substring, final String xml) {
        assertThat(xml, xml, EvaluateXPathMatcher.hasXPath(xpath, containsString(substring))
                .withNamespaceContext(NSCONTEXT));
    }

    @Then("^there is an xpath \"([^\"]*)\" that contains \"([^\"]*)\"$")
    public void checkOneXPathValueContains(final String xpath, final String substring) {
        List<Node> nodes = getDomNodes(xpath);
        boolean match = false;
        for (Node node : nodes) {
            if (node.getTextContent().contains(substring)) {
                match = true;
                break;
            }
        }
        assertTrue(match);
    }

    private List<Node> getDomNodes(final String xpath) {
        Document dom = context.getLastResponseAsDom();
        Source source = new DOMSource(dom);

        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(NSCONTEXT);

        return Lists.newArrayList(xpathEngine.selectNodes(xpath, source));
    }

    ////////////////////// async task step definitions //////////////////////////
    /**
     * Checks the last call response is an async task and saves the task id as the
     * {@code taskIdVariable} variable
     * 
     * <pre>
     * <code>
     *   <task>
     *     <id>2</id>
     *     <status>RUNNING</status>
     *     <description>Export to Geopackage database</description>
     *     <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href=
    "http://localhost:8182/tasks/2.xml" type="application/xml"/>
     *   </task>
     * </code>
     * </pre>
     * 
     */
    @Then("^the response is an XML async task (@[^\"]*)$")
    public void checkResponseIsAnXMLAsyncTask(String taskIdVariable) {
        checkNotNull(taskIdVariable);

        assertEquals("application/xml", context.getLastResponseContentType());
        final String xml = context.getLastResponseText();
        assertXmlIsAsyncTask(xml);

        Integer taskId = getAsyncTasskId(xml);
        context.setVariable(taskIdVariable, taskId.toString());
    }

    private void assertXmlIsAsyncTask(final String xml) {
        assertXpathPresent("/task/id", xml);
        assertXpathPresent("/task/status", xml);
        assertXpathPresent("/task/description", xml);
    }

    @Then("^when the task (@[^\"]*) finishes$")
    public void waitForAsyncTaskToFinish(String taskIdVariable) throws Throwable {
        checkNotNull(taskIdVariable);

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));

        AsyncContext.Status status = AsyncContext.Status.WAITING;
        do {
            Thread.sleep(100);
            String text = getAsyncTaskAsXML(taskId);
            assertXmlIsAsyncTask(text);
            status = getAsyncTaskStatus(text);
        } while (!status.isTerminated());
    }

    private String getAsyncTaskAsXML(final Integer taskId) throws IOException {
        String url = String.format("/tasks/%d", taskId);
        context.call(HttpMethod.GET, url);
        String text = context.getLastResponseText();
        return text;
    }

    @Then("^the task (@[^\"]*) status is ([^\"]*)$")
    public void checkAsyncTaskStatus(String taskIdVariable, AsyncContext.Status status)
            throws Throwable {
        checkNotNull(taskIdVariable);
        checkNotNull(status);

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        String xml = getAsyncTaskAsXML(taskId);
        assertXpathEquals("/task/status/text()", status.toString(), xml);

    }

    @Then("^the task (@[^\"]*) description contains \"([^\"]*)\"$")
    public void the_task_taskId_description_contains(final String taskIdVariable,
            String descriptionSubstring) throws Throwable {

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        final String xml = getAsyncTaskAsXML(taskId);

        final String substring = context.replaceVariables(descriptionSubstring);

        assertXpathContains("/task/description/text()", substring, xml);
    }

    @Then("^the task (@[^\"]*) result contains \"([^\"]*)\" with value \"([^\"]*)\"$")
    public void the_task_taskId_result_contains_with_value(final String taskIdVariable,
            String xpath, String expectedValueSubString) throws Throwable {

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        final String xml = getAsyncTaskAsXML(taskId);

        final String substring = context.replaceVariables(expectedValueSubString);

        String resultXpath = "/task/result/" + xpath;
        assertXpathContains(resultXpath, substring, xml);
    }

    private Integer getAsyncTasskId(final String responseBody) {
        String xml = context.getLastResponseText();
        checkResponseContainsXPath("/task/id");
        String value = evaluateXpath(xml, "/task/id/text()");
        return Integer.valueOf(value);
    }

    private AsyncContext.Status getAsyncTaskStatus(final String taskBody) {
        checkResponseContainsXPath("/task/status");
        String statusStr = evaluateXpath(taskBody, "/task/status/text()");
        AsyncContext.Status status = AsyncContext.Status.valueOf(statusStr);
        return status;
    }

    @Then("^I prune the task (@[^\"]*)$")
    public void prune_task(String taskIdVariable) throws Throwable {
        checkNotNull(taskIdVariable);

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        String url = String.format("/tasks/%d?prune=true", taskId);
        context.call(HttpMethod.GET, url);
        context.getLastResponseText();

    }

    ////////////////////// GeoPackage step definitions //////////////////////////

    @Then("^the result is a valid GeoPackage file$")
    public void gpkg_CheckResponseIsGeoPackage() throws Throwable {
        checkContentType(Variants.GEOPKG_MEDIA_TYPE.getType());

        File tmp = File.createTempFile("gpkg_functional_test", ".gpkg", context.getTempFolder());
        tmp.deleteOnExit();

        try (InputStream stream = context.getLastResponseInputStream()) {
            try (OutputStream to = new FileOutputStream(tmp)) {
                ByteStreams.copy(stream, to);
            }
        }

        GeoPackage gpkg = new GeoPackage(tmp);
        try {
            List<FeatureEntry> features = gpkg.features();
            System.err.printf("Found gpkg tables: %s\n",
                    Lists.transform(features, (e) -> e.getTableName()));
        } finally {
            gpkg.close();
        }
    }

    /**
     * Creates a GPKG file with default test contents and saves it's path as variable
     * {@code fileVariableName}
     */
    @Given("^I have a geopackage file (@[^\"]*)$")
    public void gpkg_CreateSampleGeopackage(final String fileVariableName) throws Throwable {
        GeoPackageWebAPITestSupport support = new GeoPackageWebAPITestSupport(
                context.getTempFolder());
        File dbfile = support.createDefaultTestData();
        context.setVariable(fileVariableName, dbfile.getAbsolutePath());
    }

    /**
     * Exports the Points feature type to a geopackage from the given repository and stores the file
     * name in the given variable.
     * 
     * @param repoName the repository to export from.
     * @param fileVariableName the variable to store the geopackage file name in.
     */
    @Given("^I export Points from \"([^\"]*)\" to a geopackage file with audit logs as (@[^\"]*)$")
    public void gpkg_ExportAuditLogs(final String repoName, final String fileVariableName)
            throws Throwable {
        GeoPackageWebAPITestSupport support = new GeoPackageWebAPITestSupport(
                context.getTempFolder());
        Repository geogig = context.getRepo(repoName);
        File file = support.createDefaultTestData();
        geogig.command(GeopkgAuditExport.class).setDatabase(file).setTargetTableName("Points")
                .setSourcePathspec("Points").call();
        context.setVariable(fileVariableName, file.getAbsolutePath());
        // add the repo to the set so it can be closed
        openedRepos.add(repoName);
    }

    /**
     * Adds Points/4 feature to the geopackage file referred to by the provided variable name.
     * 
     * @param fileVariableName the variable which stores the location of the geopackage file.
     */
    @When("^I add Points/4 to the geopackage file (@[^\"]*)$")
    public void gpkg_AddFeature(final String fileVariableName) throws Throwable {
        GeoPackageWebAPITestSupport support = new GeoPackageWebAPITestSupport();
        File file = new File(context.getVariable(fileVariableName));
        DataStore gpkgStore = support.createDataStore(file);

        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.addFeatures(DataUtilities.collection(TestData.point4));
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }
    }

    /**
     * Modifies all the Point features in the geopackage file referred to by the provided variable
     * name.
     * 
     * @param fileVariableName the variable which stores the location of the geopackage file.
     */
    @When("^I modify the Point features in the geopackage file (@[^\"]*)$")
    public void gpkg_ModifyFeature(final String fileVariableName) throws Throwable {
        GeoPackageWebAPITestSupport support = new GeoPackageWebAPITestSupport();
        File file = new File(context.getVariable(fileVariableName));
        DataStore gpkgStore = support.createDataStore(file);
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", TestData.point1_modified.getAttribute("ip"), Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }
    }

    /**
     * Sends a POST request with the file in the {@code fileVariableName} variable as the
     * {@code formFieldName} form field to the {@code targetURI}
     */
    @When("^I post (@[^\"]*) as \"([^\"]*)\" to \"([^\"]*)\"$")
    public void gpkg_UploadFile(String fileVariableName, String formFieldName, String targetURI)
            throws Throwable {

        File file = new File(context.getVariable(fileVariableName));
        checkState(file.exists() && file.isFile());

        context.postFile(targetURI, formFieldName, file);
    }

    @When("^I \"([^\"]*)\" content-type \"([^\"]*)\" to \"([^\"]*)\" with$")
    public void requestWithContent(String method, String contentType, String targetURI,
            String content) {
        final String resourceUri = context.replaceVariables(targetURI);
        final String requestContent = context.replaceVariables(content);
        switch (method) {
            case "PUT":
                context.callInternal(HttpMethod.PUT, resourceUri, requestContent, contentType);
                break;
            case "POST":
                context.callInternal(HttpMethod.POST, resourceUri, requestContent, contentType);
                break;
            default:
                fail("Unsupported request method: " + method);
        }
    }

    /**
     * Saves the response as a variable.
     * <p>
     * {@link #callURL(String)} will decode the variable and replace it by its value before issuing
     * the request.
     *
     * @param variableName the name of the variable to save the response as
     */
    @Then("^I save the response as \"([^\"]*)\"$")
    public void saveResponseAsVariable(final String variableName) {
        context.setVariable(variableName, context.getLastResponseText());
    }

    @Then("^the json object \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkJSONResponse(final String jsonPath, String expected) {
        expected = context.replaceVariables(expected);
        String pathValue = getStringFromJSONResponse(jsonPath);
        assertEquals("JSON Response doesn't match", expected, pathValue);
    }

    @Then("^the json object \"([^\"]*)\" ends with \"([^\"]*)\"$")
    public void checkJSONResponseEndsWith(final String jsonPath, final String expected) {
        String pathValue = getStringFromJSONResponse(jsonPath);
        assertTrue("JSON Response doesn't end with '" + expected + "'",
                pathValue.endsWith(expected));
    }

    @Then("^the json response \"([^\"]*)\" should contain \"([^\"]*)\"$")
    public void checkJSONResponseContains(final String jsonPath, final String attribute) {
        String response = getStringFromJSONResponse(jsonPath);
        assertTrue("JSON Response missing \"" + attribute + "\"", response != null);
    }

    @Then("^the json response \"([^\"]*)\" contains an empty \"([^\"]*)\" array$")
    public void checkJSONResponseContainsEmptyArray(final String jsonPath, final String attribute) {
        JsonObject response = getObjectFromJSONResponse(jsonPath);
        JsonArray array = response.getJsonArray(attribute);
        assertTrue("JSON Response contains non-empty array \"" + attribute + "\"", array.isEmpty());
    }

    @Then("^the json response \"([^\"]*)\" should contain \"([^\"]*)\" (\\d+) times$")
    public void checkJSONResponseContains(final String jsonArray, final String attribute,
            final int count) {
        JsonArray response = getArrayFromJSONResponse(jsonArray);
        int arrayCount = 0;
        for (int i = 0; i < response.size(); ++i) {
            JsonObject arrayObj = response.getJsonObject(i);
            if (arrayObj.getString(attribute, null) != null) {
                ++arrayCount;
            }
        }
        assertEquals("JSON Response doesn't contain expected response correct number of times",
                count, arrayCount);
    }

    @Then("^the JSON task (@[^\"]*) description contains \"([^\"]*)\"$")
    public void theJsonTaskTaskIdDescriptionContains(final String taskIdVariable,
            String descriptionSubstring) throws Throwable {

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        checkAsyncTaskAsJson(taskId);
        final String substring = context.replaceVariables(descriptionSubstring);
        final String description = getStringFromJSONResponse("task.description");
        assertEquals("Description did not match", substring, description);
    }

    /**
     * Saves the value of an XPath expression over the last response's XML as a variable.
     * <p>
     * {@link #callURL(String)} will decode the variable and replace it by its value before issuing
     * the request.
     *
     * @param jsonPath the expression to evalue from the last response
     * @param variableName the name of the variable to save the xpath expression value as
     */
    @Then("^I save the json response \"([^\"]*)\" as \"([^\"]*)\"$")
    public void saveResponseJSONValueAsVariable(final String jsonPath, final String variableName) {

        String jsonValue = getStringFromJSONResponse(jsonPath);
        context.setVariable(variableName, jsonValue);
    }

    @Then("^the response is a JSON async task (@[^\"]*)$")
    public void checkResponseIsAJsonAsyncTask(String taskIdVariable) {
        checkNotNull(taskIdVariable);

        assertEquals("application/json", context.getLastResponseContentType());
        assertJsonIsAsyncTask();
        final String taskId = getStringFromJSONResponse("task.id");

        context.setVariable(taskIdVariable, taskId);
    }

    @Then("^when the JSON task (@[^\"]*) finishes$")
    public void waitForAsyncJsonTaskToFinish(String taskIdVariable) throws Throwable {
        checkNotNull(taskIdVariable);

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));

        AsyncContext.Status status = AsyncContext.Status.WAITING;
        do {
            Thread.sleep(100);
            checkAsyncTaskAsJson(taskId);
            assertJsonIsAsyncTask();
            status = AsyncContext.Status.valueOf(getStringFromJSONResponse("task.status"));
        } while (!status.isTerminated());
    }

    @Then("^the JSON task (@[^\"]*) status is ([^\"]*)$")
    public void checkAsyncJsonTaskStatus(String taskIdVariable, AsyncContext.Status status)
            throws Throwable {
        checkNotNull(taskIdVariable);
        checkNotNull(status);

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        checkAsyncTaskAsJson(taskId);
        assertJsonIsAsyncTask();
        final String jsonStatus = getStringFromJSONResponse("task.status");
        assertEquals("Task Status unexpected", status.toString(), jsonStatus);
    }

    @Then("^the JSON task (@[^\"]*) result contains \"([^\"]*)\" with value \"([^\"]*)\"$")
    public void theJsonTaskTaskIdResultContainsWithValue(final String taskIdVariable,
            String jsonPath, String expectedValueSubString) throws Throwable {

        final Integer taskId = Integer.valueOf(context.getVariable(taskIdVariable));
        checkAsyncTaskAsJson(taskId);
        final String substring = context.replaceVariables(expectedValueSubString);
        final String taskResult = getStringFromJSONResponse(jsonPath);
        assertTrue("Task result did not match", taskResult.contains(substring));
    }

    @When("^I call \"([^\"]*)\" with the System Temp Directory as the parentDirectory$")
    public void callURLWithJSONPaylod(final String methodAndURL) throws JsonException, IOException {
        // build JSON payload
        JsonObject payload = toJSON("{\"parentDirectory\":\"" + systemTempPath() + "\"}");
        callURLWithJSONPayload(methodAndURL, payload);
    }

    @Then("^the parent directory of repository \"([^\"]*)\" equals System Temp directory$")
    public void checkRepositoryParent(final String repo) throws Exception {
        Repository geogig = context.getRepo(repo);
        final Optional<URI> repoLocation = geogig.command(ResolveGeogigURI.class).call();
        assertTrue("Expected Repository location to be present", repoLocation.isPresent());
        URI repoURI = repoLocation.get();
        assertEquals("Unexpected URI scheme", "file", repoURI.getScheme());
        // parent of the repo is the directory that contains the ".geogig" directory.
        // the parent of the parent of the repo is the directory that the user specifies in the Init
        // request.
        String parentDir = new File(repoURI).getParentFile().getParentFile().getCanonicalPath()
                .replace("\\", "/");
        assertEquals("Unexpected parent directory", systemTempPath(), parentDir);
    }

    private void callURLWithJSONPayload(final String methodAndURL, JsonObject payload)
            throws JsonException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        HttpMethod method = HttpMethod.valueOf(httpMethod);
        context.call(method, resourceUri, payload.toString(), MediaType.APPLICATION_JSON_VALUE);
    }

    @Then("^the json response \"([^\"]*)\" attribute \"([^\"]*)\" should each contain \"([^\"]*)\"$")
    public void checkJsonArrayContains(final String jsonArray, final String attribute,
            final String expected) {
        JsonArray array = getArrayFromJSONResponse(jsonArray);
        for (JsonObject obj : array.getValuesAs(JsonObject.class)) {
            String actual = obj.getString(attribute);
            assertTrue("JSON response doesn't contain expected value, has: " + actual,
                    actual.contains(expected));
        }
    }

    @Then("^I save the first href link from \"([^\"]*)\" as \"([^\"]*)\"$")
    public void saveHrefLinkFromJSONResponse(final String jsonArray, final String href)
            throws JsonException {
        // get the first href link from the response
        JsonArray array = getArrayFromJSONResponse(jsonArray);
        JsonObject obj = array.getJsonObject(0);
        String link = obj.getString("href");
        // strip everything up to "repos" off the front of the href link
        String linkEnd = link.substring(link.indexOf("/repos"));
        // store the linkEnd
        context.setVariable(href, linkEnd);
    }

    @When("^I call \"([^\"]*)\" with a URL encoded Form containing a parentDirectory parameter$")
    public void callURLWithFormPaylod(final String methodAndURL) throws JsonException, IOException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        HttpMethod method = HttpMethod.valueOf(httpMethod);
        // build URL encoded Form
        context.call(method, resourceUri, "parentDirectory="+systemTempPath(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @Then("^the Author config of repository \"([^\"]*)\" is set$")
    public void checkAuthorConfig(final String repo) throws Exception {
        Repository geogig = context.getRepo(repo);
        final Optional<URI> repoLocation = geogig.command(ResolveGeogigURI.class).call();
        assertTrue("Expected Repository location to be present", repoLocation.isPresent());
        // get the config
        Optional<Map<String, String>> optConfig = geogig.command(ConfigOp.class)
                .setAction(CONFIG_LIST).setScope(LOCAL).call();
        // asseert the user.name and user.email config
        assertTrue("GeoGig repo config missing", optConfig.isPresent());
        Map<String, String> config = optConfig.get();
        assertTrue("\"user.name\" missing in repository config", config.containsKey("user.name"));
        assertEquals("GeoGig User", config.get("user.name"));

        assertTrue("\"user.email\" missing in repository config", config.containsKey("user.email"));
        assertEquals("geogig@geogig.org", config.get("user.email"));
        // add the repo to the set so it can be closed
        openedRepos.add(repo);
    }

    @When("^I call \"([^\"]*)\" with Author and the System Temp Directory as the parentDirectory$")
    public void callURLWithJSONPayloadAndAuthor(final String methodAndURL)
            throws JsonException, IOException {
        // build the JSON payload
        JsonObject payload = Json.createObjectBuilder().add("parentDirectory", systemTempPath())
                .add("authorName", "GeoGig User").add("authorEmail", "geogig@geogig.org").build();
        callURLWithJSONPayload(methodAndURL, payload);
    }

    @When("^I call \"([^\"]*)\" with a URL encoded Form containing a parentDirectory parameter and Author$")
    public void callURLWithFormPaylodWithAuthor(final String methodAndURL)
            throws JsonException, IOException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        HttpMethod method = HttpMethod.valueOf(httpMethod);
        // build URL encoded Form
        StringBuilder form = new StringBuilder();
        form.append("parentDirectory=").append(systemTempPath()).append("&")
                .append("authorName=GeoGig User&")
                .append("authorEmail=geogig@geogig.org");
        context.call(method, resourceUri, form.toString(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @Then("^the parent directory of repository \"([^\"]*)\" is NOT the System Temp directory$")
    public void checkRepositoryParent2(final String repo) throws Exception {
        Repository geogig = context.getRepo(repo);
        final Optional<URI> repoLocation = geogig.command(ResolveGeogigURI.class).call();
        assertTrue("Expected Repository location to be present", repoLocation.isPresent());
        URI repoURI = repoLocation.get();
        assertEquals("Unexpected URI scheme", "file", repoURI.getScheme());
        // parent of the repo is the directory that contains the ".geogig" directory.
        // the parent of the parent of the repo is the directory that the user specifies in the Init
        // request.
        String parentDir = new File(repoURI).getParentFile().getParentFile().getCanonicalPath();
        assertNotEquals("Unexpected parent directory", systemTempPath(), parentDir);
        // add the repo to the set so it can be closed
        openedRepos.add(repo);
    }

    @When("^I call \"([^\"]*)\" with an unsupported media type$")
    public void callURLWithUnsupportedMediaType(final String methodAndURL)
            throws JsonException, IOException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        HttpMethod method = HttpMethod.valueOf(httpMethod);
        // build the JSON payload
        JsonObject payload = Json.createObjectBuilder().add("parentDirectory", systemTempPath())
                .add("authorName", "GeoGig User").add("authorEmail", "geogig@geogig.org").build();
        context.call(method, resourceUri, payload.toString(), "application/xml");
    }

    @Then("^there should be no \"([^\"]*)\" created$")
    public void checkRepoNotInitialized(final String repo) throws Exception {
        Repository geogig = context.getRepo(repo);
        assertTrue("Expected repository to NOT EXIST", null == geogig);
        // add the repo to the set so it can be closed
        openedRepos.add(repo);
    }

    @Given("^I have disabled backends: \"([^\"]*)\"$")
    public void i_have_plugin_without_backend(final String backendsToRemove) {
        assertNotNull("Backend resolver class name(s) should be provided", backendsToRemove);
        // parse the backends list
        final String[] backends = backendsToRemove.split(",");
        final ArrayList<String> backendList = new ArrayList<>(2);
        for (String backendToRemove : backends) {
            switch (backendToRemove.trim()) {
                case "Directory" :
                    backendList.add("org.locationtech.geogig.repository.impl.FileRepositoryResolver");
                    break;
                case "PostgreSQL" :
                    backendList.add("org.locationtech.geogig.storage.postgresql.PGRepositoryResolver");
                    break;
            }
        }
        // add the list to the test utility
        RepositoryResolverTestUtil.setDisabledResolvers(backendList);
    }

    String systemTempPath() throws IOException {
        return context.tempFolder.getRoot().getCanonicalPath().replace("\\", "/");
    }

    /**
     * Extracts the String representation of a JSON object response. The supplied <b>jsonPath</b>
     * should use a period(.) as the object delimeter. For example:<br>
     *
     * <pre>
     * {@code
     *     {
     *         "response" : {
     *             "success": "true",
     *             "repo": {
     *                 "name": "repo1",
     *                 "href": "http://localhost:8080/geoserver/geogig/repos/repo1.json"
     *             }
     *         }
     *     }
     * }
     * </pre>
     *
     * To access the <b>success</b> value, the String "response.success" should be passed in.
     * <p>
     * To access the <b>name</b> value, the String "response.repo.name" should be passed in.
     *
     * @param jsonPath A String representing the value desired.
     *
     * @return A String representation of the value of the object denoted by the jsonPath.
     *
     */
    private String getStringFromJSONResponse(String jsonPath) {
        final String response = context.getLastResponseText();
        final JsonObject jsonResponse = toJSON(response);
        // find the JSON object
        final String[] paths = jsonPath.split("\\.");
        String subPath;
        JsonObject path = jsonResponse;
        for (int i = 0; i < paths.length - 1; ++i) {
            subPath = paths[i];
            if (subPath.contains("[")) {
                int index = Integer.parseInt(
                        subPath.substring(subPath.indexOf('[') + 1, subPath.indexOf(']')));
                path = path.getJsonArray(subPath.substring(0, subPath.indexOf('[')))
                        .getJsonObject(index);
            } else {
                // drill down
                path = path.getJsonObject(paths[i]);
            }
        }
        final String key = paths[paths.length - 1];
        final JsonValue value = path.get(key);
        return getString(value);
    }

    private JsonObject getObjectFromJSONResponse(String jsonPath) {
        String response = context.getLastResponseText();
        final JsonObject jsonResponse = toJSON(response);
        // find the JSON object
        final String[] paths = jsonPath.split("\\.");
        JsonObject path = jsonResponse;
        for (int i = 0; i < paths.length; ++i) {
            // drill down
            path = path.getJsonObject(paths[i]);
        }
        return path;
    }

    private JsonArray getArrayFromJSONResponse(String jsonPath) {
        String response = context.getLastResponseText();
        final JsonObject jsonResponse = toJSON(response);
        // find the JSON object
        final String[] paths = jsonPath.split("\\.");
        JsonObject path = jsonResponse;
        for (int i = 0; i < paths.length - 1; ++i) {
            // drill down
            path = path.getJsonObject(paths[i]);
        }
        final String key = paths[paths.length - 1];
        return path.getJsonArray(key);
    }

    private String getString(JsonValue value) {
        switch (value.getValueType()) {
        case NULL:
            return "null";
        case FALSE:
            return "false";
        case TRUE:
            return "true";
        case STRING:
            JsonString val = JsonString.class.cast(value);
            return val.getString();
        default:
            return value.toString();
        }
    }

    private void checkAsyncTaskAsJson(final Integer taskId) throws IOException {
        String url = String.format("/tasks/%d.json", taskId);
        context.call(HttpMethod.GET, url);
    }

    private void assertJsonIsAsyncTask() {
        final String taskId = getStringFromJSONResponse("task.id");
        assertNotNull("Task id missing", taskId);
        final String taskStatus = getStringFromJSONResponse("task.status");
        assertNotNull("Task status missing", taskStatus);
        final String taskDescription = getStringFromJSONResponse("task.description");
        assertNotNull("Task Description missing", taskDescription);
    }
}
