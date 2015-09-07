MapDB Storage for GeoGig
========================

This module provides support for storing the GeoGig object graph in files managed by
`MapDB <http://mapdb.org/>`.

Author: Sebastian Schmidt, SWM Services GmbH, 2015

Current Status
--------
Prototyped by taking Graph storage Implementation from heap Storage module
and taking object storage implementation from MongoDB, adjusting to mapdb.

How to activate mapdb storage
---------------
* Run geogig init --config mapdb.version=0.1,storage.graph=mapdb,storage.objects=mapdb
to init a new Geogig Repo with mapdb storage format.
* start working.

Todos
--------
* Serialization: We're currently using toString() for serialization of Conflict Objects - is there a better way?

* Edge, Node, PathToRootWalker have been copied from heap Storage module and have been made serializable

* Graph has been taken nearly unchanged from heap Storage module 
--> do some refactoring to have more reuse

* which Serializer for Objects should be used? Version 1 or 2?

* Object storage: is multithreaded putAll() really necessary? How big should the chunks be?
(and some more, see //TODO tasks in the code)

* Performance tests: Determine best options for mapdb storage

Testing
-------
* which integration tests to implement? Currently all that were in mongoDB storage..

Known Bugs
----------
Conflict Storage does not work correctly with submaps as with mapdb-2.0.0-beta6,
currently a less efficient workaround is in place
See also: https://github.com/jankotek/mapdb/issues/569, already fixed, but not released up to now.