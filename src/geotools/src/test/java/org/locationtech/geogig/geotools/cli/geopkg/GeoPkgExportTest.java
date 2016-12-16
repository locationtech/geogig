/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.geotools.geopkg.AuditTable;
import org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class GeoPkgExportTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private GeogigCLI cli;

    private GeoPackageTestSupport support;

    @Override
    public void setUpInternal() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        cli.setGeogig(geogig);

        // Add points
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);

        geogig.command(CommitOp.class).call();

        // Add lines
        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);

        geogig.command(CommitOp.class).call();

        support = new GeoPackageTestSupport();
    }

    @Override
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testExport() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        // Verify GeoPackage
        DataStore store = store(geoPkgFile);
        try {
            assertFeatures(store, pointsType.getTypeName(), points1, points2, points3);
        } finally {
            store.dispose();
        }

        deleteGeoPkg(geoPkgFileName);
    }

    @Test
    public void testExportWithNullFeatureType() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = support.newFile().getAbsolutePath();
        exportCommand.args = Arrays.asList(null, "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportWithInvalidFeatureType() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = support.newFile().getAbsolutePath();
        exportCommand.args = Arrays.asList("invalidType", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportToFileThatAlreadyExists() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();

        exportCommand.args = Arrays.asList("WORK_HEAD:Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        exportCommand.args = Arrays.asList("WORK_HEAD:Lines", "Lines");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        // Verify GeoPackage
        DataStore store = store(geoPkgFile);
        try {
            assertFeatures(store, pointsType.getTypeName(), points1, points2, points3);
            assertFeatures(store, linesType.getTypeName(), lines1, lines2, lines3);
        } finally {
            store.dispose();
        }

        deleteGeoPkg(geoPkgFileName);
    }

    @Test
    public void testExportWithNoArgs() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        exportCommand.args = Arrays.asList();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportToFileThatAlreadyExistsWithOverwrite() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        // Write lines to the old points table
        exportCommand.args = Arrays.asList("Lines", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.overwrite = true;
        exportCommand.run(cli);

        // Verify GeoPackage
        DataStore store = store(geoPkgFile);
        try {
            assertFeatures(store, pointsType.getTypeName(), lines1, lines2, lines3);
        } finally {
            store.dispose();
        }

        deleteGeoPkg(geoPkgFileName);
    }

    @Test
    public void testExportInterchangeFormat() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        ObjectId headCommitId = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD")
                .call(RevCommit.class).get().getId();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(cli);

        // Verify GeoPackage
        JDBCDataStore store = (JDBCDataStore) store(geoPkgFile);
        try {
            assertFeatures(store, pointsType.getTypeName(), points1, points2, points3);

            Transaction gttx = new DefaultTransaction();
            try (Connection connection = store.getConnection(gttx);
                    GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
                // Verify audit table
                List<AuditTable> auditTables = metadata.getAuditTables();
                assertEquals(1, auditTables.size());
                AuditTable table = auditTables.get(0);
                assertEquals("Points_audit", table.getAuditTable());
                assertEquals("Points", table.getFeatureTreePath());
                assertEquals("Points", table.getTableName());
                assertEquals(headCommitId, table.getCommitId());
            } finally {
                gttx.close();
            }
        } finally {
            store.dispose();
        }
        deleteGeoPkg(geoPkgFileName);
    }

    private DataStore store(File result) throws InterruptedException, ExecutionException {
        assertNotNull(result);
        return support.createDataStore(result);
    }

    private void assertFeatures(DataStore store, String typeName, Feature... expected)
            throws Exception {
        try (Connection connection = ((JDBCDataStore) store).getConnection(Transaction.AUTO_COMMIT);
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
            Map<String, String> mappings = metadata.getFidMappings(typeName);

            SimpleFeatureSource source = store.getFeatureSource(typeName);
            SimpleFeatureCollection features = source.getFeatures();

            Map<String, Feature> expectedFeatures;
            {
                List<Feature> list = Lists.newArrayList(expected);
                expectedFeatures = Maps.uniqueIndex(list, (f) -> ((SimpleFeature) f).getID());
            }
            Set<String> actualFeatureIDs = new HashSet<String>();
            {
                try (SimpleFeatureIterator fiter = features.features()) {
                    while (fiter.hasNext()) {
                        SimpleFeature feature = fiter.next();
                        actualFeatureIDs.add(mappings.get(feature.getID().split("\\.")[1]));
                    }
                }
            }

            Set<String> expectedFeatureIDs = expectedFeatures.keySet();

            assertEquals(expectedFeatureIDs, actualFeatureIDs);
        }
    }

    private void deleteGeoPkg(String geoPkg) {
        File file = new File(geoPkg);
        if (file.exists()) {
            file.delete();
        }
    }
}
