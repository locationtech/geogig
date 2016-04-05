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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geopkg.GeoPackageTestSupport;
import org.mortbay.log.Log;
import org.restlet.data.Method;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.matchers.CompareMatcher;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.matchers.HasXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 *
 */
@StepDefAnnotation
public class WebAPICucumberHooks {

    public FunctionalTestContext context = new FunctionalTestContext();

    private static final Map<String, String> NSCONTEXT = ImmutableMap.of("atom",
            "http://www.w3.org/2005/Atom");

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
        String urlSpec = "/repos/" + name + "/init";
        Response response = context.callDontSaveResponse(Method.PUT, urlSpec);
        assertStatusCode(response, Status.SUCCESS_CREATED.getCode());
    }

    /**
     * Checks that the repository named {@code repositoryName}, at it's commit {@code headRef}, has
     * the expected features as given by the {@code expectedFeatures} {@link DataTable}.
     * <p>
     * The {@code DataTable} top cells represent feature tree paths, and their cells beneath each
     * feature tree path, the feature ids expected for each layer.
     * <p>
     * Example:
     * 
     * <pre>
     * <code>
     *     |  Points   |  Lines   |  Polygons   | 
     *     |  Points.1 |  Lines.1 |  Polygons.1 | 
     *     |  Points.2 |  Lines.2 |  Polygons.2 | 
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
            asMaps.forEach((m) -> m.forEach((k, v) -> expected.put(k, v)));
        }

        SetMultimap<String, String> actual = context.listRepo(repositoryName, headRef);

        assertEquals(expected, actual);
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
        // System.err.println(methodAndURL);
        context.call(method, resourceUri);
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
        Response response = context.getLastResponse();
        assertStatusCode(response, statusCode);
    }

    private void assertStatusCode(Response response, final int statusCode) {
        Status status = response.getStatus();
        Status expected = Status.valueOf(statusCode);
        assertEquals(format("Expected status code %s, but got %s", expected, status), statusCode,
                status.getCode());
    }

    @Then("^the response ContentType should be \"([^\"]*)\"$")
    public void checkContentType(final String expectedContentType) {
        String actualContentType = context.getLastResponseContentType();
        assertEquals(context.getLastResponseText(), expectedContentType, actualContentType);
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

        Set<Method> expected = Sets.newHashSet(//
                Iterables.transform(//
                        Splitter.on(',').omitEmptyStrings().splitToList(csvMethodList), //
                        (s) -> Method.valueOf(s)//
                )//
        );

        Set<Method> allowedMethods = context.getLastResponse().getAllowedMethods();

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
        assertThat(xml,
                not(HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(NSCONTEXT)));
    }

    @Then("^the xpath \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkXPathEquals(String xpath, String expectedValue) {

        final String xml = context.getLastResponseText();
        assertXpathEquals(xpath, expectedValue, xml);
    }

    private void assertXpathEquals(String xpath, String expectedValue, final String xml) {
        assertThat(xml, EvaluateXPathMatcher.hasXPath(xpath, equalTo(expectedValue))
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
        Response taskResponse = context.callDontSaveResponse(Method.GET, url);
        String text = taskResponse.getEntity().getText();
        // System.err.println(text);
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

        try (InputStream stream = context.getLastResponse().getEntity().getStream()) {
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
        GeoPackageTestSupport support = new GeoPackageTestSupport(context.getTempFolder());
        File dbfile = support.createDefaultTestData();
        context.setVariable(fileVariableName, dbfile.getAbsolutePath());
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
