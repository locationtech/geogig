/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

public interface RevTag extends RevObject {

    /**
     * @return the name
     */
    public abstract String getName();

    /**
     * @return the message
     */
    public abstract String getMessage();

    /**
     * @return the tagger
     */
    public abstract RevPerson getTagger();

    /**
     * @return the {@code ObjectId} of the commit that this tag points to
     */
    public abstract ObjectId getCommitId();

}