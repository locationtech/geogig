
.. _geogig-osm-map:

geogig-osm-map documentation
############################



SYNOPSIS
********
geogig osm map <file> [<message>]


DESCRIPTION
***********

Create new data in the repository, applying a mapping to the current OSM data.

The mapping is stored as a JSON file, using the following syntax

::

	{"rules":[{"name":"onewaystreets","filter":{"oneway":["yes"]},"fields":{"lit":{"name":"lit", "type":STRING"},"geom":{"name":"geom", "type":LINESTRING"}}]}

A mapping description is an array of mapping rules, each of them with the following fields:
 
- ``name`` defines the name of the mapping, which is used as the destination tree.
- ``filter`` is a set of tags and values, which define the entities to use for the tree. All entities which have any of the specified values for any of the given tags will be used. And empty filter will cause all entities to be used.
  To get all entities that have a given tag, no matter which value the tag gas, just use an empty list for the accepted values. 
- ``fields`` describes the attributes for the feature type, as ``tag_name:{"name":field_name, "type":field_type}`` values. Usually, ``tag_name`` and ``field_name`` will be identical, so the name of the tag is used as the field name. However, you can use a different name for the field, which will act as an alias for the tag.
  Valid types for the ``field_type`` are ``INTEGER, FLOAT, DOUBLE, LONG SHORT, POINT LINE, POLYGON, STRING, DATE``. Only one of the geometry types can be used for a field in a mapping rule. This defines the type of entities that will be used, so it acts as a filter as well. So, if you add a field of type ``POINT``, it will use only those entities represented as a points. That is, it will use only nodes. ``LINESTRING``  and ``POLYGON`` will cause only ways to be used. In both cases, all ways are used, even if they are not closed (they will be automatically closed to create the polygon). It is up to you to define the criteria for a way to be suitable for creating a polygon, such as, for instance, requiring the ``area=yes`` or "building=yes" tag/value pair.

  Apart from the fields that you add to the feature type in your mapping definition, GeoGig will always add an ``id`` field with the OSM Id of the entity. This is used to track the Id and allow for unmapping. In the case of ways, another field is added, ``nodes``, which contains the Id's of nodes that belong to the way. You should avoid using ``id`` or ``nodes`` as names of your fields, as that might cause problems.

A commit will be created after the mapping, and the working tree and index have to be clean before performing the mapping operation. The ``--message`` option can be used as well to set a given commit message.

OPTIONS
*******

<file>		The filename where the mapping to use is defined

--message <message>		The message to use for the commit that is created after the mapping operatio is performed
    

SEE ALSO
********

:ref:`geogig-osm-import`

:ref:`geogig-osm-unmap`

BUGS
****

Discussion is still open.

