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

public class CheckoutResult {

    public enum Results {
        NO_RESULT, DETACHED_HEAD, CHECKOUT_REMOTE_BRANCH, CHECKOUT_LOCAL_BRANCH, UPDATE_OBJECTS
    }

    private ObjectId oid = null;

    private Ref newRef = null;

    private String remoteName = null;

    private ObjectId newTree = null;

    private Results resultEnumVal = Results.NO_RESULT;

    public ObjectId getOid() {
        return oid;
    }

    public CheckoutResult setOid(ObjectId oid) {
        this.oid = oid;
        return this;
    }

    public Ref getNewRef() {
        return newRef;
    }

    public CheckoutResult setNewRef(Ref newRef) {
        this.newRef = newRef;
        return this;
    }

    public ObjectId getNewTree() {
        return newTree;
    }

    public CheckoutResult setNewTree(ObjectId newTree) {
        this.newTree = newTree;
        return this;
    }

    public Results getResult() {
        return resultEnumVal;
    }

    public CheckoutResult setResult(Results result) {
        if (this.resultEnumVal == Results.NO_RESULT) {
            this.resultEnumVal = result;
        }
        return this;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

}
