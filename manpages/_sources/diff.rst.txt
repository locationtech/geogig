
.. _geogig-diff:

geogig-diff documentation
###########################



SYNOPSIS
********
geogig diff [<commit> [<commit>]] [--cached] [--path <path>[ <path>]...] 

geogig diff [<commit> [<commit>]] [--cached] --nogeom  [--path <path>[ <path>]...] 

geogig diff [<commit> [<commit>]] [--cached] --summary [--path <path>[ <path>]...] 

geogig diff [<commit> [<commit>]] [--cached] --count [--path <path>[ <path>]...] 

geogig diff [<commit> [<commit>]] [--cached] --bounds [--crs EPSG:<number>] [--path <path>[ <path>]...] 


DESCRIPTION
***********

Shows changes between two commits, a commit and the working tree or a commit and the index.

If no commits are specified, it will compare the working tree and index. If only a single commit is passed, it will compare the working tree and the specified commit.

Comparison can be restricted to a given path, by using ``--path <path>``.

By default, a full detailed report of changes is shown. By using the ``--nogeom`` and ``--summary`` switches, a less detailed output can be obtained.

OPTIONS
*******

--cached	Use index instead of working tree for comparison. If no commit is specified, it compares index and HEAD commit

--nogeom	Do not show detailed changes in coordinates, but just a summary of altered points in each modified geometry

--summary	List only summary of changes. It will only show which features have changed, but not give details about the changes in each of them.

--count		Print the total number of trees and features affected by the diff instead.

--bounds	Print the spatial bounds of the changes between the two trees being compared instead of the actual diff. At least otherwise specified through the --crs parameter, the output is in EPSG:4326 lon/lat coordinate reference system. The output is four lines specifying the changed bounds at the left side of the comparison, at the right side, the merged bounds, and the output CRS. For example: 

::

	left:  -127.203597,58.217228,-126.687105,58.293822
	right: <empty>
	both:  -127.203597,58.217228,-126.687105,58.293822
	CRS:   EPSG:4326


--crs 		Specifies a different output coordinate reference system for the bounding boxes computed by the --bounds parameter. For example: EPSG:3857, etc.  

SEE ALSO
********

:ref:`geogig-format-patch`

BUGS
****

Discussion is still open.

