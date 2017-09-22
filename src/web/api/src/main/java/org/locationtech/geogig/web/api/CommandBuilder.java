/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.locationtech.geogig.rest.geotools.Export;
import org.locationtech.geogig.rest.geotools.ExportDiff;
import org.locationtech.geogig.rest.geotools.Import;
import org.locationtech.geogig.web.api.commands.Add;
import org.locationtech.geogig.web.api.commands.BeginTransaction;
import org.locationtech.geogig.web.api.commands.Blame;
import org.locationtech.geogig.web.api.commands.Branch;
import org.locationtech.geogig.web.api.commands.Cat;
import org.locationtech.geogig.web.api.commands.Checkout;
import org.locationtech.geogig.web.api.commands.Commit;
import org.locationtech.geogig.web.api.commands.Config;
import org.locationtech.geogig.web.api.commands.Diff;
import org.locationtech.geogig.web.api.commands.EndTransaction;
import org.locationtech.geogig.web.api.commands.FeatureDiff;
import org.locationtech.geogig.web.api.commands.Fetch;
import org.locationtech.geogig.web.api.commands.GetCommitGraph;
import org.locationtech.geogig.web.api.commands.Log;
import org.locationtech.geogig.web.api.commands.LsTree;
import org.locationtech.geogig.web.api.commands.Merge;
import org.locationtech.geogig.web.api.commands.Pull;
import org.locationtech.geogig.web.api.commands.Push;
import org.locationtech.geogig.web.api.commands.RebuildGraph;
import org.locationtech.geogig.web.api.commands.RefParse;
import org.locationtech.geogig.web.api.commands.RemoteManagement;
import org.locationtech.geogig.web.api.commands.Remove;
import org.locationtech.geogig.web.api.commands.RenameRepository;
import org.locationtech.geogig.web.api.commands.ReportMergeScenario;
import org.locationtech.geogig.web.api.commands.RequestDeleteRepositoryToken;
import org.locationtech.geogig.web.api.commands.ResolveConflict;
import org.locationtech.geogig.web.api.commands.RevertFeature;
import org.locationtech.geogig.web.api.commands.Statistics;
import org.locationtech.geogig.web.api.commands.Status;
import org.locationtech.geogig.web.api.commands.Tag;
import org.locationtech.geogig.web.api.commands.UpdateRef;
import org.locationtech.geogig.web.api.commands.Version;
import org.springframework.http.HttpStatus;

/**
 * Builds {@link WebAPICommand}s by parsing a given command name and uses a given parameter set to
 * fill out their variables.
 */
public class CommandBuilder {

    private final static Map<String, Supplier<AbstractWebAPICommand>> MAPPINGS =
            new HashMap<>(36);
    static {
        MAPPINGS.put("delete", RequestDeleteRepositoryToken::new);
        MAPPINGS.put("rename", RenameRepository::new);
        MAPPINGS.put("status", Status::new);
        MAPPINGS.put("log", Log::new);
        MAPPINGS.put("commit", Commit::new);
        MAPPINGS.put("config", Config::new);
        MAPPINGS.put("ls-tree", LsTree::new);
        MAPPINGS.put("updateref", UpdateRef::new);
        MAPPINGS.put("diff", Diff::new);
        MAPPINGS.put("refparse", RefParse::new);
        MAPPINGS.put("branch", Branch::new);
        MAPPINGS.put("remote", RemoteManagement::new);
        MAPPINGS.put("push", Push::new);
        MAPPINGS.put("pull", Pull::new);
        MAPPINGS.put("fetch", Fetch::new);
        MAPPINGS.put("tag", Tag::new);
        MAPPINGS.put("featurediff", FeatureDiff::new);
        MAPPINGS.put("getCommitGraph", GetCommitGraph::new);
        MAPPINGS.put("merge", Merge::new);
        MAPPINGS.put("reportMergeScenario", ReportMergeScenario::new);
        MAPPINGS.put("checkout", Checkout::new);
        MAPPINGS.put("beginTransaction", BeginTransaction::new);
        MAPPINGS.put("endTransaction", EndTransaction::new);
        MAPPINGS.put("add", Add::new);
        MAPPINGS.put("remove", Remove::new);
        MAPPINGS.put("resolveconflict", ResolveConflict::new);
        MAPPINGS.put("revertfeature", RevertFeature::new);
        MAPPINGS.put("rebuildgraph", RebuildGraph::new);
        MAPPINGS.put("blame", Blame::new);
        MAPPINGS.put("version", Version::new);
        MAPPINGS.put("cat", Cat::new);
        MAPPINGS.put("statistics", Statistics::new);
        MAPPINGS.put("export", Export::new);
        MAPPINGS.put("export-diff", ExportDiff::new);
        MAPPINGS.put("import", Import::new);
    }

    /**
     * Builds the {@link WebAPICommand}.
     * 
     * @param commandName the name of the command
     * @param options the parameter set
     * @return the command that was built
     * @throws CommandSpecException
     */
    public static AbstractWebAPICommand build(final String commandName)
            throws CommandSpecException {

        if (!MAPPINGS.containsKey(commandName)) {
            throw new CommandSpecException("'" + commandName + "' is not a geogig command",
                    HttpStatus.NOT_FOUND);
        }

        AbstractWebAPICommand command = MAPPINGS.get(commandName).get();

        return command;
    }

}
