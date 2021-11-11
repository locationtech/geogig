/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.cli;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.store.FeatureIteratorIterator;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.ql.porcelain.QLSelect;
import org.locationtech.geogig.repository.Repository;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;

import com.google.common.collect.Iterators;

public class QLTestHelper {

    private GeogigCLI cli;

    private Repository repository;

    public QLTestHelper(Repository geogig) {
        this.repository = geogig;
        this.cli = new GeogigCLI(Geogig.of(repository), new Console().disableAnsi());
    }

    public void close() {
        cli.close();
    }

    public SimpleFeatureCollection select(String query) {
        try {
            cli.getConsole().println("Query: " + query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        cli.execute("ql", query);
        SimpleFeatureCollection result = repository.command(QLSelect.class).setStatement(query)
                .call();
        return result;
    }

    public SimpleFeatureCollection selectAndAssert(String query, String... expectedFids) {
        Set<String> fids = new HashSet<>();
        if (expectedFids != null && expectedFids.length > 0) {
            fids.addAll(Arrays.asList(expectedFids));
        }
        return selectAndAssert(query, fids);
    }

    public SimpleFeatureCollection selectAndAssert(String query, Set<String> expectedFids,
            String... expectedAttributes) {

        SimpleFeatureCollection result = select(query);
        Set<String> fids = newHashSet(Iterators.transform(
                new FeatureIteratorIterator<SimpleFeature>(result.features()), (f) -> f.getID()));

        assertEquals(expectedFids, fids);

        if (expectedAttributes != null && expectedAttributes.length > 0) {
            Set<String> returnedAtts = result.getSchema().getAttributeDescriptors().stream()
                    .map(AttributeDescriptor::getLocalName)
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> expectedAtts = new TreeSet<>(Arrays.asList(expectedAttributes));
            assertEquals(expectedAtts, returnedAtts);
        }
        return result;
    }

    public void selectAndAssertCount(String query, int expectedCount) {

        SimpleFeatureCollection result = select(query);

        assertEquals(expectedCount, result.size());

    }

}
