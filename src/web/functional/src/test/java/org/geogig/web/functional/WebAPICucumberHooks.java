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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.repository.GeogigTransaction;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geopkg.GeoPackageWebAPITestSupport;
import org.locationtech.geogig.web.api.TestData;
import org.mortbay.log.Log;
import org.opengis.filter.Filter;
import org.restlet.data.Method;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.matchers.CompareMatcher;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.matchers.HasXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

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

    @Inject
    public WebAPICucumberHooks(FunctionalTestContext context) {
        this.context = context;
    }

    @cucumber.api.java.Before
    public void before() throws Exception {
        context.before();
    }

    @cucumber.api.java.After
    public void after() {
        context.after();
    }

    ///////////////// repository initialization steps ////////////////////

    @Given("^There is an empty multirepo server$")
    public void setUpEmptyMultiRepo() {
    }

    @Given("^There is a default multirepo server$")
    public void setUpDefaultMultiRepo() throws Exception {
        setUpEmptyMultiRepo();
        context.setUpDefaultMultiRepoServer();
    }

    @Given("^There is an empty repository named ([^\"]*)$")
    public void setUpEmptyRepo(String name) throws Throwable {
        String repoUri = "/repos/" + name;
        String urlSpec = repoUri + "/init";
        context.call(Method.PUT, urlSpec);
        assertStatusCode(Status.SUCCESS_CREATED.getCode());

        context.call(Method.POST, repoUri + "/config?name=user.name&value=webuser");
        context.call(Method.POST, repoUri + "/config?name=user.email&value=webuser@test.com");
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
    @Then("^the ([^\"]*) repository's ([^\"]*) should have the following features:$")
    public void verifyRepositoryContents(String repositoryName, String headRef,
            DataTable expectedFeatures) throws Throwable {

        SetMultimap<String, String> expected = HashMultimap.create();
        {
            List<Map<String, String>> asMaps = expectedFeatures.asMaps(String.class, String.class);
            for (Map<String, String> featureMap : asMaps) {
                for (Entry<String, String> entry : featureMap.entrySet()) {
                    if (entry.getValue().length() > 0) {
                        expected.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        SetMultimap<String, String> actual = context.listRepo(repositoryName, headRef);

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
        Method method = Method.valueOf(httpMethod);
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
        GeogigTransaction transaction = new GeogigTransaction(repo.context(),
                UUID.fromString(context.getVariable(variableName)));
        repo.command(TransactionEnd.class).setTransaction(transaction).call();
    }

    /**
     * Removes Points/1 from the repository.
     * 
     * @param repoName the repository to remove the feature from.
     */
    @When("^I remove Points/1 from \"([^\"]*)\"$")
    public void removeFeature(final String repoName) throws Exception {
        Repository repo = context.getRepo(repoName);
        TestData data = new TestData(repo);
        data.remove(TestData.point1);
        data.add();
        data.commit("Removed point1");
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
        Status status = Status.valueOf(context.getLastResponseStatus());
        Status expected = Status.valueOf(statusCode);
        assertEquals(format("Expected status code %s, but got %s", expected, status), statusCode,
                status.getCode());
    }

    @Then("^the response ContentType should be \"([^\"]*)\"$")
    public void checkContentType(final String expectedContentType) {
        String actualContentType = context.getLastResponseContentType();
        assertTrue(actualContentType.contains(expectedContentType));
    }

    /**
     * Checks that the response {@link Response#getAllowedMethods() allowed methods} match the given
     * list.
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

        assertEquals(expected, allowedMethods);
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

        Document dom = context.getLastResponseAsDom();
        Source source = new DOMSource(dom);

        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(NSCONTEXT);

        List<Node> nodes = Lists.newArrayList(xpathEngine.selectNodes(xpathExpression, source));
        assertEquals(times, nodes.size());
    }

    @Then("^the response body should contain \"([^\"]*)\"$")
    public void checkResponseTextContains(final String substring) {
        final String responseText = context.getLastResponseText();
        assertThat(responseText, containsString(substring));
    }

    @Then("^the xml response should not contain \"([^\"]*)\"$")
    public void responseDoesNotContainXPath(final String xpathExpression) {

        final String xml = context.getLastResponseText();
        assertThat(xml, xml,
                not(HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(NSCONTEXT)));
    }

    @Then("^the xpath \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkXPathEquals(String xpath, String expectedValue) {

        final String xml = context.getLastResponseText();
        assertXpathEquals(xpath, expectedValue, xml);
    }

    private void assertXpathEquals(String xpath, String expectedValue, final String xml) {
        assertThat(xml, xml, EvaluateXPathMatcher.hasXPath(xpath, equalTo(expectedValue))
                .withNamespaceContext(NSCONTEXT));
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
     *     <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/2.xml" type="application/xml"/>
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

        Log.info("Task %s finished: %s", taskId, status);
    }

    private String getAsyncTaskAsXML(final Integer taskId) throws IOException {
        String url = String.format("/tasks/%d", taskId);
        context.call(Method.GET, url);
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

    ////////////////////// GeoPackage step definitions //////////////////////////

    @Then("^the result is a valid GeoPackage file$")
    public void gpkg_CheckResponseIsGeoPackage() throws Throwable {
        checkContentType(Variants.GEOPKG_MEDIA_TYPE.getName());

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

}
