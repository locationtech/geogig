
.. _geogig-osm-export-sl:

geogig-osm-export-ls documentation
###################################



SYNOPSIS
********

geogig osm export-sl [options] --mapping <mapping_file>


DESCRIPTION
***********

Exports OSM data in the current working tree to a Spatialite database. Data is not exported in its canonical representation, but mapped instead before exporting.


If the table already exists, it will not be overwritten, unless the ``-o`` modifier is used.

OPTIONS
*******

--mapping <mapping_file> 	The file that contains the mapping file to use. The mapping must contain a single rule. Additional rules will be ignored. The rule defines the name of the output table.

--database      			The database to connect to.  Default: database

--user          			User name.  Default: user


SEE ALSO
********

:ref:`geogig-osm-export-pg`

:ref:`geogig-osm-export-shp`


BUGS
****

Discussion is still open.

