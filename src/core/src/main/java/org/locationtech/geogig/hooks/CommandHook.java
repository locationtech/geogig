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

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import lombok.NonNull;

/**
 * An interface that defines hooks for {@link AbstractGeoGigOp command} written in Java as opposed
 * to a scripting language.
 * <p>
 * Implementations of this interface are discovered using the standard Java {@link ServiceLoader}
 * SPI lookup, by looking for implementing class names at
 * {@code META-INF/services/org.locationtech.geogig.hooks.CommandHook} resources.
 * <p>
 * Implementations must have a default constructor (or no explicit constructor at all), and must be
 * thread safe.
 */
public interface CommandHook extends Comparable<CommandHook> {

    /**
     * The hook chain for a command is composed using priority order higher to lower, the default
     * priority is {@code 0}, there are no execution order guarantees for two hooks with the same
     * priority.
     * 
     * @return the hook's priority
     */
    public default int getPriority() {
        return 0;
    }

    public default @NonNull List<CommandHook> unwrap(@NonNull AbstractGeoGigOp<?> command) {
        return Collections.singletonList(this);
    }

    public default @Override int compareTo(@NonNull CommandHook other) {
        // reverse order, higher priority wins
        return Integer.compare(other.getPriority(), getPriority());
    }

    /**
     * @throws CannotRunGeogigOperationException
     */
    public <C extends AbstractGeoGigOp<?>> C pre(C command);

    public <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception;

    public boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz);
}
