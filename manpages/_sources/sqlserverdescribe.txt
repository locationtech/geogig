
.. _geogig-sqlserver-describe:

geogig-sqlserver-describe documentation
########################################



SYNOPSIS
********
geogig sqlserver describe [options] -t <table>


DESCRIPTION
***********

This command describes a single table in a SQLServer database.  It will print out each property name along with its type. To see a list of available tables, use ``geogig sqlserver list``.

OPTIONS
*******    

-t, --table     				The table to describe.

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

