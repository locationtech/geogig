
.. _geogig-osm-export-shp:

geogig-osm-export-shp documentation
####################################



SYNOPSIS
********
geogig osm export-shp <shapefile>  --mapping <mapping_file> [--overwrite]


DESCRIPTION
***********

Exports OSM data in the current working tree to a shapefile. Data is not exported in its canonical representation, but mapped instead before exporting.

OPTIONS
*******

--mapping <mapping_file> 	The file that contains the mapping file to use. The mapping must contain a single rule. Additional rules will be ignored.
    
--overwrite, -o 			Overwrites the specified output file if it already exists.

SEE ALSO
********

:ref:`geogig-osm-export-pg`

:ref:`geogig-osm-export-sl`

BUGS
****

Discussion is still open.

