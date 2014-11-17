/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

/**
 * Symbolic reference.
 */
public class SymRef extends Ref {

    private Ref target;

    /**
     * Constructs a new {@code SymRef} with the given name and target reference.
     * 
     * @param name the name of the symbolic reference
     * @param target the reference that this symbolic ref points to
     */
    public SymRef(String name, Ref target) {
        super(name, target.getObjectId());
        this.target = target;
    }

    /**
     * @return the reference that this symbolic ref points to
     */
    public String getTarget() {
        return target.getName();
    }

    @Override
    public String toString() {
        return new StringBuilder("SymRef").append('[').append(getName()).append(" -> ")
                .append(target.toString()).append(']').toString();
    }

}
