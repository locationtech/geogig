/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.text.TextRevObjectSerializer;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/**
 * Provides content information for repository objects
 */
public class CatObject extends AbstractGeoGigOp<CharSequence> {

    private Supplier<? extends RevObject> object;

    public CatObject setObject(Supplier<? extends RevObject> object) {
        this.object = object;
        return this;
    }

    @Override
    protected CharSequence _call() {
        Preconditions.checkState(object != null);
        RevObject revObject = object.get();

        TextRevObjectSerializer serializer = TextRevObjectSerializer.INSTANCE;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String s = "id\t" + revObject.getId().toString() + "\n";
        OutputStreamWriter streamWriter = new OutputStreamWriter(output, Charsets.UTF_8);
        try {
            streamWriter.write(s);
            streamWriter.flush();
            serializer.write(revObject, output);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot print object: " + revObject.getId().toString(),
                    e);
        }
        return output.toString();
    }
}
