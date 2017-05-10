
.. _geogig-ls:

geogig-ls documentation
#############################


SYNOPSIS
********

geogig ls  <[refspec]:[path]> [--verbose] [--abbrev] [-t] [-d] [-r]


DESCRIPTION
***********

This command lists the content of a given tree.

OPTIONS
*******    

-t 				Show tree entries even when going to recurse them. Has no effect if -r was not passed. -d implies -t.

-d 				Show only the named tree entry itself, not its children.
    
-r 				Recurse into sub-trees.

-v, --verbose 	Verbose output, include metadata, object id, and object type along with object path.
    
-a, --abbrev 	Instead of showing the full 40-byte hexadecimal object lines, show only a partial prefix. Non default number of digits can be specified with --abbrev <n>.

SEE ALSO
********



BUGS
****

Discussion is still open.

