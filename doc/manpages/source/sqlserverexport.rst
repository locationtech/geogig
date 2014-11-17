
.. _geogig-sqlserver-export:

geogig-sqlserver-export documentation
######################################



SYNOPSIS
********
geogig sqlserver export [options] <feature_type> <table>


DESCRIPTION
***********

This command exports features from a feature type into a SQLServer database.

The feature type can be defined using the <refspec>:<table> notation, so a feature type from a different tree can be exported.

If no origin tree is specified and just a feature type name is used, the working tree will be used, so ``table`` is equivalent to ``WORK_TREE:table``.

If the table already exists, it will not be overwritten, unless the ``-o`` modifier is used.

OPTIONS
*******    

-o 						        Overwrite the output table if it already exists.

--host			 				Machine name or IP address to connect to. Default: localhost

--port 							Port number to connect to.  Default: 1433    

--intsec   						Use integrated security. ignores user / password (Windows only)  Default: false

--native-paging 				Use native paging for queries. This improves performance for some types of queries. Default: true")

--geometry_metadata_table 		The optional table containing geometry metadata (geometry type and srid). Can be expressed as 'schema.name' or just 'name'

--native-serialization 			Use native SQL Server serialization (false), or WKB serialization (true).  Default: false

--database 						The database to connect to.  Default: database

--schema 						The database schema to access.  Default: public        

--user 							User name.  Default: sqlserver    

--password 						Password.  Default: <no password>

SEE ALSO
********

:ref:`geogig-sqlserver-list`

BUGS
****

Discussion is still open.

