.. _web-api-indexing-commands:

Web API: Indexing Commands
==========================

The GeoGig indexing web API allows for the creation, updating, and listing of spatial indexes on feature trees within the repository.

Index create
------------

Creates a new index on a specified feature tree using a geometry attribute in that tree.  Extra attributes may also be specified in order to improve query performance when the data is filtered on those attributes.

::

   PUT /repos/<repo>/index/create[.xml|.json]?treeRefSpec=<treeRefSpec>[&geometryAttributeName=<attributeName>][[&extraAttributes=<attributeName>]+][&indexHistory=<true|false>][&bounds=<minx,miny,maxx,maxy>]


Parameters
^^^^^^^^^^

**treeRefSpec:**
Mandatory. Defines the ref spec that resolves to the feature tree that should be indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

**geometryAttributeName:**
Optional. Defaults to the primary geometry attribute on the feature type.  The name of the attribute that should be used for indexing.

**extraAttributes:**
Optional. An extra attribute that should be stored in the index to improve performance when filtering on those attributes.  This can be defined multiple times if there should be multiple extra attributes.

**indexHistory:**
Optional. Boolean indicating whether or not index trees should be built for every commit in the history of the repository.  By default only the feature tree in commit indicated by the ``treeRefSpec`` will be indexed.

**bounds:**
Optional.  String indicating the max bounds of the spatial index.  If not specified, the bounds will be set to the extent of the coordinate reference system of the geometry attribute.

Examples
^^^^^^^^

Create an index on Points
**************************

::

	$ curl -X PUT -v "http://localhost:8182/repos/repo1/index/create?treeRefSpec=Points" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>

Create an index with extra attributes
**************************************

::

	$ curl -X PUT -v "http://localhost:8182/repos/repo1/index/create?treeRefSpec=Points&extraAttributes=ip&extraAttributes=sp" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
            <extraAttribute>ip</extraAttribute>
            <extraAttribute>sp</extraAttribute>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>

Create an index with custom bounds
***********************************

::

	$ curl -X PUT -v "http://localhost:8182/repos/repo1/index/create?treeRefSpec=Points&bounds=-60,-45,60,45" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-60,60,-45,45]</bounds>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>


Index update
------------

Updates an index to contain a different set of extra attributes.

::

   POST /repos/<repo>/index/update[.xml|.json]?treeRefSpec=<treeRefSpec>[&geometryAttributeName=<attributeName>][[&extraAttributes=<attributeName>]+][&indexHistory=<true|false>][&add|overwrite=<true|false>]


Parameters
^^^^^^^^^^

**treeRefSpec:**
Mandatory. Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

**geometryAttributeName:**
Optional. The name of the attribute that is used on the existing index. Defaults to the primary geometry attribute on the feature type.

**extraAttributes:**
Optional. An extra attribute that should be stored in the index to improve performance when filtering on those attributes.  This can be defined multiple times if there should be multiple extra attributes.

**indexHistory:**
Optional. Boolean indicating whether or not index trees should be rebuilt for every commit in the history of the repository.  By default only the feature tree in the commit indicated by the ``treeRefSpec`` will be re-indexed.

**add:**
Optional. If extra attributes already exist on the index, you must specify either ``add`` or ``overwrite`` to inform the operation how to handle combining the new attributes with the old.  If ``add`` is specified, any new attributes that do not already exist on the index will be added.

**overwrite:**
Optional: See ``add``.  If ``overwrite`` is specified, the extra attributes in the index will be replaced with those specified in the parameters.  If no extra attributes are supplied, all extra attributes will be removed from the index.

**bounds:**
Optional.  String indicating the new maximum bounds of the spatial index.

Examples
^^^^^^^^

Update the Points index to have an extra attribute
***************************************************

If the index does not contain any extra attributes, you do not need to specify ``add`` or ``overwrite``.

::

	$ curl -X POST -v "http://localhost:8182/repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
            <extraAttribute>ip</extraAttribute>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>

Update the Points index to add an extra attribute
**************************************************

In this case Points already has an extra attribute of ``sp``.  If we want to add ``ip`` we need to specify the ``add`` parameter to indicate that we don't want to remove the existing extra attribute.

::

	$ curl -X POST -v "http://localhost:8182/repos/repo1/index/update?treeRefSpec=Points&extraAttributes=ip&add=true" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
            <extraAttribute>ip</extraAttribute>
            <extraAttribute>sp</extraAttribute>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>

Update the Points index to remove extra attributes
***************************************************

In this case Points already has an extra attribute of ``sp``.  If we want to remove all extra attributes, we can specify the ``overwrite`` parameter and not supply any extra attributes.

::

	$ curl -X POST -v "http://localhost:8182/repos/repo1/index/update?treeRefSpec=Points&overwrite=true" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>

Update the max bounds of the Points index
******************************************

::

	$ curl -X POST -v "http://localhost:8182/repos/repo1/index/update?treeRefSpec=Points&bounds=-60,-45,60,45" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-60,60,-45,45]</bounds>
        </index>
        <indexedTreeId>b3340540d2098ec33b7edab1b38d3ffc18f8e162</indexedTreeId>
    </response>


Index rebuild
-------------

Rebuilds the index trees for the full history of a feature type.  This is generally only used when an index has been created or updated without the ``indexHistory`` paramater.  This command provides a way to do that operation if the need arises after the index has been created.

::

   POST /repos/<repo>/index/rebuild[.xml|.json]?treeRefSpec=<treeRefSpec>[&geometryAttributeName=<attributeName>]


Parameters
^^^^^^^^^^

**treeRefSpec:**
Mandatory. Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

**geometryAttributeName:**
Optional. The name of the attribute that is used on the existing index. Defaults to the primary geometry attribute on the feature type.

Examples
^^^^^^^^

Rebuild the index trees of an index
************************************

::

	$ curl -X POST -v "http://localhost:8182/repos/repo1/index/rebuild?treeRefSpec=Points" | xmllint --format -
	< HTTP/1.1 201 Created
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <treesRebuilt>4</treesRebuilt>
    </response>


Index drop
----------

Removes an index from the repository.

::

   DELETE /repos/<repo>/index/drop[.xml|.json]?treeRefSpec=<treeRefSpec>[&geometryAttributeName=<attributeName>]


Parameters
^^^^^^^^^^

**treeRefSpec:**
Mandatory. Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.
   
**geometryAttributeName:**
Optional. Defaults to the primary geometry attribute on the feature type.  The name of the attribute that is used on the existing index.

Examples
^^^^^^^^

Drop an index:
**************

::

	$ curl -X DELETE -v "http://localhost:8182/repos/repo1/index/drop?treeRefSpec=Points" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <dropped>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </dropped>
    </response>


Index list
------------

Lists the indexes that have been built for a repository.

::

   GET /repos/<repo>/index/list[.xml|.json][?treeName=<treeName>]


Parameters
^^^^^^^^^^

**treeName:**
Optional. Defines the tree name of a feature tree in the repository.  Only indexes on that feature tree will be listed.


Examples
^^^^^^^^

List all indexes in the repository
***********************************

::

	$ curl -v "http://localhost:8182/repos/repo1/index/list" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </index>
        <index>
            <treeName>Lines</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </index>
    </response>

List all indexes on the Points layer
*************************************

::

	$ curl -v "http://localhost:8182/repos/repo1/index/list?treeName=Points" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
    <response>
        <success>true</success>
        <index>
            <treeName>Points</treeName>
            <attributeName>the_geom</attributeName>
            <indexType>QUADTREE</indexType>
            <bounds>Env[-180,180,-90,90]</bounds>
        </index>
    </response>
