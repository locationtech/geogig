/* Copyright (c) 2014-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.io.Closeable;
import java.io.IOException;

import org.locationtech.geogig.model.RevObject;

/**
 * A closeable funnel used to transparently send objects to a remote resource.
 */
public interface ObjectFunnel extends Closeable {

    public void funnel(RevObject object) throws IOException;
}
