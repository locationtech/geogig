Using GeoGig with OpenStreetMap data
=====================================

GeoGig can be used to version OSM data, and also to contribute changes back once the work is done. GeoGig aims to provide all the tools needed for the versioning part of a normal OSM editing workflow, adding some of its powerful tools to give additional possibilites.

This section describes the GeoGig commands that interact with OSM and their usage.

Importing OSM data
--------------------

Just like you can import data form a shapefile or a PostGIS database into a GeoGig repository, you can also import OSM data from a file in one of the supported OSm formats (OSM XML or pbf). The ``osm import`` is the command to use for that.

The ``osm import`` command has the following syntax:

::

	geogig osm import <path_to_file>

Both OSM XML and pbf formats are supported.

Data in the specified file is imported into GeoGig and put into two trees: ``way`` and ``node``, with default feature types in both cases. The feature type keeps the data in a way that makes it possible to later recreate the OSM objects and export back to an OSM XML or pbf file, as we will see.

You can see the definition of those feature types by using the ``show`` command on both trees.

::

	$ geogig show WORK_HEAD:way
	TREE ID:  fb04b79726d7a969393308a3e40fdd47a6c7be4b
	SIZE:  5254
	NUMBER Of SUBTREES:  0
	DEFAULT FEATURE TYPE ID:  03585dd9f1ccd1555372782e6f18bf44ec5d9693

	DEFAULT FEATURE TYPE ATTRIBUTES
	--------------------------------
	changeset: <LONG>
	nodes: <STRING>
	tags: <STRING>
	timestamp: <LONG>
	user: <STRING>
	version: <INTEGER>
	visible: <BOOLEAN>
	way: <LINESTRING>


	$geogig show WORK_HEAD:node
	TREE ID:  98d0b69bab10307921b939aa8ee975e6eb669d17
	SIZE:  153503
	NUMBER Of SUBTREES:  0
	DEFAULT FEATURE TYPE ID:  f63c0dd9d76623e2af985553c94d5219a9c0e2b7

	DEFAULT FEATURE TYPE ATTRIBUTES
	--------------------------------
	changeset: <LONG>
	location: <POINT>
	tags: <STRING>
	timestamp: <LONG>
	user: <STRING>
	version: <INTEGER>
	visible: <BOOLEAN>

Here is an example of a way imported into the ``way`` tree, as described by the ```show`` command:

::

	$ geogig show WORK_HEAD:way/31347480

	ID:  d81271b7346586c95166c43feb6e91ffe7adb9d5

	ATTRIBUTES
	----------
	changeset: 14220478
	nodes: 269237860;2059114068;269237861;278905850;269237862;269237863;278904103;1300224351;269237865;345117527
	tags: highway:residential|lit:yes|name:Gielgenstrasse
	timestamp: 1355097350000
	user: adjuva:92274
	version: 5
	visible: true
	way: LINESTRING (7.1960069 50.7399033, 7.195868 50.7399081, 7.1950788 50.739912, 7.1949262 50.7399053, 7.1942463 50.7398686, 7.1935778 50.7398262, 7.1931011 50.7398018, 7.1929987 50.7398009, 7.1925978, 50.7397889, 7.1924199 50.7397781)

As in the case of importing from a shapefile of database, the tree where data is imported is deleted before importing, so the imported data replaces the previous one. In this case, both ``way`` and ``node`` trees are deleted, even if the imported data does not contain entities of both types. To keep existing data, use the ``--add`` switch. Notice that, although existing data will not be deleted, it will be overwritten if the imported data contains entities with the same OSM id.

Downloading data from an OSM Server
------------------------------------

A different way of putting OSM data into a GeoGig repository is by connecting to a OSM endpoint that supports the OSM Overpass API. In this case, the ``osm download`` command has to be used instead of ``osm import``

The syntax of the commands is as follows:

::

	geogig osm download [<server_URL>] [--filter <filter_file>] [--bbox <S> <W> <N> <E>]

You can specify the server from which you want to get your OSM data, just entering its URL after the ``osm download`` command. By default, if no URL is provided, the ``download`` command uses ``http://overpass-api.de/api/interpreter`` as endpoint. 

To avoid downloading the full OSM planet, a filter can be used. You should write your filter in a separate text file using the Overpass Query Language. Check the `language documentation <http://wiki.openstreetmap.org/wiki/Overpass_API/Language_Guide>`_ to know more about it.

A few considerations to take into account:

- Version information is needed to parse the downloaded data. Use the verbose mode (``out meta;``) to get the version information added.

- If your filter downloads ways, it should also download the corresponding nodes. For instance, this filter will add no data to your GeoGig repo:

	::

		way
			["name"="Gielgenstraße"]
			(50.7,7.1,50.8,7.25);
		out meta;

	The following one, however, will work:

	::

		(
		  way
		    ["name"="Gielgenstraße"]
		    (50.7,7.1,50.8,7.25);
		  >;
		);
		out meta;


If the filter you want to set is just a bounding box filter, you can use the ``--bbox`` option as a practical alternative, as in the next example:

::

	$ geogig osm download --bbox 50.7 7.1 50.8 7.25

Values after the ``--bbox`` option represent South, West, North and East limits, respectively.


Unlike the case of importing from a file, which works similar to the case of importing from a shapefile or database, downloading from OSM has to be performed with a clean index and working tree, and the imported data is not just imported into the working tree, but also staged and commited. This is done to ensure that the commit actually correspond to an OSM changeset, with no further modification, so it can be later identified and used as a reference when performing other tasks agains the OSM planet, such as updating.

Updating OSM data
-----------------

If you have downloaded OSM data into your GeoGig repository using the ``download`` command, you can easily update it to get the new changes that might have been added in the central OSM planet. To do so, just run the ``osm download`` command with the ``--update`` switch and without specifying any filter file.

::

	$ geogig osm download --update

As in the case of importing, you can select a URL different to the default one, just entering it after the command.

::

	$ geogig osm download http://overpass.osm.rambler.ru/ --update

The filter that you used for the latest import will be used. In case you want to get the most recent OSM data with a different filter, you should run the ``download`` command instead as explained before, which will replace the current OSM data in the geogig repository.

The ``download`` command with the ``--update`` switch is similar to the ``pull`` command in a normal repository. It will get the latest version of the OSM data and put it in new temporary branch. That branch starts at the commit where you made your last update. From that point GeoGig will try to merge that branch with your current branch, doing it the usual way. If you have edited your OSM and your changes are not compatible with the changes introduced in the latest snapshot that you you have just downloaded, conflicts will be signaled, and you should resolve them.

As in the case of the ``pull`` command, you can tell GeoGig to perform a rebase instead of a merge, by using the ``--rebase`` switch.

Exporting to OSM formats
-------------------------

The content of a GeoGig repository can be exported in OSM XML format, much in the same way as it works for other formats such as shapefiles. The OSM export command has the following format:

::

	geogig osm export <file> [commitish]

If the file has the ``pbf`` extension, the created file will be a pbf file. Otherwise, it will be an OSM XML file.

The area to export can be restricted by using the ``--b`` option, which works just as it does in the case of the ``download`` command. Use it to define a bounding box, and only those elements intersecting the selected area will be exported.

Data exported is taken from the "way" and "node" trees, and assumed to use the corresponding default feature types. In other words, it assumes OSM data in your repository has been imported either by using the ``osm import`` or ``osm download`` commands. Data in other trees in the repository will not be imported, even if it originated from OSM data and even uses the same feature type, since there is no way for GeoGig to know about it. You will notice that, for this reason, there is no path option in the syntax of the command, since the paths from which to export data are not configurable, and GeoGig uses the default OSM paths.

By default, the data at HEAD is exported. You can export from a different snapshot by entering the commit reference after the export file path.

For instance:

::

	$ geogig export myexportedfile.pbf HEAD~3	

OSM formats should be used as a part of a normal OSM workflow, both for importing and exporting. If you plan to edit your data and create new versions in your GeoGig repository that you can later contribute back to the OSM planet, either the OSM XML format or the pbf format have to be used. Other formats will not guarantee that the relation between nodes and ways is kept, and the result of a workflow might result in a new snapshot in the GeoGig repository that cannot be later exported and contributed back to the OSM planet.

The geometry of ways is not used to export, and it is assumed to match the set of nodes that are kept in the ``nodes`` attribute. That's the reason why the OSM formats should be used instead of other formats when exporting OSM data. Using other formats can lead to unconsistent relations between nodes and ways.

In short, you should use ``osm export`` to export your OSM data, and not commands such as ``pg export`` or ``shp export``.

To be able to use a shapefile or a PostGIS database for working with OSM data, GeoGig provides additional export commands and data mapping functionalities that will be explained later in this chapter. For now, just remember that the usual export commands are not a good idea in case you want to edit and reimport your OSM data. 




Exporting differences as changesets
------------------------------------

The differences between two commits in a repository can be exported as an OSM changeset that can be used to contribute those changes to the OSM planet. To export differences as changesets, the following command has to be used:

::

	geogig osm create-changeset [commit [commit]] -f <changesets_file>

The syntax is similar to the ``diff`` command, but the output will be saved to the specified file instead of printed on the console. The two commits are optional, and allow to select the snapshots to compare, with the same meaning as the equivalent parameters in the ``diff`` command.

To export the differences between the working tree and the current HEAD, this would be the command line to use:

::

	$ geogig osm create-changeset -f changeset.xml

Only the ``node`` and ``way`` trees are compared to find the differences between the specified commits. Changes in other trees will be ignored, and no changeset entries will be created based on them.

The changeset command accepts an addtional parameter ``--id``. In case the OSM trees contain modified or new feature which do not have a changeset id assigned (because they haven't been already uploaded to the OSM planet), they will have a negative changeset id to indicate that. If you pass an Id (which you have to retrieve manually from the OSM planet), GeoGig will use it to replace those negative Ids. This way, you will get a changeset file that is already prepared to be uploaded and contributed to OSM.

Data mapping
-------------

Apart from importing the data in the default "node" and "way" trees, OSM data can also be imported in any given tree, and a custom schema can be used for the corresponding features. This is done using a data mapping. A data mapping is a set of rules, each of them defines the data to map into a given tree. Each mapping rule contains the following elements.

- A destination tree.
- A set of characteristics of the entities to import onto that tree, which are used as a filter over the whole OSM dataset in the Geogig repository
- A set of attributes for the feature type to use. Value of those attributes will be taken from the tags of the same name, if present.

Mappings are defined in a mapping file, using JSON syntax, as in the following example:

::

	{"rules":[
	  {
	    "name":"onewaystreets",
	    "filter":{
	      "oneway":["yes"]
	    },
	    "exclude":{
	      "highway":["construction"]
	    },
	    "fields":{
	      "highway":{"name":"highway", "type":"STRING"},
	      "geom":{"name":"geom", "type":"LINESTRING"}
	    }
	  }
	]}

A mapping description is an array of mapping rules, each of them with the following fields:
 
- ``name`` defines the name of the mapping, and is used as the name of the destination tree.
- ``filter`` is a set of tags and values, which define the entities to use for the tree. All entities which have any of the specified values for any of the given tags will be used. And empty filter will cause all entities to be used.
- ``exclude`` is a set of tags and values used to exclude certain elements. Those elements that contain any of the specified values for the specified tags, will not be mapped, even if they pass the filter set by the ``filter`` element. This field can be ignored and not added to the JSON definition, so no exclusion filter is added. Examples in this document do not use this field.
- ``fields`` is a set of tags and destination column names and types.
- ``defaultFields`` is a list of default fields to be added from the original OSM feature, without transformation. It is an optional entry and can be ommitted.

The following mapping will copy all ways to a feature type that only contains the geometry of the way:

::

	{"rules":[
	  {
      "name":"all_ways",
	    "filter":{},
	    "fields":{
	      "geom":{"name":"geom","type":"LINESTRING"}
	    }
	  }
	]}

To get all the entities that have a given tag, independently of the tag value, use an empty list for the accepted values. For instance, to get all the nodes with the tag ``power`` (can be ``power=tower``, ``power=pole``, etc.), use the following mapping:

::

 	{"rules":[
 	  {
      "name":"power",
 	    "filter":{
 	      "power":[]
 	    },
 	    "fields":{
 	      "geom":{"name":"geom", "type":"POINT"},
 	      "power":{"name":"powertype", "type":"STRING"}
 	    }
 	  }
 	]}

Any way/node that matches any of the supplied filters will pass through the rule. For instance, to get a subset of buildings and air terminals (a special case of building) use:

You can make tags mandatory by adding them to the exclude section, with a ``null`` value. That will exclude all elements that do not have the tag, instead of the ones that have it or have a specific value for it.

::
	{"rules":[
	  {
		"name":"namedhighways",
		"filter":{
			"highway":[]
		},
		"exclude":{
			"name":null
		},
		"fields":{
			"geom":{"name":"geom","type":"POINT"}			
		}
	  }
	]}

That mapping will match all highways, except those that do not have a name. Only the geometry is used and no tags are added to the mapped feature.

::

 	{"rules":[
    {
      "name":"buildings",
      "filter":{
        "building":["residential","house","garage","detached","terrace","apartments"],
        "aeroway":["terminal"]
      },
      "fields":{
        "geom":{"name":"way","type":"POLYGON"},
        "building":{"name":"building", "type":"STRING"},
        "aeroway":{"name":"aeroway", "type":"STRING"}
      }
    }
  }

The format of the ``fields`` entries is a little tricky: the initial key is the tag to read from, and the value is a hash giving the field name and field type to write to. So: ``"my_tag":{"name":"my_field", "type":"FIELD_TYPE"}``

Usually, ``my_tag`` and ``my_field`` will be identical, so the name of the tag is used as the field name. However, you can use a different name for the field, which will act as an alias for the tag.

Valid types for the ``FIELD_TYPE`` are

* ``BOOLEAN``
* ``INTEGER``
* ``FLOAT``
* ``DOUBLE``
* ``LONG``
* ``SHORT``
* ``POINT``
* ``LINESTRING``
* ``POLYGON``
* ``STRING``
* ``DATE``

Each tree has only one geometry type, so the geometry type you choose to write out will act as an implicit filter: if you use a field of type ``POINT``, only nodes will be read; if you use a field of type ``LINESTRING`` or ``POLYGON``, only ways will be read. When you use a field of type ``POLYGON`` all ways will be read and automatically closed. In case you want to be more restrictive about how to create a polygon, you can use the ``filter`` entry to define the criteria for a way to be suitable for creating a polygon, such as, for instance, requiring the ``area=yes`` or ``building=yes`` tag/value pair.

An additional ``geom```tag can be used with two possible values: ``open`` and ``closed``. Instead of looking for an OSM tag named ``geom``, this will appply the filter to the geometry itself. If the value used is ``open`` then only open lines will be used to create the destination geometry (usually a line in this case). If ``closed`` is used, only those ways with the end point identical to the start point will be transformed, and the remaining ones ignored.

Notice that, although only one of the above values can be used, it has to be put in a list, as it happens with other tag values in the ``filter`` entry. Here is an example that shows how to restrict the ways used to create polygons to just those that have a closed linestring.

::

 	{"rules":[
    {
      "name":"buildings",
      "filter":{
      	"geom":["closed"],
        "building":["residential","house","garage","detached","terrace","apartments"],
        "aeroway":["terminal"]
      },
      "fields":{
        "geom":{"name":"way","type":"POLYGON"},
        "building":{"name":"building", "type":"STRING"},
        "aeroway":{"name":"aeroway", "type":"STRING"}
      }
    }
  }

Apart from the fields that you add to the feature type in your mapping definition, GeoGig will always add an ``id`` field with the OSM Id of the entity. This is used to track the Id and allow for unmapping, as we will later see. In the case of ways, another field is added, ``nodes``, which contains the Id's of nodes that belong to the way. You should avoid using ``id`` or ``nodes`` as names of your fields, as that might cause problems.

You can also add fields from the original OSM feature without doing any transformation. To do so, add the names of the fields to add in a list in the ``defaultFields`` entry.

The following fields are available. Notice that the names are case-sensitive an have to be in lower case.

* ``timestamp``
* ``changeset``
* ``tags``
* ``user``
* ``visible``
* ``version``

Here's an example of using the ``defaultFields`` entry in the JSON definition. This mapping will add the fields containing the changeset and timestamp of each feature, copying the corresponding value in the original OSM feature, without any transformation.

 	{"rules":[
    {
      "name":"buildings",
      "filter":{
        "building":["residential","house","garage","detached","terrace","apartments"],
        "aeroway":["terminal"]
      },
      "fields":{
        "geom":{"name":"way","type":"POLYGON"},
        "building":{"name":"building", "type":"STRING"},
        "aeroway":{"name":"aeroway", "type":"STRING"}
      },
      "defaultFields":["timestamp", "changeset"]
    }
  }

.. note:: [Explain this better and in more in detail]

A mapping file can be used in three different cases.

- When importing OSM data using the ``osm import`` or ``osm download`` commands. In both cases, the ``--mapping`` option has to be used, followed by the name of the file where the mapping is found, as in the following example.

::

	$ geogig osm import fiji-latest.osm.pbf --mapping mymapping.txt

Data will be imported in the usual ``way`` and ``node`` trees with the corresponding default feature types, but also in the trees defined by the mapping, and according to the filter and feature types that it defines. 

If you do not want the imported data to be added in *raw* format in the default trees, you can use the ``--no-raw`` switch. 

::

	$ geogig osm import fiji-latest.osm.pbf --mapping mymapping.txt --no-raw

This option is only available for the ``osm import`` command, but not for the ``osm download`` command, since the *raw* data is needed to later be able to perform operations such as update.

Be aware that, when you import using the ``--no-raw`` switch, you will not be able to use OSM operations on the imported data, since GeoGig will not consider it as OSM data. When using a mapping, the mapped data is an additional version of the data that is imported in a different tree to give a more practical alternative to the *raw* one, but that data is not guaranteed to have the necessary information to be able to reconstruct OSM entities. In short, GeoGig will not track data other than the data stored in the ``way`` and ``node`` trees as OSM data, so you should not to use the ``--no-raw`` switch if you plan to do OSM-like work on the imported data.

if ``--mapping`` is used and the ``--no-raw`` switch is not, the working tree and index have to be clean, and after the import and mapping, a commit will be made (just like when you use the ``download`` command, eve without mapping). This is done to allow GeoGig to keep track of mappings, so then the unmmaping operations can provide additional functionality. The comit message is automatically generated, but if you want to define your own message, you can do it using the ``--message`` option

::

	$ geogig osm import fiji-latest.osm.pbf --mapping mymapping.txt -message "import and map Fiji data" 

- With already imported OSM data. If you imported OSM data without a mapping, you can apply it afterwards by using the ``osm map`` command followed by the mapping file, as in the example below.

::

	$ geogig osm map mymapping.txt


Also in this case, as mentioned above, a commit will be created after the mapping, and the working tree and index have to be clean before performing the mapping operation. The ``--message`` option can be used as well to set a given commit message.

When exporting OSM data. OSM data can be exported to OSM formats using the ``osm export`` command, and also to other formats using commands such as ``shp export`` or ``pg export``. In these two last cases, the feature type created in the destination file or database is the same one used it the ``way`` or ``node`` tree. That is, the default one used for storing the *raw* OSM data in GeoGig. Additional commands are available to export a mapped set of features.

- ``osm export-shp``. Export to a shapefile
- ``osm export-pg``. Export to a PostGIS database
- ``osm export-sl``. Export to a Spatialite database.

.. note:: only shp and pg export currently implemented

These commands all have a syntax similar to the equivalent export commands such as ``shp export`` or ``pg export``, but without the ``--alter``, ``--defaulttype`` and ``--featuretype`` options. Instead, the ``--mapping`` option must be used to specify the file that contains the mapping to use. Also, a path cannot be specified, since the operation will always take the OSM data from the default *raw* locations at the ``way`` and ``node`` trees.

Below you can see some examples:

::

	$ geogig osm export-shp ./myexportfile.shp --mapping ./mymappingfile.json

	$ geogig osm export-pg --port 54321 --database geogig --mapping ./mymappingfile.json --user geogig --password geogig


When exporting to a shapefile, the mapping file should contain a single rule. If the mapping contains more than one mapping rule, only the first one will be used. 

In the case of a shapefile, the destination file has to be entered. In the case of a database export, the name of the each rule is used as the name of the corresponding table to create. In both cases, the ``--overwrite`` switch has to be used if the destination file/table already exists.

Since features in a shapefiles must have a geometry, the mapping used when exporting to a shapefile must contain one, and only one, field of type ``POLYGON, LINESTRING`` or ``POINT``. In the case of exporting to a database, the rule can contain no geometry attribute at all. 

In all cases, exporting is done from the working tree.

.. note:: Maybe add an option to select a commitish to export from?

Data unmapping
--------------

Mapped OSM data can also be used to modify the original OSM data that is kept in the default ``node`` and ``way`` trees. This way, you can export your data using a mapping, modifiy that mapped data, reimport it, and then tell GeoGig to reflect those changes back in the original data, which is the one used for all OSM tasks such as generating changesets, remapping to a different feature type, etc.

To unmap the data in a tree in your repository, the ``osm unmap`` command should be used, with the following syntax:

::

	geogig osm unmap <tree_path>


If you add new entities, they will just be added to the corresponding ``way`` or ``node`` trees. In case the entity already existed, the modified version from you mapped data is merged with the information that is stored in the default location and was not mapped. Those tags that are defined for an entity (and, as such, stored in the ``way`` or ``node`` trees) but are not used to create attributes in the mapped feature type, are reused when unmapping. Let's see it with an example.

For instance, imagine that you have an OSM entity with the following tags

::

  amenity:fire_station
  name:Unnamed fire station
  phone:5555986154
    
Let's say that you have run the ``export-pg`` command to export your nodes to a postGIS database, with the following mapping

::

	{"rules":[
	  {
      "name":"firestations",
	    "filter":{
	      "amenity":["fire_station"]
	    },
	    "fields":{
	      "geom":{"name":"geom", "type":"POINT"}, 
	      "name":"{"name":"name", "type":"STRING"}
	    }
	  }
	]}

Basically, you are mapping all fire stations to a new feature type which just contains the station name and its location.

Now, in your exported data, you modified the name of the above firestation from "Unnamed fire station" to "Central fire station". After that, you imported the data to a ``fire_stations`` tree using the ``pg import`` command.

The ``firestations`` tree contains your changes, but the corresponding feature in the ``node`` tree is not updated. You can tell GeoGig to update it, by running the unmap command, as shown below.

::

	$ geogig unmap fire_stations

The corresponding feature will be updated, and will have the following tags.

::

  amenity:fire_station
  name:Central fire station
  phone:5555986154

Although the ``phone`` tag was not present in the mapped data, it will continue to appear here, since it is taken from the previous version of the feature that was stored in the ``node`` tree.

All the work done by the unmap command takes place on the working tree. That is, the mapped path ``firestations`` refers to ``WORK_HEAD:firestations``, and the unmapped data is added/replaced in ``WORK_HEAD:node`` and ``WORK_HEAD:way``.

In the case of ways, the ``nodes`` field will be recomputed based on the geometry. If the geometry has changed and new points have been added to the corresponding line of polygon, new nodes will be added accordingly.

The unmapping operation also considers deleted features, by comparing with the state of your mapped tree just after the last mapping operation (that's the reason why a commit is created after mapping, to be able to locate that snapshot). All features that have been deleted from those that existed at that commit just after the mapping was performed, will be deleted from the canonical trees as well. A deleted way will not cause its corresponding nodes to be deleted, but only the canonical representation of the way itself.

An OSM workflow using GeoGig
-----------------------------

The following is a short exercise demonstrating how GeoGig can be used as part of a workflow involving OSM data.

First, let's initialize the repository.

::

	$ geogig init

For this example, we will be working on a small area define by a bounding box. The first step is to get the data corresponding to that area. We will be using a bounding box filtering, which will retrieve all the data within the area, including both ways and nodes.

Run the following command:

::

	$ geogig osm download --bbox 40 0 40.01 0.01  


Your OSM data should now be in your GeoGig repository, and a new commit should have been made.

::

	$ geogig log
	Commit:  d972aa12d9fdf9ac4192fb81da131e77c3867acf
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (4 minutes ago) 2013-06-03 14:37:21 +0300
	Subject: Updated OSM data

If you want to edit that data and work on it, you can export it using the ``osm export`` command.

::

	$ geogig osm export exported.xml

You can open the ``exported.xml`` file in a software such as JOSM and edit it. Once it is edited, export it back to an OSM file.

To create a new snapshot in the geogig repository with the edited data, just import the new OSM file.

::

	$ geogig osm import editedWithJosm.xml

and then add and commit it

::

	$ geogig add
	$ geogig commit -m "Edited OSM data"
	[...]
	$ geogig log
	Commit: a465736fdabc6d6b5a3289499bba695328a6b43c 	        
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (15 seconds ago) 2013-05-21 12:37:33 +0300
	Subject: Edited OSM data

	Commit:  58b84cee8f4817b96804324e83d10c31174da695
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (3 minutes ago) 2013-05-21 12:34:30 +0300
	Subject: Update OSM to changeset 16215593


Another way of editing your data is to export it using a mapping. Let's see how to do it.

Create a file named ``mapping.json`` in your GeoGig repository folder, with the following content:

::
	
	{"rules":[
	  {
      "name":"power",
	    "filter":{
	      "power":["tower", "pole"]
	    },
	    "fields":{
	      "coord":{"name":"coord", "type":"POINT"},
	      "power":{"name":"power", "type":"STRING"}
	    }
	  }
	]}

Now export the OSM data that you downloaded, using the above mapping. 

::

	$ geogig osm export-shp exported.shp --mapping mapping.json

The resulting database file can be imported into a desktop GIS such as QGIS. Here's how the attributes table of the imported layer would look like:

.. figure:: ../img/qgis_osm.png


Let's edit one of the features in the layer (don't worry, we are not going to commit the changes back to OSM, so we can modify it even if the new data is not real). Take the feature with the Id ``1399057662``, move its corresponding point to a different place and change the value of the ``power`` attribute from ``tower`` to ``pole``.

Save it to the same ``export.shp`` file and then import it back into the GeoGig repository using the following command:

::

	$ geogig shp import export.shp -d power

The imported data is now in the ``power`` tree.

::

	$ geogig show WORK_HEAD:power
	TREE ID:  cd6d05d0fe0c527a78e56ef4ec7439a494a6229c
	SIZE:  130
	NUMBER Of SUBTREES:  0
	DEFAULT FEATURE TYPE ID:  e1833b12c4fc867f10b3558b1b32c33abdd88afa

	DEFAULT FEATURE TYPE ATTRIBUTES
	--------------------------------
	id: <LONG>
	power: <STRING>
	the_geom: <POINT>

The node we have edited is not updated in the ``node`` tree, as you can see by running the following command:

::

	$ geogig show WORK_HEAD:node/1399057662
	ID:  9877ef1ed87f5e9e85a00416e681f3a0238725b9

	ATTRIBUTES
	----------
	changeset: 9020582
	location: POINT (0.0033643 40.0084599)
	tags: power:tower
	timestamp: 1313352916000
	user: Antonio Eugenio Burriel:24070
	version: 1
	visible: true
	


To update the data in the "node" tree, we can run the ``osm unmap`` command:


::

	$ geogig osm unmap power

Now the node should have been updated.

::

	$ geogig show WORK_HEAD:node/1399057662
	ID:  ff6663ccec292fb2c06dcea5ec8b539be9cb50fb

	ATTRIBUTES
	----------
	changeset: Optional.absent()
	location: POINT (0.0033307887896529 40.00889554573451)
	tags: power:pole
	timestamp: 1370271076015
	user: Optional.absent()
	version: Optional.absent()
	visible: true
	

You can now add and commit your changes.

To merge those changes (no matter which one of the above method you have used to edit the OSM data in your GeoGig repository) with the current data in the OSM planet, in case there have been changes, use the ``update`` switch.

::

	$ geogig download --update

If there are conflicts, the operation will be stopped and you should resolve them as usual. If not, the, changes will merged with the changes you just added when importing the xml file. If there are no changes since the last time you fetched data from the OSM server, no commit will be made, and the repository will not be changed by the update operation.

Finally, you can export the new changes that you have introduced, as a changeset, ready to be contributed to the OSM planet. The commits to compare depend on the workflow that you have followed. In the case above, you can get them by comparing the current HEAD with its second parent, which corresponds to the branch that was created with the changes downloaded in the update operation, in case there were changes (otherwise, there would be no merge operation, since it was not necessary).

::
	
	$ geogig create-changeset HEAD^2 HEAD -f changeset.xml

Or you can just compare your current HEAD to what you had after your first import.

::

	$ geogig create-changeset 58b84cee8f4 HEAD -f changeset.xml




