/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CheckoutResult {

    public enum Results {
        NO_RESULT, DETACHED_HEAD, CHECKOUT_REMOTE_BRANCH, CHECKOUT_LOCAL_BRANCH, UPDATE_OBJECTS
    }

    private @Setter(value = AccessLevel.PACKAGE) ObjectId oid;

    private @Setter(value = AccessLevel.PACKAGE) Ref newRef;

    private @Setter(value = AccessLevel.PACKAGE) String remoteName;

    private @Setter(value = AccessLevel.PACKAGE) ObjectId newTree;

    private @NonNull @Setter(value = AccessLevel.PACKAGE) Results result = Results.NO_RESULT;
}
