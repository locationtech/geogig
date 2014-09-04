/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class ReadMergeCommitMessageOp extends AbstractGeoGigOp<String> {

    @Override
    protected String _call() {
        URL envHome = new ResolveGeogigDir(platform()).call().get();
        try {
            File file = new File(envHome.toURI());
            file = new File(file, "MERGE_MSG");
            if (!file.exists()) {
                return "";
            }
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            return Joiner.on("\n").join(lines);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}