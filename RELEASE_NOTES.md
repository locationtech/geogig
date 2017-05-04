GeoGig 1.1-beta2 Release Notes
==============================

May 2, 2017.

This release is primarily a bug fix release. Notable changes since 1.1-beta1 are:

* Performance improvements to internal threading.
* Fix a bug in updating quadtrees.
* Fix a bug when retrieving repository logs for very old ancestors with large numbers of commits.
* Enable and fix bugs around the ability to cancel many GeoGig operations.
* Fix a bug in export-diff where incorrect commit IDs were sometimes being used.
* Add and enhance concurrency testing.

GeoGig 1.1-beta1 Release Notes
==============================

April 18, 2017.

This release introduces the long awaited spatial indexing capabilities to GeoGig.

GeoGig's spatial index can index the whole history of a repository's datasets. It was
developed from scratch to take advantage of geogig's core design principle to efficiently
store large amounts of features and quickly compute the differences between any two
snapshots, the Merkle Tree (a.k.a. Hash Tree), that allows us to create a DAG (Directed
Acyclic Graph) where each data snapshot is an entry point to the graph, while they share
all but what changes internally.

To the best of our knowledge, this is the first spatial index that can effectively
work on such a graph of data.

When an index is created for a feature tree (i.e. layer), a new tree is created mirroring
the canonical tree contents, but using a quad-tree clustering strategy for its non terminal
nodes.

Spatial indexing capabilities:

* Indexes are automatically updated on commits. Create an index for a layer, and any operation
that creates a new commit will trigger the update of the index (i.e. commit, rebase, merge, etc).
* Materialized attributes: the spatial index can materialize any spatial or non spatial attribute
of the FeatureType being indexed, in order to speed up attribute queries as well as spatial ones.
For example, you might want to include the attributes needed by the Styled Layer Descriptor to properly
filter out Features for rendering on a WMS; or include the time/elevation attributes to speed up
queries on time series in GeoServer. 

* Spatial indexing command set: the geogig command line and its WEB API include a full set of
commands to manage the indexes. Run `geogig index --help` or browse the online documentation at
http://geogig.org/docs for more information.


Other improvements in this release:

* Better support for foreign CRS definitions. Importing from datasets that define its coordinate
reference system in non OGC WKT formats (e.g. most shapefiles) are now correctly matches to their
corresponding CRS from the EPSG database, providing extra CRS metadata such as area of validity.

* Better PostgreSQL backend caching. The PostgreSQL backend's internal cache uses an improved
serialization format that's a lot faster to decode, while still allowing to control the cache
size by storing serialized objects instead of Java objects.

GeoGig 1.0.0 Release Notes
==========================

December 21, 2016.

The GeoGig team is proud to announce the graduation of Release Candidate 1.0-RC4 to 1.0!
Only one minor bug was found in RC4 (related to deriving its version) that needed to be 
fixed for the 1.0 release. Please see the Release Notes for RC4 below for more.

GeoGig 1.0-RC4 Release Notes
============================

December 19, 2016.

These are the release notes for the fourth GeoGig release candidate.

Five important things happended since 1.0-RC3 besides several bug fixes:

* First and foremost, the developer's team got three new memebers. Join us
to congratulate (and thank!) David Blasby, Morgan Thompson, and Alex Goudine
for becoming geogig committers and help us drive this project further and farther.

* We have cleared out all of our Intellectual Property checks on all our
dependencies and have now [LocationTech](https://locationtech.org/) blessing to perform
a sponsored release!
Thanks to LocationTech, Eclipse Foundation's geospatial branch, for their
continued support and commitment to the success of this and the other projects
under its umbrella.

* The new RevTreeBuilder infrastructure became mainstream. It replaces the
legacy tree builder in order to generate revision trees faster, create no
garbage in the objects database (the old one left dangling tree objects
increasing the database size unnecessarily), and perhaps more importantly,
enables the use of the same infrastructure to build spatial and attribute
indexes on revision graphs (yes, you'll be able to index all your data 
history, stay tunned).

* The PosgreSQL storage backend is now WAL and replication safe, by moving
to use B-Tree indexes instead of Hash indexes. Thanks David Blasby for the
thorough performance testing. 

* The default storage backend for local repositories now uses [Rocksdb](http://rocksdb.org/).
It's faster than the old BerkeleyDB Java Edition database, and produces
smaller databases on disk.
The BerkeleyDB storage backend was taken off the official build, but Boundless
still provides a separate plugin download, so if you have existing repositories
just download [the bdbje plugin](https://github.com/locationtech/geogig/releases/download/v1.0-RC4/geogig-plugins-bdbje-1.0-RC4.zip) and unzip it
under your `<geogig installation directory>/lib/` folder.

We are really really close to a first 1.0 official release now! Maybe even for Christmas!

Other important bug fixes and improvements:
-------------------------------------------

- Fix deadlock due to diff traversal not being closed
- Make sure merge aborts cleanly
- Replace WriteBack by the more efficient UpdateTree
- Add license information to documentation
- Add functional tests for web api commands.
- Fix windows handling of ANSI escapes 
- Fix Incompatible Feature Type error for equivalent CRSs
- Make geopackage fid mappings memory safe by using a rocksdb-backed map
- Fix parsing of PostgreSQL URIs 
- Update documentation for api module
- Increase unit test coverage of API Module
- Improve handling of incorrect parameters in geotools export commands
- Fix cloning from absolute paths
- Add NOTICE file with dependencies 
- Make the new RevTreeBuilder the default
- Add functional tests for http synchronization endpoints
- Serialization version 2.1 
- Add Eclipse CBI signing support to main pom.xml
- move to using BTREE instead of HASH index for postgresql backend 
- Fix missing feature mappings from GeoPackage import response


GeoGig 1.0-RC3 Release Notes
============================

August 4, 2016.

These are the release notes for the third GeoGig release candidate.

We have worked hard since RC2 on fixing the last outstanding bugs that prevented 
a solid release, and focused on some performance improvements.

This release is also the official launch of the PostgreSQL storage backend [1],
which allows to store several repositories in a single postgres database.

Also, the GeoServer plugin is being released for GeoServer 2.8, 2.9, and 2.10 series,
with several improvements both on GUI [2] and REST [3] configuration.

[1] <http://geogig.org/docs/repo/storage.html>
[2] <http://geogig.org/docs/interaction/geoserver_ui.html>
[3] <http://geogig.org/docs/interaction/geoserver_web-api.html>

The next steps are a strong focus on performance and scalability and a final
1.0 release by the end of the year. Stay tuned and write us back with your 
feedback about this important milestone.

The GeoGig team.-

Important features
------------------

- PostgresSQL storage backend
- GeoServer 2.8, 2.9, and 2.10 plugin
- GeoServer support for PostgreSQL repositories
- Several GeoServer GUI and REST improvements and user docs

Improvements
------------
- Update Postgres backend to use a serial integer as repository primary key 
- Move core API to a new geogig-api module 
- Allow amend to only update commit message. 
- Dramatic improvement for mergeop when merging features. 


Most Important Bug fixes
------------------------

- IMPORTANT BUG EXPOSED: leaf RevTree node storage order is wrong
- Geometry automerge is dangerous and produces invalid geometries
- Unwanted geometry changes on re-import of polygon layers bug enhancement
- Immutability of RevFeature compromised
- Enforce normalization of Polygon geometries
- Update Diff traversals to close properly when terminated early
- `reset --hard` and `merge --abort` leave dangling blobs
- `add` command deletes all conflicts
- GeoGigFeatureStore removes feature tree if instructed to delete all features in a layer

