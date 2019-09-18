/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.hooks.builtin;

import static org.locationtech.geogig.model.Ref.CHERRY_PICK_HEAD;
import static org.locationtech.geogig.model.Ref.HEAD;
import static org.locationtech.geogig.model.Ref.MERGE_HEAD;
import static org.locationtech.geogig.model.Ref.ORIG_HEAD;
import static org.locationtech.geogig.model.Ref.STAGE_HEAD;
import static org.locationtech.geogig.model.Ref.WORK_HEAD;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CannotRunGeogigOperationException;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.UpdateIndexesOp;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefChange;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Hooks into {@link UpdateRefs} to update all the indexes that need updating after a branch is
 * updated.
 *
 */
@Slf4j(topic = "geogig.hooks")
public class UpdateIndexesHook implements CommandHook {

    private static final ImmutableSet<String> WORK_REFS = ImmutableSet.of(HEAD, STAGE_HEAD,
            WORK_HEAD, MERGE_HEAD, CHERRY_PICK_HEAD, ORIG_HEAD);

    public @Override boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return UpdateRefs.class.equals(clazz);
    }

    public @Override <C extends Command<?>> C pre(C command)
            throws CannotRunGeogigOperationException {
        return command;
    }

    @SuppressWarnings("unchecked")
    public @Override <T> T post(Command<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {
        if (exception != null) {
            return (T) retVal;
        }
        final List<Ref> indexableRefs = findIndexableRefs(command, retVal);
        for (Ref ref : indexableRefs) {
            final Context context = command.context();
            try {
                ProgressListener listener = command.getProgressListener();
                listener.started();
                log.debug("Calling UpdateIndexesOp for {}", ref);
                List<Index> updates = context.command(UpdateIndexesOp.class)//
                        .setRef(ref)//
                        .setProgressListener(listener)//
                        .call();
                if (!listener.isCanceled() && !updates.isEmpty()) {
                    String desc = String.format("updated indexes: %s", updates.stream()
                            .map(i -> i.info().getTreeName()).collect(Collectors.joining(", ")));
                    listener.setDescription(desc);
                }
            } catch (ConflictsException conflictsEx) {
                // expected, we don't update indexes if there are merge conflicts
                log.debug("Not upadting indexes, there are merge conflicts at {}", ref);
            } catch (Exception e) {
                log.error("Error updaing indexes at {}", ref, e);
            }
        }
        return (T) retVal;
    }

    @SuppressWarnings("unchecked")
    private List<Ref> findIndexableRefs(Command<?> command, Object retVal) {
        Preconditions.checkState(command instanceof UpdateRefs);
        List<RefChange> updated = (List<RefChange>) retVal;

        return updated.stream().map(RefChange::newValue).filter(Optional::isPresent)
                .map(Optional::get).filter(this::isIndexable).collect(Collectors.toList());
    }

    private boolean isIndexable(@NonNull Ref ref) {
        ObjectId objectId = ref.getObjectId();
        if (objectId.isNull() || RevTree.EMPTY_TREE_ID.equals(objectId)) {
            return false;
        }
        if (ref instanceof SymRef) {
            return false;
        }
        if (WORK_REFS.contains(Ref.simpleName(ref.getName()))) {
            return false;
        }
        if (!Ref.isChild(Ref.REFS_PREFIX, ref.getName())) {
            return false;
        }
        return true;
    }
}
