/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.osm.internal.log.ResolveOSMMappingLogFolder;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class OSMImportOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        repo.configDatabase().put("user.name", "groldan");
        repo.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testImport() throws Exception {
        String filename = getClass().getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").featureCount();
        assertTrue(unstaged > 0);
        unstaged = geogig.getRepository().workingTree().countUnstaged("way").featureCount();
        assertTrue(unstaged > 0);
    }

    @Test
    public void testImportAdd() throws Exception {
        // import two files, using the add option, so the nodes imported by the first one are not
        // removed when adding those from the second one
        String filename = getClass().getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        filename = getClass().getResource("nodes.xml").getFile();
        file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).setAdd(true).call();
        // Check that the working tree contains elements from both imports
        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").featureCount();
        assertEquals(30, unstaged);
        unstaged = geogig.getRepository().workingTree().countUnstaged("way").featureCount();
        assertEquals(4, unstaged);
    }

    @Test
    public void testImportWithMapping() throws Exception {
        String filename = getClass().getResource("ways.xml").getFile();
        File file = new File(filename);

        // Define a mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("oneway", Lists.newArrayList("yes"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("onewaystreets", mappings, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);

        // import with mapping and check import went ok
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).setMapping(mapping)
                .call();
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:way").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        // check that the tree with the mapping exist and is not empty
        tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:onewaystreets")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);

        // check that the mapping was correctly performed
        Optional<Node> feature = geogig.getRepository().workingTree()
                .findUnstaged("onewaystreets/31045880");
        assertTrue(feature.isPresent());
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setObjectId(feature.get().getObjectId()).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        String wkt = "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals("31045880", values.get(0).get().toString());
        assertEquals("yes", values.get(1).get());

    }

    @Test
    public void testImportWithMappingAndNoRaw() throws Exception {
        String filename = getClass().getResource("ways.xml").getFile();
        File file = new File(filename);

        // Define a mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("oneway", Lists.newArrayList("yes"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("onewaystreets", mappings, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);

        // import with mapping and check import went ok and canonical folders were not created
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).setMapping(mapping)
                .setNoRaw(true).call();
        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").featureCount();
        assertEquals(0, unstaged);
        unstaged = geogig.getRepository().workingTree().countUnstaged("way").featureCount();
        assertEquals(0, unstaged);
        unstaged = geogig.getRepository().workingTree().countUnstaged("onewaystreets")
                .featureCount();
        assertEquals(2, unstaged);
        Optional<Node> feature = geogig.getRepository().workingTree()
                .findUnstaged("onewaystreets/31045880");
        assertTrue(feature.isPresent());

        // check that the mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setObjectId(feature.get().getObjectId()).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        String wkt = "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals("31045880", values.get(0).get().toString());
        assertEquals("yes", values.get(1).get());

        // check it has not created mapping log files
        File osmMapFolder = geogig.command(ResolveOSMMappingLogFolder.class).call();
        file = new File(osmMapFolder, "onewaystreets");
        assertFalse(file.exists());
        file = new File(osmMapFolder, geogig.getRepository().workingTree().getTree().getId()
                .toString());
        assertFalse(file.exists());
    }

}
