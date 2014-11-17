/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.Properties;

/**
 * Stores information for the current build. Built from a set of Properties.
 */
public class VersionInfo {

    private String projectVersion; // =${project.version}

    private String branch; // =${git.branch}

    private String commitId; // =${git.commit.id}

    private String commitIdAbbrev; // =${git.commit.id.abbrev}

    private String buildUserName; // =${git.build.user.name}

    private String buildUserEmail; // =${git.build.user.email}

    private String buildTime; // =${git.build.time}

    private String commitUserName; // =${git.commit.user.name}

    private String commitUserEmail; // =${git.commit.user.email}

    private String commitMessageFull; // =${git.commit.message.full}

    private String commitMessageShort; // =${git.commit.message.short}

    private String commitTime; // =${git.commit.time}

    /**
     * Constructs a new {@code VersionInfo} from the given Properties.
     * 
     * @param properties the properties of the current build
     */
    public VersionInfo(Properties properties) {
        this.projectVersion = getClass().getPackage().getImplementationVersion();
        this.branch = properties.get("git.branch").toString();
        this.commitId = properties.get("git.commit.id").toString();
        this.commitIdAbbrev = properties.get("git.commit.id.abbrev").toString();
        this.buildUserName = properties.get("git.build.user.name").toString();
        this.buildUserEmail = properties.get("git.build.user.email").toString();
        this.buildTime = properties.get("git.build.time").toString();
        this.commitUserName = properties.get("git.commit.user.name").toString();
        this.commitUserEmail = properties.get("git.commit.user.email").toString();
        this.commitMessageShort = properties.get("git.commit.message.short").toString();
        this.commitMessageFull = properties.get("git.commit.message.full").toString();
        this.commitTime = properties.get("git.commit.time").toString();
    }

    /**
     * @return the project version
     */
    public String getProjectVersion() {
        return this.projectVersion;
    }

    /**
     * @return the Git branch that GeoGig was built from
     */
    public String getBranch() {
        return this.branch;
    }

    /**
     * @return the last commit id on the branch that GeoGig was built from
     */
    public String getCommitId() {
        return this.commitId;
    }

    /**
     * @return the last commit id (abbreviated) on the branch that GeoGig was built from
     */
    public String getCommitIdAbbrev() {
        return this.commitIdAbbrev;
    }

    /**
     * @return the committer name of the last commit on the branch that GeoGig was built from
     */
    public String getCommitUserName() {
        return this.commitUserName;
    }

    /**
     * @return the committer email of the last commit on the branch that GeoGig was built from
     */
    public String getCommitUserEmail() {
        return this.commitUserEmail;
    }

    /**
     * @return the full commit message of the last commit on the branch that GeoGig was built from
     */
    public String getCommitMessageFull() {
        return this.commitMessageFull;
    }

    /**
     * @return the shortened commit message of the last commit on the branch that GeoGig was built
     *         from
     */
    public String getCommitMessageShort() {
        return this.commitMessageShort;
    }

    /**
     * @return the commit time of the last commit on the branch that GeoGig was built from
     */
    public String getCommitTime() {
        return this.commitTime;
    }

    /**
     * @return the Git user name of who executed the build
     */
    public String getBuildUserName() {
        return this.buildUserName;
    }

    /**
     * @return the Git user email of who executed the build
     */
    public String getBuildUserEmail() {
        return this.buildUserEmail;
    }

    /**
     * @return the time of the build
     */
    public String getBuildTime() {
        return this.buildTime;
    }
}
