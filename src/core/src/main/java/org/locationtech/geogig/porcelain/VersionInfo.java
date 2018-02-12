/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

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
        // try to get the implementation version from this class
        this.projectVersion = VersionInfo.class.getPackage().getImplementationVersion();
        if (this.projectVersion == null) {
            // no Implementation Version available
            // should only occur if running from class files and not a JAR
            this.projectVersion = "UNDETERMINED";
        }
        //@formatter:off
        this.branch = properties.getProperty("git.branch", "<unknown branch>");
        this.commitId = properties.getProperty("git.commit.id", "<unknown commit id>");
        this.commitIdAbbrev = properties.getProperty("git.commit.id.abbrev", "<unknown commit id>");
        this.buildUserName = properties.getProperty("git.build.user.name", "<unknown build user>");
        this.buildUserEmail = properties.getProperty("git.build.user.email", "<unknown build user email>");
        this.buildTime = properties.getProperty("git.build.time", "<unknown build time>");
        this.commitUserName = properties.getProperty("git.commit.user.name", "<unknown committer>");
        this.commitUserEmail = properties.getProperty("git.commit.user.email", "<unknown committer email>");
        this.commitMessageShort = properties.getProperty("git.commit.message.short", "<unknown commit message>");
        this.commitMessageFull = properties.getProperty("git.commit.message.full", "<unknown commit message>");
        this.commitTime = properties.getProperty("git.commit.time", "<unknown commit time>");
        //@formatter:on
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Project Version: ").append(getProjectVersion()).append('\n');
        sb.append("Build Time: ").append(getBuildTime()).append('\n');
        sb.append("Build User Name: ").append(getBuildUserName()).append('\n');
        sb.append("Build User Email: ").append(getBuildUserEmail()).append('\n');
        sb.append("Git Branch: ").append(getBranch()).append('\n');
        sb.append("Git Commit ID: ").append(getCommitId()).append('\n');
        sb.append("Git Commit Time: ").append(getCommitTime()).append('\n');
        sb.append("Git Commit Author Name: ").append(getCommitUserName()).append('\n');
        sb.append("Git Commit Author Email: ").append(getCommitUserEmail()).append('\n');
        sb.append("Git Commit Message: ").append(getCommitMessageFull());
        return sb.toString();
    }
}
