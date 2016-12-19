/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.beust.jcommander.Parameter;
import com.google.common.collect.Lists;

/**
 * Encapsulates and describes the arguments for the {@link RevList} command
 */
public class RevListArgs {

    /**
     * The commits to use for starting the list of output commits
     * 
     */
    @Parameter(description = "< [<commit> ...]|[<since>..<until>]")
    public List<String> commits = new ArrayList<String>();

    @Parameter(names = { "--max-count", "-n" }, description = "Maximum number of commits to log.")
    @Nullable
    public Integer limit;

    @Parameter(names = "--skip", description = "Skip number commits before starting to show the commit output.")
    @Nullable
    public Integer skip;

    @Parameter(names = "--since", description = "Maximum number of commits to log")
    @Nullable
    public String since;

    @Parameter(names = "--until", description = "Maximum number of commits to log")
    @Nullable
    public String until;

    @Parameter(names = "--author", description = "Return only commits by authors with names maching the passed regular expression")
    @Nullable
    public String author;

    @Parameter(names = "--committer", description = "Return only commits by committer with names maching the passed regular expression")
    @Nullable
    public String committer;

    @Parameter(names = { "--path", "-p" }, description = "Print only commits that have modified the given path(s)", variableArity = true)
    public List<String> pathNames = Lists.newArrayList();

    @Parameter(names = "--summary", description = "Show summary of changes for each commit")
    @Nullable
    public boolean summary;

    @Parameter(names = "--topo-order", description = "Avoid showing commits on multiple lines of history intermixed")
    @Nullable
    public boolean topo;

    @Parameter(names = "--first-parent", description = "Use only the first parent of each commit, showing a linear history")
    @Nullable
    public boolean firstParent;

    @Parameter(names = "--changed", description = "Show paths affected by each commit")
    @Nullable
    public boolean changed;

}
