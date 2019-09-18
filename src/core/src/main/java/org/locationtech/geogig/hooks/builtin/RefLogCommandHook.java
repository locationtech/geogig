/* Copyright (c) 2019 Gabriel Roldan and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.hooks.builtin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CannotRunGeogigOperationException;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.transaction.GeogigTransaction;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * Hooks into {@link UpdateRefs, to update the reflog whenever {@code HEAD} changes
 * <p>
 * This is an initial step towards implementing an actual reflog. It currently does nothing else
 * than logging ref changes to an slf4j logger.
 */
@Slf4j(topic = "geogig.reflog")
public class RefLogCommandHook implements CommandHook {

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
        if (exception == null) {
            final Optional<RefChange> head = logUpdatedRefsAndReturnUpdatedHead(command, retVal);
            // head.ifPresent(ref -> {
            // String reason = findReason(command);
            // UUID transactionId = null;
            // if (command.context() instanceof GeogigTransaction) {
            // transactionId = ((GeogigTransaction) command.context()).getTransactionId();
            // }
            // log(ref, reason, transactionId);
            // });
        }
        return (T) retVal;
    }

    private void log(RefChange change, String reason, @Nullable UUID transactionId) {
        // if (head.name().equals(Ref.HEAD))

        Ref head = (change.isDelete() ? change.oldValue() : change.newValue()).orElse(null);
        if (head == null) {
            return;
        }
        log.debug("{}/{} {}{}: {}", //
                RevObjects.toShortString(head.getObjectId()), //
                transactionId, //
                head.getName(), //
                head instanceof SymRef ? " -> " + head.peel().getName() : " ", //
                reason);
    }

    private String findReason(Command<?> command) {
        String reason = ((UpdateRefs) command).getReason().orElse(null);
        return reason == null ? "No reason given" : reason;
    }

    @SuppressWarnings("unchecked")
    private Optional<RefChange> logUpdatedRefsAndReturnUpdatedHead(Command<?> command,
            Object retVal) {
        Preconditions.checkArgument(command instanceof UpdateRefs);
        List<RefChange> refs = (List<RefChange>) retVal;

        Optional<RefChange> head = refs.stream().filter(r -> Ref.HEAD.equals(r.name())).findFirst();
        Context context = command.context();
        UUID tx = context instanceof GeogigTransaction
                ? ((GeogigTransaction) context).getTransactionId()
                : null;
        String reason = findReason(command);
        refs.forEach(r -> log(r, reason, tx));
        return head;
    }
}
