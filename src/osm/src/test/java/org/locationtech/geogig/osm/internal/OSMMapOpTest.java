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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.ResolveFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.osm.internal.MappingRule.DefaultField;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class OSMMapOpTest extends RepositoryTestCase {
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
    public void testMappingWays() throws Exception {
        // import and check that we have both ways and nodes
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        final File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        filter.put("oneway", Lists.newArrayList("yes"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("onewaystreets", filter, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:onewaystreets/31045880").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(4, values.size());
        String wkt = "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals("yes", values.get(1).get());

        // Check that the corresponding log files have been added
        BlobStore blobStore = getRepository().blobStore();
        Optional<String> blob = Blobs.getBlobAsString(blobStore, "osm/map/onewaystreets");
        assertTrue(blob.isPresent());
        blob = Blobs.getBlobAsString(blobStore, "osm/map/"
                + getRepository().workingTree().getTree().getId());
        assertTrue(blob.isPresent());
    }

    @Test
    public void testMappingDefaultFields() throws Exception {
        // import and check that we have both ways and nodes
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        filter.put("oneway", Lists.newArrayList("yes"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        ArrayList<DefaultField> defaultFields = Lists.newArrayList();
        defaultFields.add(DefaultField.timestamp);
        defaultFields.add(DefaultField.visible);
        MappingRule mappingRule = new MappingRule("onewaystreets", filter, null, fields,
                defaultFields);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:onewaystreets/31045880").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(6, values.size());
        String wkt = "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781)";
        assertEquals(wkt, values.get(4).get().toString());
        assertEquals("yes", values.get(3).get());
        assertEquals(true, values.get(2).get());
        assertEquals(1318750940000L, values.get(1).get());
        Optional<RevFeatureType> revFeatureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:onewaystreets/31045880").call();
        assertTrue(revFeatureType.isPresent());
        ImmutableList<PropertyDescriptor> descriptors = revFeatureType.get().sortedDescriptors();
        assertEquals("timestamp", descriptors.get(1).getName().toString());
        assertEquals("visible", descriptors.get(2).getName().toString());

    }

    @Test
    public void testMappingOnlyClosedPolygons() throws Exception {
        // import and check that we have both ways and nodes
        String filename = OSMImportOp.class.getResource("ways_restriction.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        filter.put("geom", Lists.newArrayList("closed"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POLYGON));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("polygons", filter, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:polygons/31045880").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(4, values.size());
        String wkt = "POLYGON ((7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781, 7.1923367 50.7395887))";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals("yes", values.get(1).get());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:polygons/24777894")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

    }

    @Test
    public void testExcludePoligonsWithLessThan3Points() throws Exception {
        // import and check that we have both ways and nodes
        String filename = OSMImportOp.class.getResource("ways_restriction.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        fields.put("geom", new AttributeDefinition("geom", FieldType.POLYGON));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("polygons", filter, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:polygons/31045880").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:polygons/24777894")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:polygons/51502277")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

    }

    @Test
    public void testMappingOnlyOpenLines() throws Exception {
        // import and check that we have both ways and nodes
        String filename = OSMImportOp.class.getResource("ways_restriction.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        filter.put("geom", Lists.newArrayList("open"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("nonclosed", filter, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:nonclosed/31045880").call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:nonclosed/24777894")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:nonclosed/51502277")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());

    }

    @Test
    public void testMappingNodes() throws Exception {
        // import and check that we have nodes
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("bus_stop"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POINT));
        fields.put("name", new AttributeDefinition("name", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("busstops", mappings, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:busstops/507464799").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(3, values.size());
        String wkt = "POINT (7.1959361 50.739397)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals(507464799l, values.get(0).get());

        // Check that the corresponding log files have been added
        BlobStore blobStore = getRepository().blobStore();
        Optional<String> blob = Blobs.getBlobAsString(blobStore, "osm/map/busstops");
        assertTrue(blob.isPresent());
        blob = Blobs.getBlobAsString(blobStore, "osm/map/"
                + getRepository().workingTree().getTree().getId());
        assertTrue(blob.isPresent());
    }

    @Test
    public void testMappingWithExclusion() throws Exception {
        // import and check that we have nodes
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filter = Maps.newHashMap();
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        filter.put("highway", Lists.newArrayList("bus_stop"));
        filterExclude.put("public_transport", Lists.newArrayList("stop_position"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POINT));
        fields.put("name", new AttributeDefinition("name", FieldType.STRING));
        fields.put("name", new AttributeDefinition("name", FieldType.STRING));
        MappingRule mappingRule = new MappingRule("busstops", filter, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:busstops/507464799").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(3, values.size());
        String wkt = "POINT (7.1959361 50.739397)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals(507464799l, values.get(0).get());

        // Check that the excluded feature is missing
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:busstops/507464865")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
    }

    @Test
    public void testMappingNodesWithAlias() throws Exception {
        // import and check that we have nodes
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Define mapping
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("bus_stop"));
        fields.put("geom", new AttributeDefinition("the_geometry", FieldType.POINT));
        fields.put("name", new AttributeDefinition("the_name", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("busstops", mappings, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // Check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:busstops/507464799").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        ImmutableList<PropertyDescriptor> descriptors = featureType.get().sortedDescriptors();
        assertEquals("the_name", descriptors.get(1).getName().getLocalPart());
        assertEquals("Gielgen", values.get(1).get());
        assertEquals("the_geometry", descriptors.get(2).getName().getLocalPart());

    }

    @Test
    public void testMappingwithNoGeometry() throws Exception {
        // Test that an exception is thrown when the mapping does not contain a geometry field

        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Define a wrong mapping without geometry
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filters = Maps.newHashMap();
        filters.put("oneway", Lists.newArrayList("yes"));
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("onewaystreets", filters, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);

        // Try to create a mapping
        try {
            geogig.command(OSMMapOp.class).setMapping(mapping).call();
            fail();
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().startsWith(
                    "The mapping rule does not define a geometry field"));
        }
    }

    @Test
    public void testMappingWithNoFilter() throws Exception {
        // Test that if no filter is specified in a mapping rule, all entities pass the filter

        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filters = Maps.newHashMap();
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("allways", filters, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();
        Iterator<NodeRef> allways = geogig.command(LsTreeOp.class).setReference("HEAD:allways")
                .call();
        assertTrue(allways.hasNext());
        Iterator<NodeRef> ways = geogig.command(LsTreeOp.class).setReference("HEAD:allways").call();
        ArrayList<NodeRef> listWays = Lists.newArrayList(ways);
        ArrayList<NodeRef> listAllways = Lists.newArrayList(allways);
        assertEquals(listWays.size(), listAllways.size());
    }

    @Test
    public void testMappingWithEmptyTagValueList() throws Exception {
        // Test that when no tags are specified, all entities pass the filter

        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> filters = Maps.newHashMap();
        fields.put("lit", new AttributeDefinition("lit", FieldType.STRING));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POINT));
        filters.put("highway", new ArrayList<String>());
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("mapped", filters, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();
        Iterator<NodeRef> iter = geogig.command(LsTreeOp.class).setReference("HEAD:mapped").call();
        ArrayList<NodeRef> list = Lists.newArrayList(iter);
        assertEquals(4, list.size());
    }

}
