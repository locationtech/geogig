
.. _geogig-geopkg-import:

geogig-geopkg-import documentation
##################################



SYNOPSIS
********
geogig geopkg import [options] [--all|-t <table>]  [--dest <path>] [--add] [--alter]


DESCRIPTION
***********

This command imports one or more tables from a GeoPackage database into the GeoGig working tree.  Either ``-t`` or ``--all`` must be set for the import process to commence.  To see a list of available tables, use ``geogig geopkg list``.

OPTIONS
*******    

-d, --dest <path>				The path to import to. Only allowed when importing a single table. If not specified, it uses the table name

--fid-attrib <attrib_name>		Uses the specified attribute as the feature id of each feature to import. If not used, the feature id will be determined programmatically.

--add							Adds the imported feature to the corresponding tree without removing previous features in case the tree already exists

--alter							Same as the ``--add`` switch, but if the feature type of the imported features is different to that of the destination tree, the default feature type is changed and all previous features are modified to use that feature type

--force-featuretype 			Use the feature type of the features to import and do not try to adapt them to the default feature type of the destination tree

-t, --table     				The table to import.
				
--all           				Import all tables.

-D, --database                  GeoPackage database to use.  Default: database.gpkg

-U, --user                      User name to use when accessing the database.  Default: user



SEE ALSO
********

:ref:`geogig-geopkg-list`

BUGS
****

Discussion is still open.

