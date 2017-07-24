
.. _geogig-geopkg-describe:

geogig-geopkg-describe documentation
####################################



SYNOPSIS
********
geogig geopkg describe [options] -t <table>


DESCRIPTION
***********

This command describes a single table in a GeoPackage database.  It will print out each property name along with its type. To see a list of available tables, use ``geogig geopkg list``.

OPTIONS
*******    

-t, --table     The table to describe.

-D, --database  GeoPackage database to use.  Default: database.gpkg

-U, --user      User name to use when accessing the database.  Default: user

SEE ALSO
********

:ref:`geogig-geopkg-list`

BUGS
****

Discussion is still open.

