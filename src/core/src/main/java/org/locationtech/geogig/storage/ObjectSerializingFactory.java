/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;

/**
 * The ObjectSerializingFactory is used to create instances of the various writers and readers used
 * to work with the serialized forms of various repository elements.
 * 
 */
public interface ObjectSerializingFactory {

    // /**
    // * Creates an instance of a commit reader.
    // *
    // * @return commit reader
    // */
    //
    // public ObjectReader<RevCommit> createCommitReader();
    //
    // /**
    // * Creates an instance of a RevTree reader.
    // */
    // public ObjectReader<RevTree> createRevTreeReader();
    //
    // /**
    // * Creates an instance of a Feature reader that can parse features.
    // *
    // * @return feature reader
    // */
    // public ObjectReader<RevFeature> createFeatureReader();
    //
    // /**
    // * Creates an instance of a Feature reader that can parse features.
    // *
    // * @param hints feature creation hints
    // * @return feature reader
    // */
    // public ObjectReader<RevFeature> createFeatureReader(final Map<String, Serializable> hints);
    //
    // /**
    // * Creates an instance of a feature type reader that can parse feature types.
    // *
    // * @return feature type reader
    // */
    // public ObjectReader<RevFeatureType> createFeatureTypeReader();
    //
    // public <T extends RevObject> ObjectWriter<T> createObjectWriter(TYPE type);
    //
    // /**
    // * @param type
    // * @return
    // */
    // public <T extends RevObject> ObjectReader<T> createObjectReader(TYPE type);
    //
    // public ObjectReader<RevObject> createObjectReader();

    void write(RevObject o, OutputStream out) throws IOException;

    RevObject read(ObjectId id, InputStream in) throws IOException;
}
