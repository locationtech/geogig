
.. _geogig-sl-export:

geogig-sl-export documentation
##############################



SYNOPSIS
********
geogig sl export [options] <feature_type> <table>


DESCRIPTION
***********

This command exports features from a feature type into a SpatiaLite database.

The feature type can be defined using the <refspec>:<table> notation, so a feature type from a different tree can be exported.

If no origin tree is specified and just a feature type name is used, the working tree will be used, so ``table`` is equivalent to ``WORK_TREE:table``.

If the table already exists, it will not be overwritten, unless the ``-o`` modifier is used.

OPTIONS
******* 

-o 	        	Overwrite the output table if it already exists.   

--database      The database to connect to.  Default: database

--user          User name.  Default: user

SEE ALSO
********

:ref:`geogig-sl-list`

BUGS
****

Discussion is still open.

