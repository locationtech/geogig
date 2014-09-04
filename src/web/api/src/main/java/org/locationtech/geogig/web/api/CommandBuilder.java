/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.Arrays;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.web.api.commands.AddWebOp;
import org.locationtech.geogig.web.api.commands.BeginTransaction;
import org.locationtech.geogig.web.api.commands.BlameWebOp;
import org.locationtech.geogig.web.api.commands.BranchWebOp;
import org.locationtech.geogig.web.api.commands.CatWebOp;
import org.locationtech.geogig.web.api.commands.CheckoutWebOp;
import org.locationtech.geogig.web.api.commands.Commit;
import org.locationtech.geogig.web.api.commands.Diff;
import org.locationtech.geogig.web.api.commands.EndTransaction;
import org.locationtech.geogig.web.api.commands.FeatureDiffWeb;
import org.locationtech.geogig.web.api.commands.FetchWebOp;
import org.locationtech.geogig.web.api.commands.GetCommitGraph;
import org.locationtech.geogig.web.api.commands.Log;
import org.locationtech.geogig.web.api.commands.LsTree;
import org.locationtech.geogig.web.api.commands.MergeWebOp;
import org.locationtech.geogig.web.api.commands.PullWebOp;
import org.locationtech.geogig.web.api.commands.PushWebOp;
import org.locationtech.geogig.web.api.commands.RebuildGraphWebOp;
import org.locationtech.geogig.web.api.commands.RefParseWeb;
import org.locationtech.geogig.web.api.commands.RemoteWebOp;
import org.locationtech.geogig.web.api.commands.RemoveWebOp;
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

    /**
     * Builds the {@link WebAPICommand}.
     * 
     * @param commandName the name of the command
     * @param options the parameter set
     * @return the command that was built
     * @throws CommandSpecException
     */
    public static WebAPICommand build(String commandName, ParameterSet options)
            throws CommandSpecException {
        AbstractWebAPICommand command = null;
        if ("status".equalsIgnoreCase(commandName)) {
            command = buildStatus(options);
        } else if ("log".equalsIgnoreCase(commandName)) {
            command = buildLog(options);
        } else if ("commit".equalsIgnoreCase(commandName)) {
            command = buildCommit(options);
        } else if ("ls-tree".equalsIgnoreCase(commandName)) {
            command = buildLsTree(options);
        } else if ("updateref".equalsIgnoreCase(commandName)) {
            command = buildUpdateRef(options);
        } else if ("diff".equalsIgnoreCase(commandName)) {
            command = buildDiff(options);
        } else if ("refparse".equalsIgnoreCase(commandName)) {
            command = buildRefParse(options);
        } else if ("branch".equalsIgnoreCase(commandName)) {
            command = buildBranch(options);
        } else if ("remote".equalsIgnoreCase(commandName)) {
            command = buildRemote(options);
        } else if ("push".equalsIgnoreCase(commandName)) {
            command = buildPush(options);
        } else if ("pull".equalsIgnoreCase(commandName)) {
            command = buildPull(options);
        } else if ("fetch".equalsIgnoreCase(commandName)) {
            command = buildFetch(options);
        } else if ("tag".equalsIgnoreCase(commandName)) {
            command = buildTag(options);
        } else if ("featurediff".equalsIgnoreCase(commandName)) {
            command = buildFeatureDiff(options);
        } else if ("getCommitGraph".equalsIgnoreCase(commandName)) {
            command = buildGetCommitGraph(options);
        } else if ("merge".equalsIgnoreCase(commandName)) {
            command = buildMerge(options);
        } else if ("checkout".equalsIgnoreCase(commandName)) {
            command = buildCheckout(options);
        } else if ("beginTransaction".equalsIgnoreCase(commandName)) {
            command = buildBeginTransaction(options);
        } else if ("endTransaction".equalsIgnoreCase(commandName)) {
            command = buildEndTransaction(options);
        } else if ("add".equalsIgnoreCase(commandName)) {
            command = buildAdd(options);
        } else if ("remove".equalsIgnoreCase(commandName)) {
            command = buildRemove(options);
        } else if ("resolveconflict".equalsIgnoreCase(commandName)) {
            command = buildResolveConflict(options);
        } else if ("revertfeature".equalsIgnoreCase(commandName)) {
            command = buildRevertFeature(options);
        } else if ("rebuildgraph".equalsIgnoreCase(commandName)) {
            command = buildRebuildGraph(options);
        } else if ("blame".equalsIgnoreCase(commandName)) {
            command = buildBlame(options);
        } else if ("version".equalsIgnoreCase(commandName)) {
            command = buildVersion(options);
        } else if ("cat".equalsIgnoreCase(commandName)) {
            command = buildCat(options);
        } else if ("statistics".equalsIgnoreCase(commandName)) {
            command = buildStatistics(options);
        } else {
            throw new CommandSpecException("'" + commandName + "' is not a geogig command");
        }

        command.setTransactionId(options.getFirstValue("transactionId", null));

        return command;
    }

    /**
     * Parses a string to an Integer, using a default value if the was not found in the parameter
     * set.
     * 
     * @param form the parameter set
     * @param key the attribute key
     * @param defaultValue the default value
     * @return the parsed integer
     */
    static Integer parseInt(ParameterSet form, String key, Integer defaultValue) {
        String val = form.getFirstValue(key);
        Integer retval = defaultValue;
        if (val != null) {
            try {
                retval = new Integer(val);
            } catch (NumberFormatException nfe) {
                throw new CommandSpecException("Invalid value '" + val + "' specified for option: "
                        + key);
            }
        }
        return retval;
    }

    /**
     * Builds the {@link Status} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static Status buildStatus(ParameterSet options) {
        Status command = new Status();
        command.setLimit(parseInt(options, "limit", 50));
        command.setOffset(parseInt(options, "offset", 0));
        return command;
    }

    /**
     * Builds the {@link Log} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static Log buildLog(ParameterSet options) {
        Log command = new Log();
        command.setLimit(parseInt(options, "limit", null));
        command.setOffset(parseInt(options, "offset", null));
        command.setPaths(Arrays.asList(options.getValuesArray("path")));
        command.setSince(options.getFirstValue("since"));
        command.setUntil(options.getFirstValue("until"));
        command.setSinceTime(options.getFirstValue("sinceTime"));
        command.setUntilTime(options.getFirstValue("untilTime"));
        command.setPage(parseInt(options, "page", 0));
        command.setElementsPerPage(parseInt(options, "show", 30));
        command.setFirstParentOnly(Boolean.valueOf(options
                .getFirstValue("firstParentOnly", "false")));
        command.setCountChanges(Boolean.valueOf(options.getFirstValue("countChanges", "false")));
        command.setReturnRange(Boolean.valueOf(options.getFirstValue("returnRange", "false")));
        command.setSummary(Boolean.valueOf(options.getFirstValue("summary", "false")));
        return command;
    }

    /**
     * Builds the {@link Commit} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static Commit buildCommit(ParameterSet options) {
        Commit commit = new Commit();
        commit.setAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        commit.setMessage(options.getFirstValue("message", null));
        commit.setAuthorName(options.getFirstValue("authorName", null));
        commit.setAuthorEmail(options.getFirstValue("authorEmail", null));
        return commit;
    }

    /**
     * Builds the {@link LsTree} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static LsTree buildLsTree(ParameterSet options) {
        LsTree lsTree = new LsTree();
        lsTree.setIncludeTrees(Boolean.valueOf(options.getFirstValue("showTree", "false")));
        lsTree.setOnlyTrees(Boolean.valueOf(options.getFirstValue("onlyTree", "false")));
        lsTree.setRecursive(Boolean.valueOf(options.getFirstValue("recursive", "false")));
        lsTree.setVerbose(Boolean.valueOf(options.getFirstValue("verbose", "false")));
        lsTree.setRefList(Arrays.asList(options.getValuesArray("path")));
        return lsTree;
    }

    /**
     * Builds the {@link UpdateRefWeb} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static UpdateRefWeb buildUpdateRef(ParameterSet options) {
        UpdateRefWeb command = new UpdateRefWeb();
        command.setName(options.getFirstValue("name", null));
        command.setDelete(Boolean.valueOf(options.getFirstValue("delete", "false")));
        command.setNewValue(options.getFirstValue("newValue", ObjectId.NULL.toString()));
        return command;
    }

    /**
     * Builds the {@link Diff} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static Diff buildDiff(ParameterSet options) {
        Diff command = new Diff();
        command.setOldRefSpec(options.getFirstValue("oldRefSpec", null));
        command.setNewRefSpec(options.getFirstValue("newRefSpec", null));
        command.setPathFilter(options.getFirstValue("pathFilter", null));
        command.setShowGeometryChanges(Boolean.parseBoolean(options.getFirstValue(
                "showGeometryChanges", "false")));
        command.setPage(parseInt(options, "page", 0));
        command.setElementsPerPage(parseInt(options, "show", 30));
        return command;
    }

    /**
     * Builds the {@link RefParseWeb} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static RefParseWeb buildRefParse(ParameterSet options) {
        RefParseWeb command = new RefParseWeb();
        command.setName(options.getFirstValue("name", null));
        return command;
    }

    /**
     * Builds the {@link BranchWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static BranchWebOp buildBranch(ParameterSet options) {
        BranchWebOp command = new BranchWebOp();
        command.setList(Boolean.valueOf(options.getFirstValue("list", "false")));
        command.setRemotes(Boolean.valueOf(options.getFirstValue("remotes", "false")));
        return command;
    }

    /**
     * Builds the {@link RemoteWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static RemoteWebOp buildRemote(ParameterSet options) {
        RemoteWebOp command = new RemoteWebOp();
        command.setList(Boolean.valueOf(options.getFirstValue("list", "false")));
        command.setRemove(Boolean.valueOf(options.getFirstValue("remove", "false")));
        command.setPing(Boolean.valueOf(options.getFirstValue("ping", "false")));
        command.setUpdate(Boolean.valueOf(options.getFirstValue("update", "false")));
        command.setVerbose(Boolean.valueOf(options.getFirstValue("verbose", "false")));
        command.setRemoteName(options.getFirstValue("remoteName", null));
        command.setNewName(options.getFirstValue("newName", null));
        command.setRemoteURL(options.getFirstValue("remoteURL", null));
        command.setUserName(options.getFirstValue("username", null));
        command.setPassword(options.getFirstValue("password", null));
        return command;
    }

    /**
     * Builds the {@link PushWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static PushWebOp buildPush(ParameterSet options) {
        PushWebOp command = new PushWebOp();
        command.setPushAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        command.setRefSpec(options.getFirstValue("ref", null));
        command.setRemoteName(options.getFirstValue("remoteName", null));
        return command;
    }

    /**
     * Builds the {@link PullWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static PullWebOp buildPull(ParameterSet options) {
        PullWebOp command = new PullWebOp();
        command.setFetchAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        command.setRefSpec(options.getFirstValue("ref", null));
        command.setRemoteName(options.getFirstValue("remoteName", null));
        command.setAuthorName(options.getFirstValue("authorName", null));
        command.setAuthorEmail(options.getFirstValue("authorEmail", null));
        return command;
    }

    /**
     * Builds the {@link FetchWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static FetchWebOp buildFetch(ParameterSet options) {
        FetchWebOp command = new FetchWebOp();
        command.setFetchAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        command.setPrune(Boolean.valueOf(options.getFirstValue("prune", "false")));
        command.setRemote(options.getFirstValue("remote"));
        return command;
    }

    /**
     * Builds the {@link TagWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static TagWebOp buildTag(ParameterSet options) {
        TagWebOp command = new TagWebOp();
        command.setList(Boolean.valueOf(options.getFirstValue("list", "false")));
        return command;
    }

    /**
     * Builds the {@link FeatureDiffWeb} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static FeatureDiffWeb buildFeatureDiff(ParameterSet options) {
        FeatureDiffWeb command = new FeatureDiffWeb();
        command.setPath(options.getFirstValue("path", null));
        command.setOldTreeish(options.getFirstValue("oldTreeish", ObjectId.NULL.toString()));
        command.setNewTreeish(options.getFirstValue("newTreeish", ObjectId.NULL.toString()));
        command.setAll(Boolean.valueOf(options.getFirstValue("all", "false")));
        return command;
    }

    /**
     * Builds the {@link GetCommitGraph} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static GetCommitGraph buildGetCommitGraph(ParameterSet options) {
        GetCommitGraph command = new GetCommitGraph();
        command.setDepth(parseInt(options, "depth", 0));
        command.setCommitId(options.getFirstValue("commitId", ObjectId.NULL.toString()));
        command.setPage(parseInt(options, "page", 0));
        command.setElementsPerPage(parseInt(options, "show", 30));
        return command;
    }

    /**
     * Builds the {@link BeginTransaction} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static BeginTransaction buildBeginTransaction(ParameterSet options) {
        BeginTransaction command = new BeginTransaction();
        return command;
    }

    /**
     * Builds the {@link EndTransaction} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static EndTransaction buildEndTransaction(ParameterSet options) {
        EndTransaction command = new EndTransaction();
        command.setCancel(Boolean.valueOf(options.getFirstValue("cancel", "false")));
        return command;
    }

    /**
     * Builds the {@link MergeWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static MergeWebOp buildMerge(ParameterSet options) {
        MergeWebOp command = new MergeWebOp();
        command.setNoCommit(Boolean.valueOf(options.getFirstValue("noCommit", "false")));
        command.setCommit(options.getFirstValue("commit", null));
        command.setAuthorName(options.getFirstValue("authorName", null));
        command.setAuthorEmail(options.getFirstValue("authorEmail", null));
        return command;
    }

    /**
     * Builds the {@link CheckoutWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static CheckoutWebOp buildCheckout(ParameterSet options) {
        CheckoutWebOp command = new CheckoutWebOp();
        command.setName(options.getFirstValue("branch", null));
        command.setOurs(Boolean.valueOf(options.getFirstValue("ours", "false")));
        command.setTheirs(Boolean.valueOf(options.getFirstValue("theirs", "false")));
        command.setPath(options.getFirstValue("path", null));
        return command;
    }

    /**
     * Builds the {@link AddWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static AddWebOp buildAdd(ParameterSet options) {
        AddWebOp command = new AddWebOp();
        command.setPath(options.getFirstValue("path", null));
        return command;
    }

    /**
     * Builds the {@link VersionWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static VersionWebOp buildVersion(ParameterSet options) {
        VersionWebOp command = new VersionWebOp();
        return command;
    }

    /**
     * Builds the {@link RemoveWebOp} command.
     * 
     * @param options the parameter set
     * @return the built command
     */
    static RemoveWebOp buildRemove(ParameterSet options) {
        RemoveWebOp command = new RemoveWebOp();
        command.setPath(options.getFirstValue("path", null));
        command.setRecursive(Boolean.valueOf(options.getFirstValue("recursive", "false")));
        return command;
    }

    static ResolveConflict buildResolveConflict(ParameterSet options) {
        ResolveConflict command = new ResolveConflict();
        command.setPath(options.getFirstValue("path", null));
        command.setFeatureObjectId(options.getFirstValue("objectid", null));
        return command;
    }

    static RebuildGraphWebOp buildRebuildGraph(ParameterSet options) {
        RebuildGraphWebOp command = new RebuildGraphWebOp();
        command.setQuiet(Boolean.valueOf(options.getFirstValue("quiet", "false")));
        return command;
    }

    static RevertFeatureWebOp buildRevertFeature(ParameterSet options) {
        RevertFeatureWebOp command = new RevertFeatureWebOp();
        command.setAuthorName(options.getFirstValue("authorName", null));
        command.setAuthorEmail(options.getFirstValue("authorEmail", null));
        command.setCommitMessage(options.getFirstValue("commitMessage", null));
        command.setMergeMessage(options.getFirstValue("mergeMessage", null));
        command.setNewCommitId(options.getFirstValue("newCommitId", null));
        command.setOldCommitId(options.getFirstValue("oldCommitId", null));
        command.setPath(options.getFirstValue("path", null));
        return command;
    }

    static BlameWebOp buildBlame(ParameterSet options) {
        BlameWebOp command = new BlameWebOp();
        command.setCommit(options.getFirstValue("commit", null));
        command.setPath(options.getFirstValue("path", null));
        return command;
    }

    static CatWebOp buildCat(ParameterSet options) {
        CatWebOp command = new CatWebOp();
        String objectId = options.getFirstValue("objectid", null);
        if (objectId != null) {
            command.setObjectId(ObjectId.valueOf(objectId));
        }
        return command;
    }

    static StatisticsWebOp buildStatistics(ParameterSet options) {
        StatisticsWebOp command = new StatisticsWebOp();
        command.setPath(options.getFirstValue("path", null));
        command.setSince(options.getFirstValue("since", null));
        command.setUntil(options.getFirstValue("branch", null));
        return command;
    }
}
