/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.geotools.data.reader;

import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.expression.PropertyName;

/**
 * this is very simple, it changes a property reference.
 * i.e.  "property" = 'a'     TO    "new_property" = 'a'
 */
public class RenamePropertyFilterVisitor extends DuplicatingFilterVisitor {

        String originalPropertyName;

        String newPropertyName;

        public RenamePropertyFilterVisitor(String pName, String newPname) {
                this.originalPropertyName = pName;
                this.newPropertyName = newPname;
        }

        @Override public Object visit(PropertyName expression, Object extraData) {
                String pName = expression.getPropertyName();
                if ((pName != null) && (pName.equals(originalPropertyName))) {
                        return getFactory(extraData)
                                .property(newPropertyName, expression.getNamespaceContext());
                }
                return super.visit(expression, extraData);
        }
}
