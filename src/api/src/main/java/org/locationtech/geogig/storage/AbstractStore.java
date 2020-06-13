/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

public abstract class AbstractStore implements Store {

    protected boolean readOnly;

    protected boolean open;

    protected AbstractStore() {
        this(false);
    }

    protected AbstractStore(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public @Override void open() {
        open = true;
    }

    public @Override void close() {
        open = false;
    }

    public @Override boolean isOpen() {
        return open;
    }

    public @Override boolean isReadOnly() {
        return readOnly;
    }

}
