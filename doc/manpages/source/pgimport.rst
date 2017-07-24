
.. _geogig-pg-import:

geogig-pg-import documentation
##############################



SYNOPSIS
********
geogig pg import [connection_options] [--all|-t <table>]  [--dest <path>] [--add] [--alter]


DESCRIPTION
***********

This command imports one or more tables from a PostGIS database into the GeoGig working tree.  Either ``-t`` or ``--all`` must be set for the import process to commence.  To see a list of available tables, use ``geogig pg list``.

OPTIONS
*******    

-d, --dest <path>				The path to import to. Only allowed when importing a single table. If not specified, it uses the table name

--fid-attrib <attrib_name>		Uses the specified attribute as the feature id of each feature to import. If not used, the feature id will be determined programmatically.

--add							Adds the imported feature to the corresponding tree without removing previous features in case the tree already exists

--alter							Same as the ``--add`` switch, but if the feature type of the imported features is different to that of the destination tree, the default feature type is changed and all previous features are modified to use that feature type

--force-featuretype			    Use the feature type of the features to import and do not try to adapt them to the default feature type of the destination tree

-t, --table     				The table to import.
				
--all           				Import all tables.
				
--host          				Machine name or IP address to connect to. Default: localhost
				
--port          				Port number to connect to.  Default: 5432
				
--schema        				The database schema to access.  Default: public
				
--database      				The database to connect to.  Default: database
				
--user          				User name.  Default: postgres
				
--password      				Password.  Default: <no password>



SEE ALSO
********

:ref:`geogig-pg-list`

BUGS
****

Discussion is still open.

