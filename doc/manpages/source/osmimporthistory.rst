
.. _geogig-osm-import-history:

geogig-osmimporthistory documentation
#####################################



SYNOPSIS
********
geogig import-history [options] <OSM api URL. eg: http://api.openstreetmap.org/api/0.6>


DESCRIPTION
***********

Downloads OSM data from OSM planet, replicating its history in the local repo, adding a new commit for each original changeset.


OPTIONS
*******
--dev       Use the development test api endpoint at ``http://api06.dev.openstreetmap.org/api/0.6``. Notice that this does not contain the real OSM history, and it is just used for testing purposes.
     
--from <id>		If specified, instead of starting from the first changeset, start from this one
       
--keep-files, -k 	If specified, downloaded changeset files are kept in the --saveto folder
       
--numthreads, -t <n>    Number of threads to use to fetch changesets. Must be between 1 and 6

--Resume      Resume import from last imported changeset on the current branch.
       
--saveto <dir>     Directory where to save the changesets. Defaults to $TMP/changesets.osm

--to <id> 		If specified, instead of downloading changesets until the last one, stop at this one
       

SEE ALSO
********

:ref:`geogig-osm-download`

:ref:`geogig-osm-import`

BUGS
****

Discussion is still open.

