/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.hooks;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.AbstractGeoGigOp.CommandListener;
import org.locationtech.geogig.di.Decorator;

/**
 * An interceptor for the call() method in GeoGig operations that allow hooks
 * 
 */
public class CommandHooksDecorator implements Decorator {

    @SuppressWarnings("unchecked")
    @Override
    public boolean canDecorate(Object instance) {
        boolean canDecorate = instance instanceof AbstractGeoGigOp;
        if (canDecorate) {
            canDecorate &= instance.getClass().isAnnotationPresent(Hookable.class);
            canDecorate |= Hookables
                    .hasClasspathHooks((Class<? extends AbstractGeoGigOp<?>>) instance.getClass());
        }
        return canDecorate;
    }

    @Override
    public <I> I decorate(I subject) {
        AbstractGeoGigOp<?> op = (AbstractGeoGigOp<?>) subject;
        CommandHookChain callChain = CommandHookChain.builder().command(op).build();
        if (!callChain.isEmpty()) {
            op.addListener(new HooksListener(callChain));
        }
        return subject;
    }

    private static class HooksListener implements CommandListener {

        private CommandHookChain callChain;

        public HooksListener(CommandHookChain callChain) {
            this.callChain = callChain;
        }

        @Override
        public void preCall(AbstractGeoGigOp<?> command) {
            callChain.runPreHooks();
        }

        @Override
        public void postCall(AbstractGeoGigOp<?> command, Object result, boolean success) {
            callChain.runPostHooks(result, success);
        }

    }
}
