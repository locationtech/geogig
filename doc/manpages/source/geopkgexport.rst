
.. _geogig-geopkg-export:

geogig-geopkg-export documentation
##################################



SYNOPSIS
********
geogig geopkg export [options] [--interchange] [<commit-ish>:]<path> <table>


DESCRIPTION
***********

This command exports features from a feature type into a GeoPackage database.

The feature type can be defined using the [<commit-ish>:]<path> notation, so a feature type from a different tree can be exported.

If no origin tree is specified and just a path is used, the working tree will be used, so ``table`` is equivalent to ``WORK_TREE:table``.

If the table already exists, it will not be overwritten, unless the ``-o`` modifier is used.

If the ``interchange`` flag is used, additional audit tables will be added to the GeoPackage to keep track of future changes.  This can be used in order to utilize the ``geogig geopkg pull`` command in the future, which provides a more robust way of importing data.

OPTIONS
*******    

--alter             Export all features if several types are found, altering them to adapt to the output feature type

--defaulttype       Export only features with the tree default feature type if several types are found

--featuretype       Export only features with the specified feature type if several types are found

-o, --overwrite     Overwrite the output table if it already exists.

-i, --interchange   Export as geogig mobile interchange format.  This adds additional tables to the GeoPackage to keep track of any future modifications made.  These changes can then be pulled back into GeoGig via the ``geogig geogpkg pull`` command.

-D, --database      GeoPackage database to use.  Default: database.gpkg

-U, --user          User name to use when accessing the database.  Default: user

SEE ALSO
********

:ref:`geogig-geopkg-list`
:ref:`geogig-geopkg-pull`

BUGS
****

Discussion is still open.

