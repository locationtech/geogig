
.. _geogig-sqlserver-import:

geogig-sqlserver-import documentation
######################################



SYNOPSIS
********
geogig sqlserver import [options] [--all|-t <table>]


DESCRIPTION
***********

This command imports one or more tables from a SQLServer database into the GeoGig working tree.  Either ``-t`` or ``--all`` must be set for the import process to commence.  To see a list of available tables, use ``geogig sqlserver list``.

OPTIONS
*******    

-t, --table     				The table to import.

--all           				Import all tables.

--add							Adds the imported feature to the corresponding tree without removing previous features in case the tree already exists

--alter							Same as the ``--add`` switch, but if the feature type of the imported features is different to that of the destination tree, the default feature type is changed and all previous features are modified to use that feature type

--force--featuretype			Use the feature type of the features to import and do not try to adapt them to the default feature type of the destination tree

--host			 				Machine name or IP address to connect to. Default: localhost

--port 							Port number to connect to.  Default: 1433    

--intsec   						Use integrated security. ignores user / password (Windows only)  Default: false

--native-paging 				Use native paging for queries. This improves performance for some types of queries. Default: true")

--geometry_metadata_table 		The optional table containing geometry metadata (geometry type and srid). Can be expressed as 'schema.name' or just 'name'

--native-serialization 			Use native SQL Server serialization (false), or WKB serialization (true).  Default: false

--database 						The database to connect to.  Default: database

--schema 						The database schema to access.  Default: public        

--user 							User name.  Default: sqlserver    

--password 						Password.  Default: <no password>


SEE ALSO
********

:ref:`geogig-sqlserver-list`

BUGS
****

Discussion is still open.

