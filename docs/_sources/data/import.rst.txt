.. _import:

Import data
===========

The first step to add data to a GeoGig repository, so it can be versioned, is to import it from a file or database. Data stored in a file or a database cannot be versioned by GeoGig, so it has to be converted to the native GeoGig format and imported into the working tree. From there, we can start working with it and follow the typical workflow of editing, updating, and sharing whatever changes we make to that data.


.. note:: As we already mentioned, the working tree of GeoGig is not the filesystem. That means that, to make data available to GeoGig so it can be added to the index and committed, you cannot just copy the file to be versioned into a repository folder or create it there. It's necessary to import your data into the working tree, so it gets stored in the native format used by GeoGig. This adds an extra step if compared to VCSs oriented to work with source code files, but allows GeoGig to have a more powerful handling of spatial data.

Importing data into the working tree of a GeoGig repository is done using GeoGig commands which read a given data source and add all or part of its content to the repository. Data can also be exported from the repository into common formats that can be then used in other applications such as a desktop GIS. This built-in import/export process provides the necessary workflow for a GeoGig repository and makes it independent of the tools used to actually edit the data.

Currently, data can be imported/exported from/to GeoJSON, shapefiles, PostGIS, Oracle Spatial and Geopackage databases. Depending on the type of data source, a different command should be used. For the supported data sources, the general syntax is as follows.

::

	$ geogig <shp|geojson|geopkg|sl|oracle> <import|export> <specific_parameters>


In the case	of importing data, the following syntax is used.

::

	$ geogig <shp|geojson|pg|sl|oracle> import <source>[-d <destination_path>] [--no-overwrite] <specific_parameters>

Data can be imported into a given path defined using the ``-d`` option. If it is not used, the destination path will be defined automatically based on the data source. For instance, in the case of using a shapefile, the destination path is defined using the name of the shapefile.

The ``source`` argument is the filepath of the file to import, in the case of shapefiles. When importing from a PostGIS database, use the name of the table.

The following command line will import all the features in a shapefile named ``parks.shp`` into the ``parks`` tree in the working tree of the GeoGig repository.

::

	$ geogig shp import /home/shapefiles/parks.shp

To import into a tree named "myparks", the following command would be used.

::

	$ geogig shp import /home/shapefiles/parks.shp -d myparks


A tree in the GeoGig working tree is the equivalent of a folder in the filesystem. However, if you move into the ``.geogig`` folder where the working tree (and the rest of the GeoGig repository data) is stored, you will not see the folders that you might have created by importing data. The structure of the repository is not visible in the filesystem. The only way to explore it is to use additional GeoGig commands that are covered in this documentation.

When importing from a database, additional parameters can be supplied to configure the database connection. In the case of importing from a PostGIS database, the following options are available.

* ``--host``: Machine name or IP address to connect to. Default: localhost
* ``--port``: Port number to connect to.  Default: 5432
* ``--schema``: The database schema to access.  Default: public
* ``--database``: The database to connect to.  Default: database
* ``--user``: User name.  Default: postgres
* ``--password``: Password.  Default: <no password>

If using a Geopackage database, only the following parameters are used.

* ``--database``: The database to connect to.  Default: database.sqlite
* ``--user``: User name.  Default: user


When importing from a database, all tables can be imported with a single command. To do so, do not specify the name of a table as the data source, use the ``--all`` option instead, as in the following example.

::

	$ geogig pg import --all

If a destination path is supplied and the ``--all`` option used, all tables will be imported into the same path.

A listing of all available tables for a given database connection can be obtained using the ``list`` command.

::

	$ geogig pg list



Since our repository had just been initialized, it was completely empty, and there is no way that the data we have imported can conflict with data that already existed in the repository. However, importing new data (or a different version of that same data) later can cause conflicts and it might require a different approach than the use of the plain import command as we have done now. We will deal with these cases elsewhere in the documentation. For general use, it is enough to know that our repository contains some spatial data in its working tree.

Once the data is imported in the working tree, GeoGig can use it. The original file or database from which you imported the data remains something that GeoGig cannot manage, so it is of no use to GeoGig. There is no link between your repository and the original file or database, so you can remove that original item from your system and it would have no effect at all on GeoGig, which will work exclusively with the copy of the data that is now in the repository working tree.

To see that the data is actually in the working tree, you can use the ``status`` command. This command gives you information about the data that you have in the working tree and the index, comparing between them and also with the repository database. This way, you can see which data has been modified but not added to the repository and which data has been added but has not yet been versioned.

::

	$ geogig status
	# On branch master
	# Changes not staged for commit:
	#   (use "geogig add <path/to/fid>..." to update what will be committed
	#   (use "geogig checkout -- <path/to/fid>..." to discard changes in working directory
	#
	#      added  parks/2
	#      added  parks/3
	#      added  parks/1
	# 3 total.

There were 3 features in the imported shapefile, and now they are in the working tree. They are still unversioned and they have to be added to the staging area before they can be committed from there into the repository database, creating a new snapshot of the repository data.
