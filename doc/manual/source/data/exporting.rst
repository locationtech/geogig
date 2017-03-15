.. _exporting:

Exporting from a GeoGig repository
===================================

Data can also be exported from the GeoGig repository, allowing full synchronization with external applications that cannot use the native format of the GeoGig working tree.
This also allows to export changes that have been incorporated into the working tree from an external repository, making those changes available to applications, and making them aware of edits done remotely.

GeoGig supports the same formats for exporting that it does for importing. That is, GeoJSON, shapefiles, PostGIS/SQL Server/Oracle databases and Geopackage. To export from a GeoGig repository, the following syntax is used

::

	$ geogig <shp|geojson|pg|oracle|sl> export <path_to_export> <destination> [-overwrite] [--defaulttype] [--featuretype]


The ``destination`` option is the filepath in the case of exporting to a shapefile, or the table name in case of exporting to a database. In both cases, the element designated by the ``destination`` parameter should not exist. If it exists, GeoGig will not perform the export operation. If you want GeoGig to overwrite, you must explicitly tell it to do so, by using the ``--overwrite`` option.

The ``path_to_export`` refers by default to the working tree. Thus, the path ``roads`` refers to the full reference ``WORK_HEAD:roads``. Data can be exported from a given commit or a different reference, by using a full reference instead of just a path. For instance, the following line will export the ``roads`` path from the current HEAD of the repository, to a shapefile.

::

	$ geogig shp export HEAD:roads exported.shp

When exporting to a database, the same options used to configure the database connection that are available for the import operation are also available for exporting.

Notice that, as it was mentioned before, features with different feature types can coexist under the same path. When exporting, this will cause GeoGig to show an error message and to not complete the export operation, since this is not allowed to happen in a shapefile or a PostGIS table. Only paths with all features sharing the same feature type of the parent tree can be safely imported using the corresponding export commands.

If you want to export a path that contains features with different feature types, you have two options to select which features should be exported

- Use the ``--defaulttype`` switch to tell GeoGig that you want only those features with the feature type of the selected path.

- Use the  ``--featuretype`` option followed by the ID of the feature type to export. GeoGig will only export those features that have the feature type defined by the specified ID

Remember that you can find the ID of the feature type of a given tree by using the ``command`` to describe that tree. To find the ID of the feature type of a given feature, also use the ``cat`` command, passing the path to the feature instead.

Another alternative for exporting when there are mixed feature types under a path is to use the ``--alter`` switch. This is similar to the ``--alter`` switch of the import operation, and it changes the feature attributes to make them match the output feature type.

The output feature type is selected with the ``--featuretype`` option. If no feature type is specified using this option, the default feature type from the path to export is used. Using the ``--defaulttype`` option has the same effect.

Here is a quick summary with examples of all the option that can be used when a path to export contain mixed feature types

- Exporting only features that have the default feature type

	::

		$ geogig shp export Points Points.shp --defaulttype


- Exporting only feature that have a given feature type

	::

		$ geogig shp export Points Points.shp --featuretype 0a3ebd6a

- Exporting all features in the selected path, using the default feature type and modifying features when they have a different feature type

	::

		$ geogig shp export Points Points.shp --alter

- Exporting all features in the selected path, using a given feature type and modifying features when they have a different feature type

	::

		$ geogig shp export Points Points.shp --alter --featuretype 0a3ebd6a
