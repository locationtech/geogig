
.. _geogig-pg-export:

geogig-pg-export documentation
###############################



SYNOPSIS
********
geogig pg export [options] [<commit-ish>:]<path> <table>


DESCRIPTION
***********

This command exports features from a feature type into a PostGIS database.

The feature type can be defined using the [<commit-ish>:]<path> notation, so a feature type from a different tree can be exported.

If no origin tree is specified and just a feature type name is used, the working tree will be used, so ``table`` is equivalent to ``WORK_TREE:table``.

If the table already exists, it will not be overwritten, unless the ``-o`` modifier is used.

OPTIONS
*******    

-o, --overwrite     Overwrite the output table if it already exists.

--host              Machine name or IP address to connect to. Default: localhost

--port              Port number to connect to.  Default: 5432

--schema            The database schema to access.  Default: public

--database          The database to connect to.  Default: database

--user              User name.  Default: postgres

--password          Password.  Default: <no password>

SEE ALSO
********

:ref:`geogig-pg-list`

BUGS
****

Discussion is still open.

