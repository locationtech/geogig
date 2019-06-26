/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.test.functional;

import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1_FTmodified;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;

import org.locationtech.geogig.cli.test.functional.CLIContext;
import org.locationtech.geogig.cli.test.functional.CLIContextProvider;

import cucumber.api.Scenario;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 * Step definitions specific to the geotools module. Builds on top of the ones in the
 * {@code org.locationtech.geogig.cli.test.functional} package for non geotools specific ones.
 */
@StepDefAnnotation
public class GeoToolsStepDefinitions {

    private CLIContext localRepo;

    private CLIContextProvider contextProvider;

    @cucumber.api.java.Before
    public void before(Scenario scenario) throws Throwable {
        contextProvider = CLIContextProvider.get();
        contextProvider.before(scenario);
        this.localRepo = contextProvider.getOrCreateRepositoryContext("localrepo");
    }

    @cucumber.api.java.After
    public void after(Scenario scenario) {
        contextProvider.after(scenario);
    }

    private String getPGDatabaseParameters(String commandSpec) throws Exception {
        IniPGProperties properties = new IniPGProperties();
        StringBuilder sb = new StringBuilder();
        sb.append(" --host ");
        sb.append(properties.get("database.host", String.class).orElse("localhost"));

        sb.append(" --port ");
        sb.append(properties.get("database.port", String.class).orElse("5432"));

        sb.append(" --schema ");
        sb.append(properties.get("database.schema", String.class).orElse("public"));

        sb.append(" --database ");
        sb.append(properties.get("database.database", String.class).orElse("database"));

        sb.append(" --user ");
        sb.append(properties.get("database.user", String.class).orElse("postgres"));

        sb.append(" --password ");
        sb.append(properties.get("database.password", String.class).orElse("postgres"));

        return commandSpec.replace("pg ", "pg " + sb.toString().trim() + " ");
    }

    @When("^I run the command \"([^\"]*)\" on the PostGIS database$")
    public void I_run_the_command_on_the_PostGIS_database(String commandSpec) throws Throwable {
        commandSpec = getPGDatabaseParameters(commandSpec);
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

    @When("^I run the command \"([^\"]*)\" on the SpatiaLite database$")
    public void I_run_the_command_on_the_SpatiaLite_database(String commandSpec) throws Throwable {
        commandSpec += " --database ";
        commandSpec += getClass().getResource("testdb.sqlite").getPath();
        String[] args = commandSpec.split(" ");
        localRepo.runCommand(args);
    }

    @Given("^I have several feature types in a path$")
    public void I_have_several_feature_types_in_a_path() throws Throwable {
        localRepo.insertAndAdd(points2);
        localRepo.runCommand(true, "commit -m Commit1");
        localRepo.insertAndAdd(points1_FTmodified);
        localRepo.runCommand(true, "commit -m Commit2");
        localRepo.insertAndAdd(points3);
        localRepo.insertAndAdd(lines1);
        localRepo.runCommand(true, "commit -m Commit3");
        localRepo.insertAndAdd(lines2, lines3);
        localRepo.runCommand(true, "commit -m Commit4");
    }

}
