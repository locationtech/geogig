/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */

/**
 * This package encloses the GeoGig revision graph object model, comprised of a set of abstractions
 * that denote everything that's stored in a GeoGig repository in order to implement a
 * <a href="https://en.wikipedia.org/wiki/Distributed_version_control"> distributed revision control
 * system</a> for vectorial geospatial information.
 * <p>
 * The most important objects in a GeoGig repository are the "revision objects". For instance,
 * {@link org.locationtech.geogig.model.RevCommit commits},
 * {@link org.locationtech.geogig.model.RevTag tags}, {@link org.locationtech.geogig.model.RevTree
 * trees}, {@link org.locationtech.geogig.model.RevFeatureType feature types}, and
 * {@link org.locationtech.geogig.model.RevFeature features}; immutable data structures whose
 * relationships comprise a repository's "revision graph".
 * <p>
 * A repository is, hence, a collection of revision objects that together allow to track history and
 * lineage of geospatial datasets.
 * <p>
 * All the operations available on GeoGig are ultimately related to administering these revision
 * objects in a repository by creating these immutable data structures and managing the
 * relationships between them in order to control the evolution of the repository's revision graph.
 * <p>
 * The repository revision graph, conceptually, is a
 * <a href="https://en.wikipedia.org/wiki/Directed_acyclic_graph">Directed Acyclic Graph</a> (DAG)
 * whose entry points are commits.
 * 
 * 
 * @apiNote all method arguments and return values are non-null by default, except if they're
 *          annotated with {@code @org.eclipse.jdt.annotation.Nullable}
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package org.locationtech.geogig.model;
