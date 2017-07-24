
.. _geogig-geopkg-pull:

geogig-geopkg-pull documentation
################################



SYNOPSIS
********
geogig geopkg pull [options]


DESCRIPTION
***********

This command imports one or more tables from a GeoPackage database that have been exported with the ``interchange`` parameter.  This works by replaying all of the changes made to the GeoPackage into a new commit based on the commit that the data was exported from.  That commit is then merged into the current ``HEAD``.

OPTIONS
*******    

-m, --message       Commit message to use for the imported changes.

-t, --table         Feature table to import.  Required if tables are from multiple commits.

-D, --database      GeoPackage database to use.  Default: database.gpkg

-U, --user          User name to use when accessing the database.  Default: user


SEE ALSO
********

:ref:`geogig-geopkg-export`
:ref:`geogig-geopkg-import`

BUGS
****

Discussion is still open.

