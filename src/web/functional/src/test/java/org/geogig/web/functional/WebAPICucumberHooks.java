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
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.junit.Rule;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.matchers.HasXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 *
 */
@StepDefAnnotation
public class WebAPICucumberHooks {

    private Map<String, String> variables = new HashMap<>();

    @Rule
    public FunctionalTestContext context = new FunctionalTestContext();

    @cucumber.api.java.Before
    public void before() throws Exception {
        context.before();
    }

    @cucumber.api.java.After
    public void after() {
        context.after();
    }

    @Given("^There is an empty multirepo server$")
    public void setUpEmptyMultiRepo() throws Throwable {
    }

    @Given("^There is a default multirepo server$")
    public void setUpDefaultMultiRepo() throws Throwable {
        setUpEmptyMultiRepo();
        context.setUpDefaultMultiRepoServer();
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
     * @throws Throwable
     */
    @When("^I call \"([^\"]*)\"$")
    public void callURL(final String methodAndURL) throws Throwable {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        resourceUri = replaceVariables(resourceUri, this.variables);
        Method method = Method.valueOf(httpMethod);
        context.call(method, resourceUri);
    }

    static String replaceVariables(final String uri, Map<String, String> variables) {
        String resource = uri;
        int varIndex = -1;
        while ((varIndex = resource.indexOf("{@")) > -1) {
            for (int i = varIndex + 1; i < resource.length(); i++) {
                char c = resource.charAt(i);
                if (c == '}') {
                    String varName = resource.substring(varIndex + 1, i);
                    String varValue = variables.get(varName);
                    Preconditions.checkState(varValue != null,
                            "Variable " + varName + " does not exist");

                    String tmp = resource.replace("{" + varName + "}", varValue);
                    resource = tmp;
                    break;
                }
            }
        }
        return resource;
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
            final String variableName) throws Throwable {

        // checkResponseContainsXPath(xpathExpression);

        String xml = context.getLastResponse().getEntityAsDom().getText();
        // System.err.println(xml);

        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("atom", "http://www.w3.org/2005/Atom");

        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(prefix2Uri);

        String xpathValue = xpathEngine.evaluate(xpathExpression,
                new StreamSource(new StringReader(xml)));
        // System.err.println("XPath: " + xpathExpression + ", value: '" + xpathValue + "'");
        this.variables.put(variableName, xpathValue);
    }

    @Then("^the response status should be '(\\d+)'$")
    public void checkStatusCode(final int statusCode) throws Throwable {
        Response response = context.getLastResponse();
        Status status = response.getStatus();
        Status expected = Status.valueOf(statusCode);
        assertEquals(format("Expected status code %s, but got %s", expected, status), statusCode,
                status.getCode());
    }

    @Then("^the response ContentType should be \"([^\"]*)\"$")
    public void checkContentType(final String expectedContentType) throws Throwable {
        Response response = context.getLastResponse();
        MediaType mediaType = response.getEntity().getMediaType();
        String actualContentType = mediaType.getName();
        assertEquals(expectedContentType, actualContentType);
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
    public void checkResponseAllowedMethods(final String csvMethodList) throws Throwable {

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
    public void checkResponseContainsXPath(final String xpathExpression) throws Throwable {

        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("atom", "http://www.w3.org/2005/Atom");

        final String xml = context.getLastResponse().getEntity().getText();
        assertThat(xml, HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(prefix2Uri));
    }

    /**
     * Checks that the given xpath expression is found exactly the expected times in the response
     * xml
     */
    @Then("^the xml response should contain \"([^\"]*)\" (\\d+) times$")
    public void checkXPathCadinality(final String xpathExpression, final int times)
            throws Throwable {

        Document dom = context.getLastResponse().getEntityAsDom().getDocument();
        Source source = new DOMSource(dom);

        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("atom", "http://www.w3.org/2005/Atom");

        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(prefix2Uri);

        List<Node> nodes = Lists.newArrayList(xpathEngine.selectNodes(xpathExpression, source));
        assertEquals(times, nodes.size());
    }

    @Then("^the response body should contain \"([^\"]*)\"$")
    public void checkResponseTextContains(final String substring) throws Throwable {
        final String responseText = context.getLastResponse().getEntity().getText();
        assertThat(responseText, containsString(substring));
    }

    @Then("^the xml response should not contain \"([^\"]*)\"$")
    public void responseDoesNotContainXPath(final String xpathExpression) throws Throwable {

        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("atom", "http://www.w3.org/2005/Atom");

        final String xml = context.getLastResponse().getEntity().getText();
        assertThat(xml,
                not(HasXPathMatcher.hasXPath(xpathExpression).withNamespaceContext(prefix2Uri)));
    }

    @Then("^the xpath \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkXPathEquals(String xpath, String expectedValue) throws Throwable {

        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("atom", "http://www.w3.org/2005/Atom");

        final String xml = context.getLastResponse().getEntity().getText();
        assertThat(xml, EvaluateXPathMatcher.hasXPath(xpath, equalTo(expectedValue))
                .withNamespaceContext(prefix2Uri));
    }
}
