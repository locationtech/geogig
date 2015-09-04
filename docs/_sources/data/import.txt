.. _import:

Import data
===========

The first step to add data to a GeoGig repository, so it can be versioned, is to import it from a file or database. Data stored in a file or a database cannot be versioned by GeoGig, so it has to be converted to the native GeoGig format, and imported into the working tree. From there, we can start working with it and follow the typical workflow of editing, updating and sharing whatever changes we make to that data.


.. note:: As we already mentioned, the working tree of GeoGig is not the filesystem. That means that, to make data available to GeoGig so it can be added to the index and commited , you cannot just copy the file to be versioned into a repository folder or create it there. It's necessary to import your data into the working tree, so it gets stored in the native format used by GeoGig. This adds an extra step if compared to VCS's oriented to work with source code files, but allows GeoGig to have a more powerful handling of spatial data.

Importing data into the working tree of a GeoGig repository is done using GeoGig commands which read a given data source and add all or part of its content to the repository. Data can also be exported from the repository into common formats that can be then used in other applications such as a desktop GIS. That provides the necessary tools to work against a GeoGig repository and make it independent of the tools used to actually edit the data.

Currently, data can be imported/exported from/to GeoJSON, shapefiles, and PostGIS, SQLServer, Oracle Spatial and SpatiaLite databases. Depending on the type of data source, a different command should be used. For these supported data sources, the general syntax is as follows:

::

	$ geogig <shp|geojson|pg|sl|sqlserver|oracle> <import|export> <specific_parameters>


In the case	of importing data, the following syntax is used

::

	$ geogig <shp|geojson|pg|sl|sqlserver|oracle> import <source>[-d <destination_path>] [--no-overwrite] <specific_parameters>

Data can be imported in a given path defined using the ``-d`` option. If it is not used, the destination path will be defined automatically based on the data source. For instance, in the case of using a shapefile, the destination path is defined using the name of the shapefile.

The ``source`` argument is the filepath of the file to import, in case of using shapefiles, or the name of the table to import in case of importing from a PostGIS database.

The following command line will import all the features in a shapefile named ``parks.shp`` into the ``parks`` tree in the working tree of the GeoGig repository.

::

	$ geogig shp import /home/shapefiles/parks.shp

To import into a tree named "myparks", the following command should be used:

::

	$ geogig shp import /home/shapefiles/parks.shp -d myparks


A tree in the GeoGig working tree is the equivalent of a folder in the filesystem. However, if you move into the ``.geogig`` folder where the working tree (and the rest of the GeoGig repository data) is stored, you will not see the folders that you might have created by importing data. The structure of the repository is not visible in the filesystem, and the only way to explore it is to use additional GeoGig commands that we will see soon.

When importing from a database, additional parameters can be supplied to configure the database connection. In the case of importing from a PostGIS database, the following options are available.


* ``--host``: Machine name or IP address to connect to. Default: localhost
* ``--port``: Port number to connect to.  Default: 5432
* ``--schema``: The database schema to access.  Default: public
* ``--database``: The database to connect to.  Default: database
* ``--user``: User name.  Default: postgres
* ``--password``: Password.  Default: <no password>

I using a SpatiaLite database only the following parameters are used.

* ``--database``: The database to connect to.  Default: database.sqlite
* ``--user``: User name.  Default: user


GeoGig also supports SQLServer data. In that case the following parameters have to be configured


* ``--host``: Machine name or IP address to connect to. Default: localhost
* ``--port``: Port number to connect to.  Default: 1433
* ``--intsec``: Use integrated security. ignores user / password (Windows only)  Default: false
* ``--native-paging``: Use native paging for queries. This improves performance for some types of queries. Default: true")
* ``--geometry_metadata_table``: The optional table containing geometry metadata (geometry type and srid). Can be expressed as 'schema.name' or just 'name'
* ``--native-serialization``: Use native SQL Server serialization (false), or WKB serialization (true).  Default: false
* ``--database``: The database to connect to.  Default: database
* ``--schema``: The database schema to access.  Default: public
* ``--user``: User name.  Default: sqlserver
* ``--password``: Password.  Default: <no password>

This connection parameters are also used when exporting to a database. We will see the exporting functionality later in this same manual.

When importing from a database, all tables can be imported with one single command. To do so, do not enter the name of a table as data source, but use the ``--all`` option instead, as in the following example:

::

	$ geogig pg import --all

If a destination path is supplied and the ``--all`` option used, all tables will be imported into the same path.

A listing of all available tables for a given database connection can be obtained using the ``list`` command, as shown below.

::

	$ geogig pg list



Since our repository had just been initialized, it was completely empty, and there is no way that the data we have imported can conflict with data that already existed in the repository. However, importing new data (or a different version of that same data) later can cause conflicts and it might require a different approach than just using the plain import command as we have done now. We will deal with these cases later, but for now it is enough to know that our repository contains some spatial data in its working tree.

Once the data is imported in the working tree, GeoGig can use it. The original file or database from which you imported it is still something that GeoGig cannot manage, so it is of no use for GeoGig. There is no link between your repository and the original file or data base, so you can literally remove it from your system and it would have no effect at all on GeoGig, which will work exclusively with the copy of the data that is now in the repository working tree.

To see that the data is actually in the working tree, you can use the ``status`` command. This command gives you information about the data that you have in the working tree and the index, comparing between them and also with the repository database. This way, you can see which data has been modified but not added to the repository, or which data has been added but has not yet been versioned.

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

There were 3 features in the imported shapefile, and now they are in the working tree. They are still unversioned, and they have to be added to the staging area before they can be committed from there into the repository database, creating a new snapshot of the repository data.

