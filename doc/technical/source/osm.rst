OSM
====

Problems to solve / Things to consider  about OSM commands in GeoGig:
----------------------------------------------------------------------

1. Updating
~~~~~~~~~~~~
The current update command just re-downloads with the last filter, but is not a smart download, and downloads everything. The overpass API allows to fetch only features newer than a given data, but that cannot be used if there are deletions, since it does not report deleted elements.

The OSM API allows to download history, including deletions, but does not support filters.

Ideally, a mix of both functionalities would be needed for geogig to work optimally

2. Unmapping of ways
~~~~~~~~~~~~~~~~~~~~~
When a way is unmapped, its geometry is used to re-create the list of nodes. The best way would be to take the coords of the nodes and check if a node exist in each coordinate, and if so, take the node id, otherwise, add a new node. This is, however, not possible now, since it would not be efficient. GeoGig has no spatial indexing, and searching a feature by its coordinates is not an available operation.

The current implementation just retrieves the nodes that belonged to the way in the last version, and check the current geometry against them. This is fine if all new nodes are actually new, but if the way uses a node that it did not use before but that exists already, that node will not be used (since there is no way of retrieving the id of the node in that coord), and a new one in that same position is added.

3. Updating new entities
~~~~~~~~~~~~~~~~~~~~~~~~~
When a new node is added (whether by the user, who created it in something like JOSM, or by an unmap operation), new entities get a negative ID, as it seems customary in OSM before committing them. Once submitted, they get a valid ID, and when later updating, the Id's will not match, so GeoGig will not replace them, leaving both versions.

This is, in fact, not a problem now, since the update operation just deletes and updates everything (see (1)), but once we get a more efficient update strategy, this problem will surface.


OSM Paths
----------

.. note::

  This is just an idea, not implemented yet. Is it a good idea??

The default paths for OSM data are ``way`` and ``node``. they should contain just OSM data imported using the corresponding GeoGig commands. To use those paths for different data and avoid problem with OSM commands, the default paths can be changed using the ``config`` command. Default paths are kept in the ``osm.nodepath`` and ``osm.waypath`` config parameters, which can be configured as shown in the example below.

::

	$ geogig config osm.nodepath osmnode
	$ geogig config osm.waypath osmway
