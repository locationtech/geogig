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
import java.util.List;
import java.util.Map;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.ResolveFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.osm.internal.MappingRule.DefaultField;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

public class OSMUnmapOpTest extends RepositoryTestCase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMappingAndUnmappingOfWays() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("node").count();
        assertTrue(unstaged > 0);
        unstaged = workTree.countUnstaged("way").count();
        assertTrue(unstaged > 0);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // map
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("residential"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("name", new AttributeDefinition("name", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("residential", mappings, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:residential/31347480").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:residential/31347480").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(4, values.size());

        // modify a mapped feature. We add a new coordinate to the geometry (0,1) and change the
        // value of 'name' tag to "newvalue"
        ArrayList<Coordinate> coords = Lists.newArrayList(((Geometry) values.get(2).get())
                .getCoordinates());
        coords.add(new Coordinate(0, 1));
        assertEquals(31347480l, values.get(0).get());
        GeometryFactory gf = new GeometryFactory();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder((SimpleFeatureType) featureType.get()
                .type());
        fb.set("geom", gf.createLineString(coords.toArray(new Coordinate[0])));
        fb.set("name", "newname");
        fb.set("id", 31347480l);
        fb.set("nodes", values.get(3).get());
        SimpleFeature newFeature = fb.buildFeature("31347480");
        geogig.getRepository().workingTree().insert("residential", newFeature);
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:residential/31347480").call(RevFeature.class);
        assertTrue(mapped.isPresent());
        values = mapped.get().getValues();
        assertEquals(
                "LINESTRING (7.1960069 50.7399033, 7.195868 50.7399081, 7.1950788 50.739912, 7.1949262 50.7399053, "
                        + "7.1942463 50.7398686, 7.1935778 50.7398262, 7.1931011 50.7398018, 7.1929987 50.7398009, 7.1925978 50.7397889, "
                        + "7.1924199 50.7397781, 0 1)", values.get(2).get().toString());
        assertEquals(31347480l, ((Long) values.get(0).get()).longValue());
        assertEquals("newname", values.get(1).get().toString());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("residential").call();

        // Check that raw OSM data was updated
        // First, we check that the corresponding way has been modified
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:way/31347480").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals(
                "LINESTRING (7.1960069 50.7399033, 7.195868 50.7399081, 7.1950788 50.739912, 7.1949262 50.7399053, "
                        + "7.1942463 50.7398686, 7.1935778 50.7398262, 7.1931011 50.7398018, 7.1929987 50.7398009, 7.1925978 50.7397889, "
                        + "7.1924199 50.7397781, 0 1)", values.get(7).get().toString());
        assertEquals("lit:no|highway:residential|name:newname|oneway:yes", values.get(3).get()
                .toString());

        // now we get the 'nodes' field in the unmapped feature and check take the id of its last
        // node, which refers to the node that we have added to the geometry
        int WAY_NODES_FIELD = 6;
        String nodes = values.get(WAY_NODES_FIELD).get().toString();
        String[] nodeIds = nodes.split(";");
        String newNodeId = nodeIds[nodeIds.length - 1];
        // and we check that the node has been added to the 'node' tree and has the right
        // coordinates.
        Optional<RevFeature> newNode = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/" + newNodeId).call(RevFeature.class);
        assertTrue(newNode.isPresent());
        values = newNode.get().getValues();
        int NODE_GEOM_FIELD = 6;
        assertEquals("POINT (0 1)", values.get(NODE_GEOM_FIELD).get().toString());

    }

    @Test
    public void testMappingAndUnmappingOfWaysFromPolygons() throws Exception {
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

        // delete the original feature
        geogig.getRepository().workingTree().delete("way/31045880");
        Optional<RevFeature> original = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:way/31045880").call(RevFeature.class);
        assertFalse(original.isPresent());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("polygons").call();

        // check that the original way has been recreated
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:way/31045880").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals(
                "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781, 7.1923367 50.7395887)",
                values.get(7).get().toString());

    }

    @Test
    public void testMappingAndUnmappingOfNodes() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();

        // Map
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

        // Modify a node
        GeometryFactory gf = new GeometryFactory();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder((SimpleFeatureType) featureType.get()
                .type());
        fb.set("geom", gf.createPoint(new Coordinate(0, 1)));
        fb.set("name", "newname");
        fb.set("id", 507464799l);
        SimpleFeature newFeature = fb.buildFeature("507464799");
        geogig.getRepository().workingTree().insert("busstops", newFeature);

        // check that it was correctly inserted in the working tree
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(mapped.isPresent());
        values = mapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(2).get().toString());
        assertEquals(507464799l, ((Long) values.get(0).get()).longValue());
        assertEquals("newname", values.get(1).get().toString());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("busstops").call();

        // check that the unmapped node has the changes we introduced
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/507464799").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(6).get().toString());
        assertEquals(
                "bus:yes|public_transport:platform|highway:bus_stop|VRS:ortsteil:Hoholz|name:newname|VRS:ref:68566|VRS:gemeinde:BONN",
                values.get(3).get().toString());
        // check that unchanged nodes keep their attributes
        Optional<RevFeature> unchanged = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/1633594723").call(RevFeature.class);
        values = unchanged.get().getValues();
        assertEquals("14220478", values.get(4).get().toString());
        assertEquals("1355097351000", values.get(2).get().toString());
        assertEquals("2", values.get(1).get().toString());

    }

    @Test
    public void testMappingAndUnmappingOfNodesWithDeletions() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Map
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

        // Delete a node
        geogig.getRepository().workingTree().delete("busstops", "507464799");

        // check that it was correctly deleted in the working tree
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:busstops/507464799").call(RevFeature.class);
        assertFalse(mapped.isPresent());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("busstops").call();

        // check that the node has been deleted in the canonical tree
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/507464799").call(RevFeature.class);
        assertFalse(unmapped.isPresent());

    }

    @Test
    public void testMappingAndUnmappingOfNodesWithAlias() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Map
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("bus_stop"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POINT));
        fields.put("name", new AttributeDefinition("name_alias", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("busstops", mappings, filterExclude, fields, null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

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

        // unmap without having made any changes and check that the canonical folders are not
        // modified
        geogig.command(OSMUnmapOp.class).setPath("busstops").call();
        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("way").count();
        assertEquals(0, unstaged);
        unstaged = workTree.countUnstaged("node").count();
        assertEquals(0, unstaged);

        // Modify a node
        GeometryFactory gf = new GeometryFactory();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder((SimpleFeatureType) featureType.get()
                .type());
        fb.set("geom", gf.createPoint(new Coordinate(0, 1)));
        fb.set("name_alias", "newname");
        fb.set("id", 507464799l);
        SimpleFeature newFeature = fb.buildFeature("507464799");
        geogig.getRepository().workingTree().insert("busstops", newFeature);

        // check that it was correctly inserted in the working tree
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(mapped.isPresent());
        values = mapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(2).get().toString());
        assertEquals(507464799l, ((Long) values.get(0).get()).longValue());
        assertEquals("newname", values.get(1).get().toString());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("busstops").call();

        unstaged = workTree.countUnstaged("node").featureCount();
        assertEquals(1, unstaged);

        // check that the unmapped node has the changes we introduced
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/507464799").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(6).get().toString());
        assertEquals(
                "bus:yes|public_transport:platform|highway:bus_stop|VRS:ortsteil:Hoholz|name:newname|VRS:ref:68566|VRS:gemeinde:BONN",
                values.get(3).get().toString());
        // check that unchanged nodes keep their attributes
        Optional<RevFeature> unchanged = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/1633594723").call(RevFeature.class);
        values = unchanged.get().getValues();
        assertEquals("14220478", values.get(4).get().toString());
        assertEquals("1355097351000", values.get(2).get().toString());
        assertEquals("2", values.get(1).get().toString());

    }

    @Test
    public void testMappingAndUnmappingOfNodesWithDefaultFieds() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // Map
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("bus_stop"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.POINT));
        fields.put("name", new AttributeDefinition("name_alias", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        ArrayList<DefaultField> defaultFields = Lists.newArrayList(DefaultField.tags,
                DefaultField.timestamp);
        MappingRule mappingRule = new MappingRule("busstops", mappings, filterExclude, fields,
                defaultFields);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:busstops/507464799").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(5, values.size());
        String wkt = "POINT (7.1959361 50.739397)";
        assertEquals(wkt, values.get(4).get().toString());
        assertEquals(507464799l, values.get(0).get());

        // Modify a node
        GeometryFactory gf = new GeometryFactory();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder((SimpleFeatureType) featureType.get()
                .type());
        fb.set("geom", gf.createPoint(new Coordinate(0, 1)));
        fb.set("name_alias", "newname");
        fb.set("id", 507464799l);
        fb.set("tags",
                "VRS:gemeinde:BONN|VRS:ortsteil:Hoholz|VRS:ref:68566|bus:yes|highway:bus_stop|name:Gielgen|public_transport:platform");
        fb.set("timestamp", 1355097351000l);
        SimpleFeature newFeature = fb.buildFeature("507464799");
        geogig.getRepository().workingTree().insert("busstops", newFeature);

        // check that it was correctly inserted in the working tree
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(mapped.isPresent());
        values = mapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(4).get().toString());
        assertEquals(507464799l, ((Long) values.get(0).get()).longValue());
        assertEquals("newname", values.get(3).get().toString());
        assertEquals("1355097351000", values.get(2).get().toString());
        assertEquals(
                "VRS:gemeinde:BONN|VRS:ortsteil:Hoholz|VRS:ref:68566|bus:yes|highway:bus_stop|name:Gielgen|public_transport:platform",
                values.get(1).get().toString());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("busstops").call();

        WorkingTree workTree = geogig.getRepository().workingTree();
        long unstaged = workTree.countUnstaged("node").featureCount();
        assertEquals(1, unstaged);

        // check that the unmapped node has the changes we introduced
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/507464799").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals("POINT (0 1)", values.get(6).get().toString());
        assertEquals(
                "bus:yes|public_transport:platform|highway:bus_stop|VRS:ortsteil:Hoholz|name:newname|VRS:ref:68566|VRS:gemeinde:BONN",
                values.get(3).get().toString());
        // check that unchanged nodes keep their attributes
        Optional<RevFeature> unchanged = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/1633594723").call(RevFeature.class);
        values = unchanged.get().getValues();
        assertEquals("14220478", values.get(4).get().toString());
        assertEquals("1355097351000", values.get(2).get().toString());
        assertEquals("2", values.get(1).get().toString());

    }

    @Test
    public void testMappingAndUnmappingOfWaysWithAlias() throws Exception {
        // Import
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("msg").call();
        // map
        Map<String, AttributeDefinition> fields = Maps.newHashMap();
        Map<String, List<String>> mappings = Maps.newHashMap();
        mappings.put("highway", Lists.newArrayList("residential"));
        fields.put("geom", new AttributeDefinition("geom", FieldType.LINESTRING));
        fields.put("name", new AttributeDefinition("name_alias", FieldType.STRING));
        Map<String, List<String>> filterExclude = Maps.newHashMap();
        MappingRule mappingRule = new MappingRule("residential", mappings, filterExclude, fields,
                null);
        List<MappingRule> mappingRules = Lists.newArrayList();
        mappingRules.add(mappingRule);
        Mapping mapping = new Mapping(mappingRules);
        geogig.command(OSMMapOp.class).setMapping(mapping).call();

        // check that mapping was correctly performed
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:residential/31347480").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:residential/31347480").call();
        assertTrue(featureType.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        assertEquals(4, values.size());

        // unmap without having made any changes and check that the canonical folders are not
        // modified
        WorkingTree workTree = geogig.getRepository().workingTree();
        geogig.command(OSMUnmapOp.class)/* .setMapping(mapping) */.setPath("residential").call();
        long unstaged = workTree.countUnstaged("way").count();
        assertEquals(0, unstaged);
        unstaged = workTree.countUnstaged("node").count();
        assertEquals(0, unstaged);

        // modify a mapped feature. We change the value of 'name_alias' tag to "newvalue"
        ArrayList<Coordinate> coords = Lists.newArrayList(((Geometry) values.get(2).get())
                .getCoordinates());
        coords.add(new Coordinate(0, 1));
        assertEquals(31347480l, values.get(0).get());
        GeometryFactory gf = new GeometryFactory();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder((SimpleFeatureType) featureType.get()
                .type());
        fb.set("geom", gf.createLineString(coords.toArray(new Coordinate[0])));
        fb.set("name_alias", "newname");
        fb.set("id", 31347480l);
        fb.set("nodes", values.get(3).get());
        SimpleFeature newFeature = fb.buildFeature("31347480");
        geogig.getRepository().workingTree().insert("residential", newFeature);
        Optional<RevFeature> mapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:residential/31347480").call(RevFeature.class);
        assertTrue(mapped.isPresent());
        values = mapped.get().getValues();
        assertEquals(
                "LINESTRING (7.1960069 50.7399033, 7.195868 50.7399081, 7.1950788 50.739912, 7.1949262 50.7399053, "
                        + "7.1942463 50.7398686, 7.1935778 50.7398262, 7.1931011 50.7398018, 7.1929987 50.7398009, 7.1925978 50.7397889, "
                        + "7.1924199 50.7397781, 0 1)", values.get(2).get().toString());
        assertEquals(31347480l, ((Long) values.get(0).get()).longValue());
        assertEquals("newname", values.get(1).get().toString());

        // unmap
        geogig.command(OSMUnmapOp.class).setPath("residential")/* .setMapping(mapping) */.call();

        // Check that raw OSM data was updated
        // First, we check that the corresponding way has been modified
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:way/31347480").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        values = unmapped.get().getValues();
        assertEquals(
                "LINESTRING (7.1960069 50.7399033, 7.195868 50.7399081, 7.1950788 50.739912, 7.1949262 50.7399053, "
                        + "7.1942463 50.7398686, 7.1935778 50.7398262, 7.1931011 50.7398018, 7.1929987 50.7398009, 7.1925978 50.7397889, "
                        + "7.1924199 50.7397781, 0 1)", values.get(7).get().toString());
        assertEquals("lit:no|highway:residential|name:newname|oneway:yes", values.get(3).get()
                .toString());

        // now we get the 'nodes' field in the unmapped feature and check the id of its last
        // node, which refers to the node that we have added to the geometry
        int WAY_NODES_FIELD = 6;
        String nodes = values.get(WAY_NODES_FIELD).get().toString();
        String[] nodeIds = nodes.split(";");
        String newNodeId = nodeIds[nodeIds.length - 1];
        // and we check that the node has been added to the 'node' tree and has the right
        // coordinates.
        Optional<RevFeature> newNode = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/" + newNodeId).call(RevFeature.class);
        assertTrue(newNode.isPresent());
        values = newNode.get().getValues();
        int NODE_GEOM_FIELD = 6;
        assertEquals("POINT (0 1)", values.get(NODE_GEOM_FIELD).get().toString());

    }

    @Test
    public void testUnmappingWithoutIDAttribute() throws Exception {
        insert(points1);
        try {
            geogig.command(OSMUnmapOp.class).setPath("Points").call();
            fail();
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().startsWith("No 'id' attribute found"));
        }

    }

    @Override
    protected void setUpInternal() throws Exception {
        // TODO Auto-generated method stub

    }

}
