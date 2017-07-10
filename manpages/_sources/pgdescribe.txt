
.. _geogig-pg-describe:

geogig-pg-describe documentation
################################



SYNOPSIS
********
geogig pg describe [options] -t <table>


DESCRIPTION
***********

This command describes a single table in a PostGIS database.  It will print out each property name along with its type. To see a list of available tables, use ``geogig pg list``.

OPTIONS
*******    

-t, --table     The table to describe.

--host          Machine name or IP address to connect to. Default: localhost

--port          Port number to connect to.  Default: 5432

--schema        The database schema to access.  Default: public

--database      The database to connect to.  Default: database

--user          User name.  Default: postgres

--password      Password.  Default: <no password>

SEE ALSO
********

:ref:`geogig-pg-list`

BUGS
****

Discussion is still open.

