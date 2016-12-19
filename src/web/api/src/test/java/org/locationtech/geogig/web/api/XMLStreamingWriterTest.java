/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 *
 */
public class XMLStreamingWriterTest extends AbstractStreamingWriterTest {

    private static final Map<String, String> NSCONTEXT = ImmutableMap.of("atom",
            "http://www.w3.org/2005/Atom");

    @Override
    protected StreamingWriter createWriter(Writer sink) {
        return new XMLStreamingWriter(sink);
    }

    private Document getDocument(String actualBuffer) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(actualBuffer.getBytes(StandardCharsets.UTF_8)));
    }

    private String getXPath(String[] paths, String part) throws IOException {
        // build an xpath
        StringBuilder xpathBuilder = new StringBuilder("/");
        for (String path : paths) {
            xpathBuilder.append(path).append("/");
        }
        // if part is not null, append it
        if (part != null) {
            xpathBuilder.append(part).append("()");
        } else {
            // remove the trailing slash
            xpathBuilder.deleteCharAt(xpathBuilder.length()-1);
        }
        return xpathBuilder.toString();
    }

    @Override
    protected void verifyInternal(String[] paths, String value, String actualBuffer) throws IOException {
        // build an xpath
        String xpath = getXPath(paths, "text");
        // assert the value matches, or "" (empty String) matches if value is null
        assertThat(actualBuffer, EvaluateXPathMatcher.hasXPath(xpath, equalTo(
                (value != null) ? value : "")).withNamespaceContext(NSCONTEXT));
    }

    private List<Node> getXPathNodes(String[] paths, String actualBuffer) throws IOException,
            ParserConfigurationException, SAXException {
        Document dom = getDocument(actualBuffer);
        Source source = new DOMSource(dom);

        JAXPXPathEngine xpathEngine = new JAXPXPathEngine();
        xpathEngine.setNamespaceContext(NSCONTEXT);

        return Lists.newArrayList(xpathEngine.selectNodes(getXPath(paths, null), source));
    }

    @Override
    protected void verifyArrayInternal(String[] paths, String[] expectedValues, String actualBuffer)
            throws IOException {
        try {
            // get an array of the node values
            List<Node> nodes = getXPathNodes(paths, actualBuffer);
            List<String> nodeValues = Lists.newArrayList();
            for (Node node : nodes) {
                Node child = node.getFirstChild();
                nodeValues.add((child != null) ? child.getNodeValue() : null);
            }
            assertArrayEquals("Array doesn't contain expected values", expectedValues, nodeValues.toArray());
        } catch (ParserConfigurationException | SAXException ex) {
            ex.printStackTrace();
            fail("Error parsing XML");
        }
    }

    @Override
    protected void verifyAttributeInternal(String[] paths, String value, String actualBuffer) throws IOException {
        // the paths array has the node xpath and the attribute name as the last array element
        // get the attribute name
        final String attributeName = paths[paths.length -1];
        // get the node
        // add all but the last path element
        String[] xpath = new String[paths.length -1];
        System.arraycopy(paths, 0, xpath, 0, xpath.length);
        try {
            List<Node> nodes = getXPathNodes(xpath, actualBuffer);
            assertEquals(1, nodes.size());
            // get the node
            Node node = nodes.get(0);
            // get the atrributes
            NamedNodeMap attributes = node.getAttributes();
            Node attribute = attributes.getNamedItem(attributeName);
            assertNotNull(attribute);
            assertEquals(value, attribute.getNodeValue());
        } catch (ParserConfigurationException | SAXException ex) {
            ex.printStackTrace();
            fail("Error parsing XML");
        }
    }

    @Override
    protected void verifyArrayElementInternal(String[] paths, String value, String actualBuffer) throws IOException {
        verifyInternal(paths, value, actualBuffer);
    }

}
