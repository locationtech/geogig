/* Copyright (c) 2018-present Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Strings;

/**
 */
public class BranchConfigOp extends AbstractGeoGigOp<BranchConfig> {

    private String branchName;

    private @Nullable String description;

    private @Nullable String remoteName, remoteBranch;

    private Boolean delete;

    public BranchConfig delete() {
        this.delete = true;
        return call();
    }

    public List<BranchConfig> getAll() {
        List<BranchConfig> configs = new ArrayList<>();

        final Map<String, String> all = configDatabase().getAll();
        for (Ref branch : command(BranchListOp.class).call()) {
            final String section = String.format("branches.%s", branch.localName());
            final String remoteKey = String.format("%s.%s", section, "remote");
            final String remoteBranchKey = String.format("%s.%s", section, "merge");
            final String descriptionKey = String.format("%s.%s", section, "description");
            String remoteName = all.get(remoteKey);
            String remoteBranch = all.get(remoteBranchKey);
            String description = all.get(descriptionKey);

            BranchConfig config = BranchConfig.builder()//
                    .branch(branch)//
                    .remoteName(Optional.ofNullable(remoteName))//
                    .remoteBranch(Optional.ofNullable(remoteBranch))//
                    .description(Optional.ofNullable(description))//
                    .build();
            configs.add(config);

        }
        return configs;
    }

    public BranchConfig get() {
        return call();
    }

    public BranchConfig set() {
        return call();
    }

    /**
     * @param branchName the name of the branch to create, must not already exist
     */
    public BranchConfigOp setName(final String branchName) {
        this.branchName = branchName;
        return this;
    }

    public BranchConfigOp setRemoteName(String remote) {
        this.remoteName = remote;
        return this;
    }

    public BranchConfigOp setRemoteBranch(String branch) {
        this.remoteBranch = branch;
        return this;
    }

    public BranchConfigOp setDescription(String description) {
        this.description = description;
        return this;
    }

    protected BranchConfig _call() {
        checkState(branchName != null, "branch name was not provided");

        if (!Strings.isNullOrEmpty(remoteBranch)) {
            checkState(!Strings.isNullOrEmpty(remoteName),
                    "If remote branch is provided, remote name is mandatory");
            command(CheckRefFormat.class).setAllowOneLevel(true).setThrowsException(true)
                    .setRef(remoteBranch).call();
        }

        final Ref branch = command(BranchResolveOp.class).setName(branchName).call()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Branch %s not found", branchName)));

        String remoteName = this.remoteName;
        String remoteBranch = this.remoteBranch;
        String description = this.description;

        final String section = String.format("branches.%s", branch.localName());
        final String remoteKey = String.format("%s.%s", section, "remote");
        final String remoteBranchKey = String.format("%s.%s", section, "merge");
        final String descriptionKey = String.format("%s.%s", section, "description");

        final boolean delete = this.delete != null && this.delete.booleanValue();
        final boolean get = !delete && remoteName == null && remoteBranch == null
                && description == null;

        ConfigDatabase db = configDatabase();
        if (get) {
            Map<String, String> all = db.getAllSection(section);
            remoteName = all.get("remote");
            remoteBranch = all.get("merge");
            description = all.get("description");
        } else if (delete) {
            remoteName = db.get(remoteKey).orNull();
            remoteBranch = db.get(remoteBranchKey).orNull();
            description = db.get(descriptionKey).orNull();
            db.remove(remoteKey);
            db.remove(remoteBranchKey);
            db.remove(descriptionKey);
        } else {
            if (!Strings.isNullOrEmpty(remoteName)) {
                setConfig(remoteKey, remoteName);
                if (Strings.isNullOrEmpty(remoteBranch)) {
                    remoteBranch = branch.localName();
                }
                setConfig(remoteBranchKey, remoteBranch);
            }
            if (!Strings.isNullOrEmpty(description)) {
                setConfig(descriptionKey, description);
            }
        }
        BranchConfig config = BranchConfig.builder()//
                .branch(branch)//
                .remoteName(Optional.ofNullable(remoteName))//
                .remoteBranch(Optional.ofNullable(remoteBranch))//
                .description(Optional.ofNullable(description))//
                .build();
        return config;
    }

    private void setConfig(final String sectionAndKey, String value) {
        command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.LOCAL)
                .setName(sectionAndKey).setValue(value).call();
    }
}
