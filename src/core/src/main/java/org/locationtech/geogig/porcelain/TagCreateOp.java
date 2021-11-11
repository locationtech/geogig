/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.locationtech.geogig.base.Preconditions.checkArgument;
import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

/**
 * Creates a new tag
 * 
 */
public class TagCreateOp extends AbstractGeoGigOp<RevTag> {

    private String name;

    private ObjectId commitId;

    private String message;

    /**
     * Executes the tag creation operation.
     * 
     * @return the created tag
     * 
     */
    protected @Override RevTag _call() throws RuntimeException {
        checkState(name != null, "tag name was not provided");
        final String tagRefPath = Ref.TAGS_PREFIX + name;
        checkArgument(!command(RefParse.class).setName(tagRefPath).call().isPresent(),
                "A tag named '" + name + "' already exists.");
        command(CheckRefFormat.class).setThrowsException(true).setRef(tagRefPath).call();

        if (commitId == null) {
            final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
            Preconditions.checkState(currHead.isPresent(),
                    "Repository has no HEAD, can't create tag");
            commitId = currHead.get().getObjectId();
        }
        RevPerson tagger = resolveTagger();
        message = message == null ? "" : message;

        final RevTag tag = RevTag.builder().name(name).commitId(commitId).message(message)
                .tagger(tagger).build();

        objectDatabase().put(tag);
        Optional<Ref> branchRef = command(UpdateRef.class).setName(tagRefPath)
                .setNewValue(tag.getId()).setReason("tag: create").call();
        checkState(branchRef.isPresent());

        return tag;
    }

    public TagCreateOp setMessage(String message) {
        this.message = message;
        return this;
    }

    public TagCreateOp setCommitId(ObjectId commitId) {
        this.commitId = commitId;
        return this;
    }

    public TagCreateOp setName(String name) {
        this.name = name;
        return this;
    }

    private RevPerson resolveTagger() {
        final String nameKey = "user.name";
        final String emailKey = "user.email";
        String taggerName = getClientData(nameKey)
                .orElseGet(() -> command(ConfigGet.class).setName(nameKey).call().orElse(null));
        String taggerEmail = getClientData(emailKey)
                .orElseGet(() -> command(ConfigGet.class).setName(emailKey).call().orElse(null));

        checkState(taggerName != null,
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                nameKey, nameKey);
        checkState(taggerEmail != null,
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                emailKey, emailKey);

        Platform platform = platform();
        long taggerTimeStamp = platform.currentTimeMillis();
        int taggerTimeZoneOffset = platform.timeZoneOffset(taggerTimeStamp);
        return RevPerson.builder().build(taggerName, taggerEmail, taggerTimeStamp,
                taggerTimeZoneOffset);
    }

}
