/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashFunction;

/**
 * Base interface for the closed set of revision objects that are stored in a GeoGig repository.
 * <p>
 * All {@link RevObject}s have a {@link #getType() type} and {@link #getId() id}.
 * <p>
 * The id is an {@link ObjectId} computed algorithmically by {@link HashObjectFunnels} with a SHA-1
 * {@link HashFunction HashFunction}, the the type is given by the concrete kind of revision object.
 * 
 * @apiNote all revision objects are immutable data structures that describe the contents of an
 *          instance of the specific type. When stored in a repository, the id shall not be included
 *          as part of its serialized form, but only its contents. Given the case, specific checks
 *          can be implemented to ensure a revision object obtained from the repository for a given
 *          {@link ObjectId} do hash out to the expected id.
 * @implNote Given any two objects of the same type with the same exact contents shall hash out to
 *           the same {@link ObjectId}, {@link #equals(Object) equality} checks can merely compare
 *           the two {@link ObjectId}s for equality.
 * 
 * @see RevCommit
 * @see RevTree
 * @see RevFeature
 * @see RevFeatureType
 * @see RevTag
 * 
 * @since 1.0
 */
public interface RevObject {
    /**
     * A "natural order" {@link Ordering comparator} for revobject instances based on the
     * {@link ObjectId}
     */
    public static final Ordering<RevObject> NATURAL_ORDER = new Ordering<RevObject>() {
        @Override
        public int compare(RevObject o1, RevObject o2) {
            return o1.getId().compareTo(o2.getId());
        }
    };

    /**
     * {@code RevObject} types enumeration.
     */
    public static enum TYPE {
        COMMIT {
            @Override
            public int value() {
                return 0;
            }

            @Override
            public Class<RevCommit> binding() {
                return RevCommit.class;
            }
        },
        TREE {
            @Override
            public int value() {
                return 1;
            }

            @Override
            public Class<RevTree> binding() {
                return RevTree.class;
            }
        },
        FEATURE {
            @Override
            public int value() {
                return 2;
            }

            @Override
            public Class<RevFeature> binding() {
                return RevFeature.class;
            }
        },
        TAG {
            @Override
            public int value() {
                return 3;
            }

            @Override
            public Class<RevTag> binding() {
                return RevTag.class;
            }
        },
        FEATURETYPE {
            @Override
            public int value() {
                return 4;
            }

            @Override
            public Class<RevFeatureType> binding() {
                return RevFeatureType.class;
            }
        };

        public abstract int value();

        public abstract Class<? extends RevObject> binding();

        public static TYPE valueOf(final int value) {
            return TYPE.values()[value];
        }

        private static final ImmutableMap<Class<? extends RevObject>, Integer> byBinding = ImmutableMap
                .of(COMMIT.binding(), COMMIT.value(), TREE.binding(), TREE.value(),
                        FEATURE.binding(), FEATURE.value(), TAG.binding(), TAG.value(),
                        FEATURETYPE.binding(), FEATURETYPE.value());

        public static TYPE valueOf(final Class<? extends RevObject> binding) {
            Preconditions.checkNotNull(binding);
            Integer value = byBinding.get(binding);
            Preconditions.checkNotNull(value);
            return valueOf(value);
        }
    }

    /**
     * @return the object type of this object
     */
    public TYPE getType();

    /**
     * @return unique hash of this object.
     */
    public ObjectId getId();

    /**
     * @implNote Given any two objects of the same type with the same exact contents shall hash out
     *           to the same {@link ObjectId}, {@link #equals(Object) equality} checks can merely
     *           compare the two {@link ObjectId}s for equality.
     * 
     */
    @Override
    public boolean equals(Object o);
}
