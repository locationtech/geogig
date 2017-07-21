
.. _geogig-shp-export:

geogig-shp-export documentation
################################



SYNOPSIS
********
geogig shp export [<commit-ish>:]<path> <shapefile> [-o]


DESCRIPTION
***********

This command exports features from a feature type into a shapefile.

The feature type can be defined using the [<commit-ish>:]<path> notation, so a feature type from a different tree can be exported.

If no origin tree is specified and just a feature type name is used, the working tree will be used, so ``mypath`` is equivalent to ``WORK_TREE:mypath``.

If the output file already exists, it will not be overwritten, unless the ``-o`` modifier is used.

OPTIONS
*******

-o, --overwrite 		Overwrite the output file in case it already exists.

BUGS
****

Discussion is still open.

