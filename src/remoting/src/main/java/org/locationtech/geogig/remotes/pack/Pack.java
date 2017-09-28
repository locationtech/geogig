/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import java.util.List;

import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.ProgressListener;

public interface Pack {

    public List<RefDiff> applyTo(PackProcessor target, ProgressListener progress);
}
