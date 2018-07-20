/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.beust.jcommander.Parameter;
import com.google.common.collect.Lists;

/**
 * Encapsulates and describes the arguments for the {@link Log} command
 */
public class LogArgs {

    @Parameter(names = { "--max-count", "-n" }, description = "Maximum number of commits to log.")
    @Nullable
    public Integer limit;

    @Parameter(names = "--skip", description = "Skip number commits before starting to show the commit output.")
    @Nullable
    public Integer skip;

    @Parameter(names = "--since", description = "Show only commits since the specified 'since' date")
    @Nullable
    public String since;

    @Parameter(names = "--until", description = "Show only commits until the specified 'until' date")
    @Nullable
    public String until;

    @Parameter(names = "--author", description = "Show only commits by authors with names maching the passed regular expression")
    @Nullable
    public String author;

    @Parameter(names = "--committer", description = "Show only commits by committer with names maching the passed regular expression")
    @Nullable
    public String committer;

    @Parameter(names = "--oneline", description = "Print only commit id and message on a single line per commit")
    public boolean oneline;

    @Parameter(description = "[[<until>]|[<since>..<until>]]")
    public List<String> sinceUntilPaths = Lists.newArrayList();

    @Parameter(names = { "--path", "-p" }, description = "Print only commits that have modified the given path(s)", variableArity = true)
    public List<String> pathNames = Lists.newArrayList();

    @Parameter(names = "--summary", description = "Show summary of changes for each commit")
    @Nullable
    public boolean summary;

    @Parameter(names = "--stats", description = "Show stats of changes for each commit")
    @Nullable
    public boolean stats;

    @Parameter(names = "--names-only", description = "Show names of changed elements")
    @Nullable
    public boolean names;

    @Parameter(names = "--topo-order", description = "Avoid showing commits on multiple lines of history intermixed")
    @Nullable
    public boolean topo;

    @Parameter(names = "--first-parent", description = "Use only the first parent of each commit, showing a linear history")
    @Nullable
    public boolean firstParent;

    @Parameter(names = "--all", description = "Show history of all branches")
    @Nullable
    public boolean all;

    @Parameter(names = "--branch", description = "Show history of selected branch")
    @Nullable
    public String branch;

    @Parameter(names = "--abbrev-commit", description = "Show abbreviate commit IDs")
    @Nullable
    public boolean abbrev;

    @Parameter(names = "--decoration", description = "Show reference names")
    @Nullable
    public boolean decoration;

    @Parameter(names = "--utc", description = "Show date/time in UTC")
    @Nullable
    public boolean utcDateFormat;

}
