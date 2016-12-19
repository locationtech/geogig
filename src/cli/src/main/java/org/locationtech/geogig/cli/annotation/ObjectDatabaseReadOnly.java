/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.storage.ObjectDatabase;

/**
 * Annotation for {@link CLICommand}s giving geogig a hint that the operation is read only for
 * {@link ObjectDatabase}, but may need write access to any remote repository it may connect to.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObjectDatabaseReadOnly {

}
