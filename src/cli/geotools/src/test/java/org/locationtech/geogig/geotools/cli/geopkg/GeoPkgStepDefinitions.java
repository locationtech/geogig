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

import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1_modified;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;

import java.io.File;
import java.util.Arrays;

import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.cli.test.functional.CLIContext;
import org.locationtech.geogig.cli.test.functional.CLIContextProvider;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;

import com.google.common.base.Preconditions;

import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 * Step definitions specific to the geopkg module. Builds on top of the ones in the
 * {@code org.locationtech.geogig.cli.test.functional} package for non geopkg specific ones.
 */
@StepDefAnnotation
public class GeoPkgStepDefinitions {

    private CLIContext localRepo;

    private CLIContextProvider contextProvider;

    private TemporaryFolder tempFolder;

    @cucumber.api.java.Before
    public void before() throws Throwable {
        contextProvider = CLIContextProvider.get();
        contextProvider.before();
        this.localRepo = contextProvider.getOrCreateRepositoryContext("localrepo");
        tempFolder = new TemporaryFolder();
        tempFolder.create();
    }

    @cucumber.api.java.After
    public void after() {
        contextProvider.after();
        tempFolder.delete();
    }

    @When("^I run the command \"([^\"]*)\" on a new GeoPackage file$")
    public void I_run_the_command_on_a_new_GeoPackage_file(String commandSpec) throws Throwable {
        GeoPackageTestSupport support = new GeoPackageTestSupport();
        commandSpec += " --database ";
        commandSpec += support.newFile().getPath();
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

    @When("^I run the command \"([^\"]*)\" on an existing GeoPackage file$")
    public void I_run_the_command_on_an_existing_GeoPackage_file(String commandSpec)
            throws Throwable {
        GeoPackageTestSupport support = new GeoPackageTestSupport();
        commandSpec += " --database ";
        commandSpec += support.createDefaultTestData().getPath();
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

    @When("^I run the command \"([^\"]*)\" on an existing interchange GeoPackage file$")
    public void I_run_the_command_on_an_existing_interchange_GeoPackage_file(String commandSpec)
            throws Throwable {
        localRepo.insertAndAdd(points1, points2, points3);
        localRepo.runCommand(true, "commit -m initial");

        GeoPackageTestSupport support = new GeoPackageTestSupport();

        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(localRepo.geogigCLI);

        DataStore gpkgStore = support.createDataStore(geoPkgFile);
        // Modify a point to the geopackage
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", ((SimpleFeature) points1_modified).getAttribute("ip"),
                    Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        commandSpec += " --database ";
        commandSpec += geoPkgFileName;
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

    @When("^I run the command \"([^\"]*)\" on an existing interchange GeoPackage file with a conflict$")
    public void I_run_the_command_on_an_existing_interchange_GeoPackage_file_with_conflict(
            String commandSpec) throws Throwable {
        localRepo.insertAndAdd(points1, points2, points3);
        localRepo.runCommand(true, "commit -m initial");

        GeoPackageTestSupport support = new GeoPackageTestSupport();

        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(localRepo.geogigCLI);

        DataStore gpkgStore = support.createDataStore(geoPkgFile);
        // Modify a point to the geopackage
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", ((SimpleFeature) points1_modified).getAttribute("ip"),
                    Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        // Remove the point in the repo
        localRepo.deleteAndAdd(points1);
        localRepo.runCommand(true, "commit -m deleted");

        commandSpec += " --database ";
        commandSpec += geoPkgFileName;
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

}
