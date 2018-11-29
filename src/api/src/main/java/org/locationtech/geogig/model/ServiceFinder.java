/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * A service finder to lookup default instances of a given interface by a system property, and
 * environment variable, and/or the {@link ServiceLoader} SPI mechanism.
 * <p>
 * For a given interface, a default implementation is looked up in this order:
 * <ul>
 * <li>1. By a <strong>System property</strong>, if provided through
 * {@link #systemProperty(String)}. (e.g. {@code java
 * -DpropertyName=<fully qualified class name>}
 * <li>2. By an <strong>environment variable</strong>, if provided through
 * {@link #environmentVariable(String)}. (e.g. {@code export
 * ENV_VAR=<fully qualified class name> && java ...}
 * <li>3. By using the <strong>{@code java.util.ServiceLoader} SPI</strong> mechanism looking for
 * implementations of {@code type}, and choosing the one with the highest
 * {@link PriorityService#getPriority() priority} in case there are multiple implementations in the
 * classpath and the provided argument class extends {@link PriorityService}.
 * 
 * @since 1.4
 */
@Accessors(chain = true, fluent = true)
@Slf4j
public class ServiceFinder {

    private @Setter @Nullable String systemProperty;

    private @Setter @Nullable String environmentVariable;

    /**
     * Implements lookup mechanism
     * 
     * @throws NoSuchElementException
     */
    public <T> T lookupDefaultService(@NonNull Class<T> type) {
        T defaultInstance = null;
        if (systemProperty != null) {
            defaultInstance = lookupSystemProperty(type, systemProperty);
        }
        if (defaultInstance == null && environmentVariable != null) {
            defaultInstance = lookupEnvironmentVariable(type, environmentVariable);
        }
        if (defaultInstance == null) {
            defaultInstance = lookupService(type);
        }
        if (defaultInstance == null) {
            String spropMsg = systemProperty == null ? null
                    : String.format(" System property '%s'", systemProperty);
            String envMsg = environmentVariable == null ? null
                    : String.format(" Environment variable '%s'", environmentVariable);

            String msg = String.format("No implementation of %s found through", type.getName());
            if (spropMsg != null) {
                msg += spropMsg;
            }
            if (envMsg != null) {
                msg += (spropMsg == null ? "" : ", ") + envMsg;
            }
            msg += (spropMsg != null || envMsg != null ? " nor" : "") + " ServiceLoader";
            log.error(msg);
            throw new NoSuchElementException(msg);
        }
        return defaultInstance;
    }

    public <T> List<T> lookupServices(@NonNull Class<T> type) {
        log.debug("Looking up instances of service {}", type.getName());
        ServiceLoader<T> loader = ServiceLoader.load(type);
        ArrayList<T> services = Lists.newArrayList(loader.iterator());
        log.debug("Found {} instances of service {}: {}", services.size(), type.getName(),
                services.stream().map(s -> s.getClass().getName()).collect(Collectors.toList()));
        return services;
    }

    public @Nullable <T> T lookupService(@NonNull Class<T> type) {
        List<T> spiImpls = lookupServices(type);
        if (spiImpls.isEmpty()) {
            return null;
        }
        final boolean isPrioritizable = PriorityService.class.isAssignableFrom(type);
        if (isPrioritizable) {
            // sort in descending priority order
            Collections.sort(spiImpls,
                    (i1, i2) -> Integer.compare(((PriorityService) i2).getPriority(),
                            ((PriorityService) i1).getPriority()));
        }
        T instance = spiImpls.get(0);
        T next = spiImpls.size() < 2 ? null : spiImpls.get(1);
        if (next != null) {
            if (isPrioritizable) {
                if (((PriorityService) next).getPriority() == ((PriorityService) instance)
                        .getPriority()) {
                    log.warn("More than one implementation of {} found using ServiceLoader "
                            + "with the same priority of {}"
                            + "defaulting to the first one found of type {}. All additional factories: {}.",
                            type.getName(), instance.getClass().getName(),
                            ((PriorityService) instance).getPriority(),
                            spiImpls.subList(1, spiImpls.size()).stream()
                                    .map(i -> i.getClass().getName()).collect(Collectors.toList()));
                }
            } else {
                String msg = String.format("Unable to determine default implementation of {}. "
                        + "Found several instances and it's not a prioritizable service: %s ",
                        type.getName(), spiImpls.stream().map(s -> s.getClass().getName())
                                .collect(Collectors.toList()));
                log.error(msg);
                throw new IllegalStateException(msg);
            }

            log.debug("Implementation of {} of type {} found using ServiceLoader", type.getName(),
                    instance.getClass().getName());
        }
        return instance;
    }

    public @Nullable <T> T lookupSystemProperty(@NonNull Class<T> type,
            @NonNull String systemPropertyName) {
        log.debug("Looking up implementation of {} as System property parameter {}", type.getName(),
                systemPropertyName);
        String className = System.getProperty(systemPropertyName);
        if (Strings.isNullOrEmpty(className)) {
            log.debug("{} not provided as System property.", systemPropertyName);
            return null;
        }
        log.debug("Found {} implementation as system propety {}={}", type.getName(),
                systemPropertyName, className);

        try {
            Object newInstance = Class.forName(className).newInstance();
            T service = type.cast(newInstance);
            log.debug("Created instance of {} of type {} given as system property {}",
                    type.getName(), service.getClass().getName(), systemPropertyName);
            return service;
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to instantiate '" + className
                    + "' provided as System property " + systemPropertyName, e);
        }
    }

    public @Nullable <T> T lookupEnvironmentVariable(@NonNull Class<T> type,
            @NonNull String environmentVariable) {

        log.debug("Looking up implementation of {} as environment variable {}", type.getName(),
                environmentVariable);
        String className = System.getenv(environmentVariable);
        if (Strings.isNullOrEmpty(className)) {
            log.debug("{} not provided as environment variable {}.", type.getName(),
                    environmentVariable);
            return null;
        }
        log.debug("Found environment variable {}={}, instantiating argument class",
                environmentVariable, className);

        try {
            Object newInstance = Class.forName(className).newInstance();
            T service = type.cast(newInstance);
            log.debug("Created instance of {} of type {} given as environment variable {}",
                    type.getName(), service.getClass().getName(), environmentVariable);
            return service;
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to instantiate '" + className
                    + "' provided as environment variable " + environmentVariable, e);
        }
    }
}
