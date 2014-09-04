/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.test.sqlserver.functional;

import static org.locationtech.geogig.cli.test.functional.general.GlobalState.runCommand;
import cucumber.annotation.en.When;
import cucumber.runtime.java.StepDefAnnotation;

@StepDefAnnotation
public class SQLServerInitSteps {

    protected String getSQLServerDatabaseParameters() throws Exception {
        IniSQLServerProperties properties = new IniSQLServerProperties();
        StringBuilder sb = new StringBuilder();
        sb.append(" --host ");
        sb.append(properties.get("database.host", String.class).or("localhost"));

        sb.append(" --port ");
        sb.append(properties.get("database.port", String.class).or("1433"));

        sb.append(" --schema ");
        sb.append(properties.get("database.schema", String.class).or("dbo"));

        sb.append(" --database ");
        sb.append(properties.get("database.database", String.class).or("test"));

        sb.append(" --user ");
        sb.append(properties.get("database.user", String.class).or("sa"));

        sb.append(" --password ");
        sb.append(properties.get("database.password", String.class).or("sa"));

        return sb.toString();
    }

    @When("^I run the command \"([^\"]*)\" on the SQL Server database$")
    public void I_run_the_command_X_on_the_sqlserver_database(String commandSpec) throws Throwable {
        commandSpec += getSQLServerDatabaseParameters();
        String[] args = commandSpec.split(" ");
        runCommand(args);
    }

}
