/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import org.locationtech.geogig.storage.FieldType;

import com.google.gson.annotations.Expose;

public class AttributeDefinition {

    @Expose
    private String name;

    @Expose
    private FieldType type;

    public AttributeDefinition(String name, FieldType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public boolean equals(Object o) {
        if (o instanceof AttributeDefinition) {
            AttributeDefinition at = (AttributeDefinition) o;
            return name.equals(at.name) && at.type.equals(type);
        } else {
            return false;
        }
    }

}
