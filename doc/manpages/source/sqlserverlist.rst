
.. _geogig-sqlserver-list:

geogig-sqlserver-list documentation
####################################



SYNOPSIS
********
geogig sqlserver list [options]


DESCRIPTION
***********

This command simply lists of the tables available in the specified SQLServer database.

OPTIONS
*******    

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


BUGS
****

Discussion is still open.

