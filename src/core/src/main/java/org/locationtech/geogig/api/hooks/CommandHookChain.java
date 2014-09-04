/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.hooks;

import static com.google.common.base.Preconditions.checkState;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class CommandHookChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHookChain.class);

    private AbstractGeoGigOp<?> target;

    private List<CommandHook> hooks;

    private CommandHookChain(final AbstractGeoGigOp<?> target) {
        this.target = target;
        this.hooks = new LinkedList<CommandHook>();
    }

    public boolean isEmpty() {
        return hooks == null || hooks.isEmpty();
    }

    void setNext(CommandHook next) {
        this.hooks.add(next);
    }

    public void runPreHooks() throws CannotRunGeogigOperationException {
        AbstractGeoGigOp<?> command = target;
        // run pre-hooks
        for (CommandHook hook : Lists.reverse(hooks)) {
            try {
                LOGGER.debug("Running pre command hook {}", hook);
                command = hook.pre(command);
            } catch (CannotRunGeogigOperationException e) {
                throw e;
            }
        }
    }

    public Object runPostHooks(Object retVal, boolean success) {
        AbstractGeoGigOp<?> command = target;

        for (CommandHook hook : hooks) {
            try {
                retVal = hook.post(command, retVal, success);
            } catch (Exception e) {
                // this exception should not be thrown in a post-execution hook, but just in case,
                // we swallow it and ignore it
                LOGGER.warn(
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

        /**
         * Comparator that determines the priority of two hooks. In least to most important order:
         * {@link ShellScriptHook}, {@link JVMScriptHook}, other {@link CommandHook}s (i.e. pure
         * java ones)
         */
        private static final Comparator<CommandHook> HOOKS_PRIORITY = new Comparator<CommandHook>() {
            @Override
            public int compare(CommandHook o1, CommandHook o2) {
                int p1 = o1 instanceof ShellScriptHook ? 1 : (o1 instanceof JVMScriptHook ? 0 : -1);
                int p2 = o2 instanceof ShellScriptHook ? 1 : (o2 instanceof JVMScriptHook ? 0 : -1);
                return p1 - p2;
            }
        };

        private AbstractGeoGigOp<?> command;

        public Builder command(AbstractGeoGigOp<?> command) {
            this.command = command;
            return this;
        }

        public CommandHookChain build() {
            checkState(command != null, "command is null");

            CommandHookChain chain = new CommandHookChain(command);

            List<CommandHook> commandHooks = Hookables.findHooksFor(command);
            if (!commandHooks.isEmpty()) {
                PriorityQueue<CommandHook> queue = new PriorityQueue<CommandHook>(
                        commandHooks.size(), HOOKS_PRIORITY);
                queue.addAll(commandHooks);
                for (CommandHook hook : queue) {
                    chain.setNext(hook);
                }
            }
            return chain;
        }

    }
}
