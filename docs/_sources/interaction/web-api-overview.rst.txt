Web API: Overview
#################

This will walk you through GeoGig's web API and all of its currently supported functionality. This doc also assumes that you already know what any given command does, this is strictly how to use these commands.

If you don't already have the GeoGig source from GitHub and a GeoGig repository set up do that first. Next, to get the web API up and running after you have built the latest GeoGig, ``cd`` into your repository and run ``geogig serve``. This will set up a jetty server for your repository and give you access to the web API. Now you just need to open up a web browser and go to ``localhost:8182/repos/<repo name>/log`` to make sure the server starts up. After you have gotten the web server up and running, you can test any of the commands listed here.

Some commands have parameters that are required for that command to work.  These parameters will be described as "Mandatory".

.. note:: All web API command response are formatted for xml by default, however you can get a JSON response by adding ``output_format=JSON`` to the URL parameters.

.. note:: All web API commands have a variable at the top of the response indicating success or failure, so you can still have a 200 status on the request and have a failure. This can happen when the command runs into an internal error.

Parameters
==========

Parameters can be supplied to the various commands through URL encoding, JSON, or XML.

- **URL Encoded**

::

  curl -X POST "http://localhost:8182/repos/<repo name>/config?name=user.name&value=John%20Doe"
  
- **JSON**

::

  curl -X POST -H "Content-Type: application/json" -d '{"name":"user.name","value":"John Doe"}' "http://localhost:8182/repos/<repo name>/config"
  
- **XML**

::

  curl -X POST -H "Content-Type: application/xml" -d "<params><name>user.name</name><value>John Doe</value></params>" "http://localhost:8182/repos/<repo name>/config"
