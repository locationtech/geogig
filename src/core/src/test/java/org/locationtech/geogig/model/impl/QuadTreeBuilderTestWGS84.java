/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.internal.QuadTreeTestSupport;
import org.locationtech.geogig.plumbing.index.IndexTestSupport;
import org.locationtech.jts.geom.Envelope;

public class QuadTreeBuilderTestWGS84 extends QuadTreeBuilderTest {

    @Override
    protected Envelope createMaxBounds() {
        return RevObjects.makePrecise(QuadTreeTestSupport.wgs84Bounds());
    }

    public @Test void testRemoveWorldPoints() {
        List<Node> nodes = IndexTestSupport.createWorldPointsNodes(5);
        final RevTreeBuilder builder = createBuiler();
        nodes.forEach((n) -> assertTrue(builder.put(n)));
        RevTree tree = builder.build();
        assertEquals(nodes.size(), tree.size());

        List<Node> removes = nodes.subList(0, nodes.size() / 2);
        final RevTreeBuilder builder2 = createBuiler(tree);

        removes.forEach((n) -> assertTrue(builder2.remove(n)));

        RevTree tree2 = builder2.build();
        assertEquals(nodes.size() - removes.size(), tree2.size());
    }

    public @Test void testRemoveWorldPointsWholeQuadrant() {
        List<Node> nodes = IndexTestSupport.createWorldPointsNodes(5);
        final RevTreeBuilder builder = createBuiler();
        nodes.forEach((n) -> assertTrue(builder.put(n)));
        RevTree tree = builder.build();
        assertEquals(nodes.size(), tree.size());

        final Envelope NEBounds = new Envelope(0, 180, 0, 90);
        List<Node> removes = new ArrayList<>();
        for (Node n : nodes) {
            if (NEBounds.contains(n.bounds().orNull())) {
                removes.add(n);
            }
        }
        final RevTreeBuilder builder2 = createBuiler(tree);

        removes.forEach((n) -> assertTrue(builder2.remove(n)));

        RevTree tree2 = builder2.build();
        assertEquals(nodes.size() - removes.size(), tree2.size());
    }
}
