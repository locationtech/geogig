package org.locationtech.geogig.model;

import javax.annotation.Nullable;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

public @Accessors(fluent = true) class RevTagBuilder {

    private @Setter RevObjectFactory factory;

    private @Setter ObjectId id;

    private @Setter @NonNull String name;

    private @Setter @NonNull ObjectId commitId;

    private @Setter @NonNull String message;

    private @Setter @NonNull RevPerson tagger;

    public RevTag build() {
        return build(id, name, commitId, message, tagger);
    }

    public RevTag build(@Nullable ObjectId id, @NonNull String name, @NonNull ObjectId commitId,
            @NonNull String message, @NonNull RevPerson tagger) {
        if (id == null) {
            id = HashObjectFunnels.hashTag(name, commitId, message, tagger);
        }
        if (factory == null) {
            factory = RevObjectFactory.defaultInstance();
        }
        return factory.createTag(id, name, commitId, message, tagger);
    }
}