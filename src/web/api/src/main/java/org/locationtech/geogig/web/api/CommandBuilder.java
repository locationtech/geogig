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
import java.util.function.Function;

import org.locationtech.geogig.rest.geotools.ExportWebOp;
import org.locationtech.geogig.web.api.commands.AddWebOp;
import org.locationtech.geogig.web.api.commands.BeginTransaction;
import org.locationtech.geogig.web.api.commands.BlameWebOp;
import org.locationtech.geogig.web.api.commands.BranchWebOp;
import org.locationtech.geogig.web.api.commands.CatWebOp;
import org.locationtech.geogig.web.api.commands.CheckoutWebOp;
import org.locationtech.geogig.web.api.commands.Commit;
import org.locationtech.geogig.web.api.commands.ConfigWebOp;
import org.locationtech.geogig.web.api.commands.Diff;
import org.locationtech.geogig.web.api.commands.EndTransaction;
import org.locationtech.geogig.web.api.commands.FeatureDiffWeb;
import org.locationtech.geogig.web.api.commands.FetchWebOp;
import org.locationtech.geogig.web.api.commands.GetCommitGraph;
import org.locationtech.geogig.web.api.commands.InitWebOp;
import org.locationtech.geogig.web.api.commands.Log;
import org.locationtech.geogig.web.api.commands.LsTree;
import org.locationtech.geogig.web.api.commands.MergeWebOp;
import org.locationtech.geogig.web.api.commands.PullWebOp;
import org.locationtech.geogig.web.api.commands.PushWebOp;
import org.locationtech.geogig.web.api.commands.RebuildGraphWebOp;
import org.locationtech.geogig.web.api.commands.RefParseWeb;
import org.locationtech.geogig.web.api.commands.RemoteWebOp;
import org.locationtech.geogig.web.api.commands.RemoveWebOp;
import org.locationtech.geogig.web.api.commands.RenameRepository;
import org.locationtech.geogig.web.api.commands.RequestDeleteRepositoryToken;
import org.locationtech.geogig.web.api.commands.ResolveConflict;
import org.locationtech.geogig.web.api.commands.RevertFeatureWebOp;
import org.locationtech.geogig.web.api.commands.StatisticsWebOp;
import org.locationtech.geogig.web.api.commands.Status;
import org.locationtech.geogig.web.api.commands.TagWebOp;
import org.locationtech.geogig.web.api.commands.UpdateRefWeb;
import org.locationtech.geogig.web.api.commands.VersionWebOp;

/**
 * Builds {@link WebAPICommand}s by parsing a given command name and uses a given parameter set to
 * fill out their variables.
 */
public class CommandBuilder {

    private static Map<String, Function<ParameterSet, WebAPICommand>> MAPPINGS = new HashMap<>();
    static {
        MAPPINGS.put("init", InitWebOp::new);
        MAPPINGS.put("delete", RequestDeleteRepositoryToken::new);
        MAPPINGS.put("rename", RenameRepository::new);
        MAPPINGS.put("status", Status::new);
        MAPPINGS.put("log", Log::new);
        MAPPINGS.put("commit", Commit::new);
        MAPPINGS.put("config", ConfigWebOp::new);
        MAPPINGS.put("ls-tree", LsTree::new);
        MAPPINGS.put("updateref", UpdateRefWeb::new);
        MAPPINGS.put("diff", Diff::new);
        MAPPINGS.put("refparse", RefParseWeb::new);
        MAPPINGS.put("branch", BranchWebOp::new);
        MAPPINGS.put("remote", RemoteWebOp::new);
        MAPPINGS.put("push", PushWebOp::new);
        MAPPINGS.put("pull", PullWebOp::new);
        MAPPINGS.put("fetch", FetchWebOp::new);
        MAPPINGS.put("tag", TagWebOp::new);
        MAPPINGS.put("featurediff", FeatureDiffWeb::new);
        MAPPINGS.put("getCommitGraph", GetCommitGraph::new);
        MAPPINGS.put("merge", MergeWebOp::new);
        MAPPINGS.put("checkout", CheckoutWebOp::new);
        MAPPINGS.put("beginTransaction", BeginTransaction::new);
        MAPPINGS.put("endTransaction", EndTransaction::new);
        MAPPINGS.put("add", AddWebOp::new);
        MAPPINGS.put("remove", RemoveWebOp::new);
        MAPPINGS.put("resolveconflict", ResolveConflict::new);
        MAPPINGS.put("revertfeature", RevertFeatureWebOp::new);
        MAPPINGS.put("rebuildgraph", RebuildGraphWebOp::new);
        MAPPINGS.put("blame", BlameWebOp::new);
        MAPPINGS.put("version", VersionWebOp::new);
        MAPPINGS.put("cat", CatWebOp::new);
        MAPPINGS.put("statistics", StatisticsWebOp::new);
        MAPPINGS.put("export", ExportWebOp::new);
    }

    /**
     * Builds the {@link WebAPICommand}.
     * 
     * @param commandName the name of the command
     * @param options the parameter set
     * @return the command that was built
     * @throws CommandSpecException
     */
    public static WebAPICommand build(final String commandName, final ParameterSet options)
            throws CommandSpecException {

        if (!MAPPINGS.containsKey(commandName)) {
            throw new CommandSpecException("'" + commandName + "' is not a geogig command");
        }

        WebAPICommand command = MAPPINGS.get(commandName).apply(options);

        return command;
    }

}
