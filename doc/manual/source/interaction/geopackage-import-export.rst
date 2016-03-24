GeoPackage import and export Web-API 
====================================

`GeoPackage <http://www.geopackage.org/>`_ is an open, standards-based, platform-independent, portable, self-describing, compact format for transferring geospatial information.


The GeoPackage import-export GeoGig Web-API allows for downloading a repository snapshot or a subset of it as a GeoPackage file, and for uploading a GeoPackage file and import its vector layers into the GeoGig repository.


GeoPackage export
-----------------

Exports a repository snapshot or a subset of it as a GeoPackage file.

A repository snaphot is the data (layers and their features) addressable from a certain commit.

::

   GET /<repo>/export[.xml|.json]?format=gpkg[&root=<refspec>][&path=<layerName>[,<layerName>]+][&bbox=<boundingBox>][&interchange=<true|false>]


Parameters
^^^^^^^^^^

**format:**
Mandatory. Must be ``gpkg`` (case insensitive). Defines the output format of the export operation.
   
**root:**
Optional. Defaults to ``HEAD``. The ref spec that resolves to the root tree from
where to export data (e.g. ``HEAD``, ``WORK_HEAD``, a branch name like ``master``,
``refs/heads/master``, a commit id, possibly abbreviated like ``50e295dd``, a relative
refspec like ``HEAD~2``, ``master^4``, etc)

**path:**
Optional. A comma separated list of layer names to export. Defaults to exporting all layers in the resolved root tree.

**bbox:**
Optional. A bounding box filter. If present, only features matching the
indicated bounding box filter will be exported. Applies to all exported layers. Format is
``minx,miny,maxx,maxy,<SRS>``, where ``SRS`` is the EPSG code for the coordinates (e.g.
``EPSG:4326``, ``EPSG:26986``, etc), always using "longitude first" axis order.

**interchange:**
Optional. Boolean indicating whether to enable GeoGig's interchange format extension for GeoPackage.
The "GeoGig GeoPackage Extention" is an extension to the geopackage format that (transparently) records all changes made 
to vector layers in hidden audit tables that can then be used to replay those changes on top of the repository.

Examples   
^^^^^^^^

Missing format argument:
************************

::

	$ curl -v "http://localhost:8182/export" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<response>
	  <success>false</success>
	  <error>output format not provided</error>
	</response>

Bad refspec:
************

Note since the command is run asynchronously, you'll notice the error once the async task status is polled:

::

	$ curl -v "http://localhost:8182/export?format=GPKG&root=nonExistingBranch" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<task>
	  <id>2</id>
	  <status>RUNNING</status>
	  <description>Export to Geopackage database</description>
	  <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/2.xml" type="application/xml"/>
	</task>

	$ curl -v "http://localhost:8182/tasks/1.xml" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<task>
	  <id>2</id>
	  <status>FAILED</status>
	  <description>Export to Geopackage database</description>
	  <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/2.xml" type="application/xml"/>
	  <error>
	    <message>RefSpec doesn't resolve to a tree: 'nonExistingBranch'</message>
	    <stackTrace><![CDATA[java.lang.IllegalArgumentException: RefSpec doesn't resolve to a tree: 'nonExistingBranch'
		at com.google.common.base.Preconditions.checkArgument(Preconditions.java:145)
		at org.locationtech.geogig.geotools.plumbing.DataStoreExportOp.resolveExportLayerRefSpecs(DataStoreExportOp.java:178)
	]]></stackTrace>
	  </error>
	</task>

Export all layers in the current HEAD:
**************************************

::

	$ curl -v "http://localhost:8182/export?format=GPKG" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<task>
	  <id>3</id>
	  <status>RUNNING</status>
	  <description>Export to Geopackage database</description>
	  <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/3.xml" type="application/xml"/>
	</task>

Poll task status until it's FINISHED:

::

	$ curl -v "http://localhost:8182/tasks/3.xml" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<task>
	  <id>3</id>
	  <status>RUNNING</status>
	  <description>Export to Geopackage database</description>
	  <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/3.xml" type="application/xml"/>
	  <progress>
	    <task>Exporting from d77a84bee98ae2972953308775c0b0f318f0df6d:ne_10m_roads to ne_10m_roads... </task>
	    <amount>99.29507</amount>
	  </progress>
	</task>
    

	$ curl -v "http://localhost:8182/tasks/3.xml" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<task>
	  <id>3</id>
	  <status>FINISHED</status>
	  <description>Export to Geopackage database</description>
	  <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/3.xml" type="application/xml"/>
	  <result>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/tasks/3/download" type="application/octet-stream;type=geopackage"/>
	  </result>
	</task>

Note once the task is finished, the `result` element contains the download link for the generated geopackage file.

Finally, download the GeoPackage:

::

	$ curl -v "http://localhost:8182/tasks/3/download" > all_layers_current_head.gpkg
	< HTTP/1.1 200 OK
	< Content-Type: application/octet-stream;type=geopackage
	< Content-Length: 80854016
	100 77.1M  100 77.1M    0     0   842M      0 --:--:-- --:--:-- --:--:--  847M
	* Connection #0 to host localhost left intact

That `curl` command downloaded the geopackage to the `all_layers_current_head.gpkg` file.
Now you can open it, for example, in QGGIS:

::

	$ qgis all_layers_current_head.gpkg


.. figure:: ../img/qgis_exported_geopackage.png


