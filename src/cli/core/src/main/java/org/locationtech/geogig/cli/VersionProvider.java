/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.porcelain.VersionInfo;
import org.locationtech.geogig.porcelain.VersionOp;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

    public @Override String[] getVersion() throws Exception {
        VersionInfo info = VersionOp.get();
        List<String> vlines = new ArrayList<>();
        vlines.add(printVersionProperty("Project Version", info.getProjectVersion()));
        vlines.add(printVersionProperty("Build Time", info.getBuildTime()));
        vlines.add(printVersionProperty("Git Branch", info.getBranch()));
        vlines.add(printVersionProperty("Git Commit ID", info.getCommitId()));
        vlines.add(printVersionProperty("Git Commit Time", info.getCommitTime()));
        vlines.add(printVersionProperty("Git Commit Author Name", info.getCommitUserName()));
        vlines.add(printVersionProperty("Git Commit Author Email", info.getCommitUserEmail()));
        vlines.add(printVersionProperty("Git Commit Message", info.getCommitMessageFull()));

        return vlines.toArray(new String[vlines.size()]);
    }

    private String printVersionProperty(String propertyName, @Nullable String propertyValue) {

        return String.format("%s : %s", propertyName,
                (propertyValue == null ? "Unspecified" : propertyValue));
    }
}
