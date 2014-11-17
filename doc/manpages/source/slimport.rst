
.. _geogig-sl-import:

geogig-sl-import documentation
##############################



SYNOPSIS
********
geogig sl import [options] [--all|-t <table>]


DESCRIPTION
***********

This command imports one or more tables from a SpatiaLite database into the GeoGig working tree.  Either ``-t`` or ``--all`` must be set for the import process to commence.  To see a list of available tables, use ``geogig sl list``.

OPTIONS
*******    

-t, --table     The table to import.

--all           Import all tables.

--add							Adds the imported feature to the corresponding tree without removing previous features in case the tree already exists

--alter							Same as the ``--add`` switch, but if the feature type of the imported features is different to that of the destination tree, the default feature type is changed and all previous features are modified to use that feature type

--force--featuretype			Use the feature type of the features to import and do not try to adapt them to the default feature type of the destination tree

--database      The database to connect to.  Default: database

--user          User name.  Default: user

SEE ALSO
********

:ref:`geogig-sl-list`

BUGS
****

Discussion is still open.

