/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.storage.ObjectWriter;
import org.locationtech.geogig.storage.text.TextSerializationFactory;

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

        TextSerializationFactory factory = new TextSerializationFactory();
        ObjectWriter<RevObject> writer = factory.createObjectWriter(revObject.getType());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String s = "id\t" + revObject.getId().toString() + "\n";
        OutputStreamWriter streamWriter = new OutputStreamWriter(output, Charsets.UTF_8);
        try {
            streamWriter.write(s);
            streamWriter.flush();
            writer.write(revObject, output);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot print object: " + revObject.getId().toString(),
                    e);
        }
        return output.toString();
    }
}
