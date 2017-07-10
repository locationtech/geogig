Working with OSM data in GeoGig
################################

GeoGig includes a set of specific commands to work with OpenStreetMap data. This allows to set up a repository with OSM data and go through a complete workflow of editing and updating it, and eventually preparing the necessary files to contribute changes back to the central OSM planet. This tutorial describes one of such workflows.

The basic OSM-GeoGig workflow follows the scheme summarized below:

- Import a set of OSM data from a PBF or XML, add and commit to the node & way trees.
- Map that data to a set of GeoGig trees using a mapping specification file
- Export those mapped trees to a database or other datastore
- Modify the data in the external database with various tools
- Import the modified data back into the mapped trees using a GeoGig import
- Unmap the changes back to the canonical node & way trees
- Create an OSM Changeset based on those changes
- Push that changeset to the global OSM API Servers



Setting up the OSM base.
-------------------------

The first thing to do is to import OSM data into a GeoGig repository, so we can later work with it.

Create a folder for your repository, open a terminal in it and create the repo typing

::

	$ geogig init

The easiest way of adding data to your repository is using a file in one of the OSM formats, whether an XML file or a compressed pbf file. Both of them are supported by GeoGig. This will not give you the full history of the repository, but just a single snapshot corresponding to a given time. This is what most users need to perform OSM work.

In the workshop dataset you will find a file named andorra.pbf with an extract of the OSM planet. Import it using the following command

::

	$ geogig osm import andorra.pbf

The aove command assumes that the file to import is in the repository folder. Adjust the path accordingly if you have it somewhere else.

You will see something like this:

::

	Importing into GeoGig repo...
	87.120
	87.484 entities processed in 7,498 s

	Building trees for [node, way]

	Trees built in 1,931 s
	0%
	Processed entities: 87.484.
	 Nodes: 83.881.
	 Ways: 3.494

Two new trees should have been created with your trees and nodes, and you can check that by running:

::

	$ geogig ls
	Root tree/
    	node/
    	way/

The imported features are in the working tree. To create a new snapshot in the repository, you have to add and commit it.

::

	$ geogig add
	Counting unstaged elements...87377
	Staging changes...
	100%
	87375 features and 2 trees staged for commit
	0 features and 0 trees not staged for commit

	$ geogig commit -m "First import of OSM data"
	100%
	[fb89273215cd0f2a92a22e0120a1bd1a19620e51] First import of OSM data
	Committed, counting objects...87375 features added, 0 changed, 0 deleted.

Mapping the OSM data in a repository
-------------------------------------

You can describe an entity to see how they are stored in the repository. For instance, let's describe a way.

::

	$ geogig show way/24664831

	ID:  7430790dde70a7f70c5ab7fc1f992d6a24afe8ab
	FEATURE TYPE ID:  6deead5b94e28c813525d3371b26948bbf13c9ef

	ATTRIBUTES
	----------
	visible: true
	version: 4
	timestamp: 1352568988000
	tags: highway:unclassified
	changeset: 13823696
	user: KartoGrapHiti:57645
	nodes: 268092812;268091938;268091940;268091942;268091944;268091946;268091948;268091950;2006749901;2006749897;2006749903;2006749899;2006749895;2006749893;2006749891;2006749889
	way: LINESTRING (1.4914396 42.565715000000004, 1.4914782000000002 42.565614200000006, 1.4916327 42.5654625, 1.4919503 42.565228600000005, 1.4922250000000001 42.564982, 1.4924653 42.5647608, 1.4926455 42.5645838, 1.4929857000000002 42.5644574, 1.4931016000000001 42.564402400000006, 1.493273 42.564354900000005, 1.4936003 42.564430300000005, 1.493854 42.5643614, 1.4941412 42.5642499, 1.4942882000000002 42.5641138, 1.494936 42.563841700000005, 1.4951742000000001 42.5636925)


As you can see, all tags are stored together under a single attribute named ``tags``. The structure of the attributes associated to an OSM way is fixed and always the same. You can, however, map your OSM data, creating a new tree in the repository with a different set of attributes derived from the tags. Mapping can also be used to filter the data, so only certain ways or nodes are stored in the new tree.

In the workshop dataset you will find a mapping file named ``mapping.json``. Open it with a text editor. It should look like this:

::

	{"rules":[
	  {
	    "name":"aerialways",
	    "filter":{
	      "aerialway":[]
	    },
	    "fields":{
	      "aerialway":{"name":"aerialway", "type":"STRING"},
	      "name":{"name":"name", "type":"STRING"},
	      "geom":{"name":"geom", "type":"LINESTRING"}
	    }
	  }
	]}

You can apply it to the repository by running

::

	geogig osm map mapping.json

Once applied, your tree should look like this:

::

	$ geogig ls	
	Root tree/
	    node/
	    aerialways/
	    way/


Describing a single feature in the ``aerialways`` tree will show you the structure of the associated attributes, as defined in the mapping file.

::

	$ geogig show aerialways/52163704

	ID:  4fe2996182dfcad8e440546f2a61d12291123e31
	FEATURE TYPE ID:  0270c5fd961871135664733378fc6267881f53de

	ATTRIBUTES
	----------
	id: 52163704
	aerialway: chair_lift
	name: TSD4 Portella
	geom: LINESTRING (1.6132674 42.5535841, 1.6280712000000002 42.5526567)
	nodes: 664559723;664559794

If you now want to do some editing of the aerial ways, it would be more practical to work on the recently created tree that contains them than directly with the tree containing all the ways and having the default schema.

Editing the OSM data
--------------------

Editing is done outside the repository, so layer have to exported to a suitale format. Run the following command to export the ``aerialways`` folder to a shapefile

::

	$ shp export aerialways aerialways.shp
	Exporting aerialways...
	100%
	aerialways exported successfully to aerialways.shp

You can now open the resulting shapefile in a software such as QGIS and edit it.

.. image: aerialways.png

In the attributes table you will see that some features do not have a value in the ``name`` field. This is because the name tag was not found when mapping them. Locate the feature with the id 143323580 and enter a name in the name field. Save the changes to the shapefile.

Importing the shapefile back into the GeoGig repository will update it with the changes that you have done. To reimport it, use the following command:

::

	$ geogig shp import aerialways.shp


The changes can be inspected by using the diff command

::

	$ geogig diff HEAD WORK_HEAD



To get more detail about the change in the feture that we have modified use the following command:

::

	$ geogig diff HEAD WORK_HEAD --path aerialways/143323580


OSM operations work on the canonical representations of OSM features in the repo, which are always stored in the ``ways`` and ``nodes`` trees. Our changes are only stored in the ``aerialways`` tree, so we have to bring them to the canonical trees. This is done by unmapping the ``aerialways`` tree. To unmap it, we can use the following command

::

	$ geogig osm unmap 

Now changes have also been applied to the ``ways`` tree, as it can be seen by running the following command:

::
	
	$ geogig diff HEAD WORK_HEAD --path ways

To create a new snapshot in the repository history, add and commit the above changes.

::

	$ geogig add
	$ geogig commit -m "Added missing airway name"

Contributing changes back to the OSM planet
--------------------------------------------

GeoGig can generate a file with the differences between two snapshots in a format suitable for being contributed to the OSM planet. In our case, we can export the change that we have just introduced and later apply in in the OSM planet. This is done using the following command.

::

	$ geogig osm create-changeset HEAD~1 HEAD

However, the resulting changeset will not have valid changeset ID assigned, since those IDs are managed by the OSM API. As a result of that, you will not be able to apply that chageset on the OSM planet. Changesets generated by the ``create-changeset`` command will be valid IDs only if they represent a change that is already part of the OSM planet, but not a change that has been manually added in the GeoGig repository.

To fix that, you first have to create an ID using the OSM API, sending a PUT request to ``http://api.openstreetmap.org/api/0.6/changeset/create``. You can use curl on a terminal to do that. Check ``http://wiki.openstreetmap.org/wiki/API_v0.6```for more information about this usage of the OSM API.

The PUT request will return the ID of the changeset, and you can provided it to the ``create-changeset`` GeoGig command, as follows.

::

	$ geogig geogig osm create-changeset HEAD~1 HEAD --id <your_changeset_id>

The resulting diff file can now be pushed to the OSM planet. GeoGig, however, does not implement that functionality, so you should curl or some other similar tool to do it.

