Serving Multiple Repositories and Managing Them
===============================================

The GeoGig Web-API allows you to serve up multiple repositories from a single endpoint via the ``geogig serve`` command.  The Web-Api documentation goes over using this command to serve a single repository, but this section will discuss using the command to serve and manage multiple repositories.


Serving Multiple Repositories
-----------------------------

When ``geogig serve`` is run from within a geogig repository directory, it will serve only that repository.  However, if it is run from a directory containing zero or more GeoGig repositories while using the ``--multirepo`` parameter, all repositories within that directory will be served through the endpoint.

Given the following directory structure:

::

	/repos/
	  repo1/
	    .geogig/
	  repo2/
	    .geogig/
	  repo3/
	    .geogig/


To serve repo1, repo2, and repo3 under a single endpoint, perform the following commands:

::

	$ cd /repos
	$ geogig serve --multirepo

This will enable all three repositories to be accessed through the ``http://localhost:8182/`` endpoint.

The rest of this document will use this repository configuration for demonstration purposes.


Repository Management
---------------------

Listing Repositories
********************

In order to see which repositories are currently being served, you can issue a ``GET`` request against the ``repos/`` endpoint.  The response will also provide a link to each individual repository's endpoint.

::

	$ curl -v "http://localhost:8182/repos/" | xmllint --format - --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<repos>
	  <repo>
	    <name>repo2</name>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/repos/repo2.xml" type="application/xml"/>
	  </repo>
	  <repo>
	    <name>repo3</name>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/repos/repo3.xml" type="application/xml"/>
	  </repo>
	  <repo>
	    <name>repo1</name>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/repos/repo1.xml" type="application/xml"/>
	  </repo>
	</repos>

Creating New Repositories
*************************

You can create a new repository by issuing a ``PUT`` request to the ``init`` endpoint of the desired repository.  The name preceding ``init`` will be used as the repository name.

::

	$ curl -X PUT -v "http://localhost:8182/repos/repo4/init" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<response>
	  <success>true</success>
	  <repo>
	    <name>repo4</name>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/repos/repo4.xml" type="application/xml"/>
	  </repo>
	</response>
	
Behind the scenes, a new repository will be created and configured with ``repo4`` as the name.  The actual directory that is created for the repository will be randomized to avoid naming conflicts with other repositories.  This is important because repository management allows you to rename repositories.

Renaming Repositories
*********************

If you wish to change the name of a repository after it has been created, you can do so by issuing a ``POST`` request to the ``rename`` endpoint of the repository with the new name.  Because the repository's name is used in web-api calls, renaming a repository will cause the endpoint for that repository to change to the new name.

::

	$ curl -X POST -v "http://localhost:8182/repos/repo4/rename?name=betterName" | xmllint --format -
	< HTTP/1.1 301 Moved Permanently
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<response>
	  <success>true</success>
	  <repo>
	    <name>betterName</name>
	    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8182/repos/betterName.xml" type="application/xml"/>
	  </repo>
	</response>

The repository can now be accessed via the ``http://localhost:8182/repos/betterName/`` endpoint.

Deleting Repositories
*********************

If a repository is no longer needed, you may wish to delete it.  You may do this by performing a two-step delete operation on the repository.  It is set up this way in order to prevent any accidental deletions.  The first step to delete a repository is to issue a ``GET`` request to the ``delete`` endpoint of the repository.  This will return a token that can be used to delete the repository in step two.

::

	$ curl -v "http://localhost:8182/repos/betterName/delete" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<response>
	  <success>true</success>
      <token>db431217519a4c72</token>
	</response>
	
Now that we have the delete token, we can issue a ``DELETE`` request to the repository endpoint.

::

	$ curl -X DELTE -v "http://localhost:8182/repos/betterName?token=db431217519a4c72" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<deleted>betterName</deleted>
	
The repository has now been deleted.

Asynchronous Requests
---------------------

Just like when serving a single repository, asynchronous web-api tasks can be polled via the ``tasks`` endpoint.  All repositories share the same tasks endpoint.

::

	$ curl -v "http://localhost:8182/tasks" | xmllint --format -
	< HTTP/1.1 200 OK
	< Content-Type: application/xml
	<?xml version="1.0" encoding="UTF-8"?>
	<tasks/>
	
In this example, no asynchronous tasks have been run, but if they had been, they would be listed here with their corresponding task id.
