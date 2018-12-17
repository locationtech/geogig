/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.hooks;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

final @Slf4j class CommandHookChain {

    private static final ImmutableList<CommandHook> classPathHooks;
    static {
        classPathHooks = loadClasspathHooks();
    }

    private AbstractGeoGigOp<?> target;

    private List<CommandHook> hooks;

    static ImmutableList<CommandHook> loadClasspathHooks() {
        ServiceLoader<CommandHook> loader = ServiceLoader.load(CommandHook.class,
                CommandHook.class.getClassLoader());
        return ImmutableList.copyOf(loader.iterator());
    }

    static boolean hasClasspathHooks(Class<? extends AbstractGeoGigOp<?>> commandClass) {
        for (CommandHook hook : classPathHooks) {
            if (hook.appliesTo(commandClass)) {
                return true;
            }
        }
        return false;
    }

    private CommandHookChain(final AbstractGeoGigOp<?> target) {
        this.target = target;
        this.hooks = new LinkedList<>();
    }

    public boolean isEmpty() {
        return hooks == null || hooks.isEmpty();
    }

    void setNext(CommandHook next) {
        this.hooks.add(next);
    }

    /**
     * @throws CannotRunGeogigOperationException
     */
    final void runPreHooks() {
        AbstractGeoGigOp<?> command = target;
        // run pre-hooks
        for (CommandHook hook : Lists.reverse(hooks)) {
            log.debug("Running pre command hook {}", hook);
            command = hook.pre(command);
        }
    }

    final Object runPostHooks(@Nullable Object retVal, @Nullable RuntimeException exception) {
        AbstractGeoGigOp<?> command = target;

        for (CommandHook hook : hooks) {
            try {
                retVal = hook.post(command, retVal, exception);
            } catch (CannotRunGeogigOperationException rethrow) {
                throw rethrow;
            } catch (Exception e) {
                // this exception should not be thrown in a post-execution hook, but just in case,
                // we swallow it and ignore it
                log.warn(
                        "Post-command hook {} for command {} threw an exception that will not be propagated",
                        hook, command.getClass().getName(), e);
            }
        }

        return retVal;

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AbstractGeoGigOp<?> command;

        public Builder command(AbstractGeoGigOp<?> command) {
            this.command = command;
            return this;
        }

        public CommandHookChain build() {
            checkState(command != null, "command is null");

            CommandHookChain chain = new CommandHookChain(command);

            List<CommandHook> commandHooks = findHooksFor(command);
            if (!commandHooks.isEmpty()) {
                Collections.sort(commandHooks);
                for (CommandHook hook : commandHooks) {
                    chain.setNext(hook);
                }
            }
            return chain;
        }
    }

    static List<CommandHook> findHooksFor(AbstractGeoGigOp<?> command) {

        @SuppressWarnings("unchecked")
        final Class<? extends AbstractGeoGigOp<?>> clazz = (Class<? extends AbstractGeoGigOp<?>>) command
                .getClass();

        List<CommandHook> hooks = Collections.emptyList();

        for (CommandHook hook : classPathHooks) {
            if (hook.appliesTo(clazz)) {
                if (hooks.isEmpty()) {
                    hooks = new LinkedList<>();
                }
                hooks.addAll(hook.unwrap(command));
            }
        }
        return hooks;
    }
}
