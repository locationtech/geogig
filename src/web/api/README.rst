*******************
GeoGig Web API
*******************

Proof-of-concept straw-man web API for a single repository.

It does:

* handle `status`, `log`, `commit`, `ls-tree`
* allow XML or JSON responses, including JSONP callbacks
* allow specification of the repository via the command line

It does **not**:

* allow concurrent use with command-line (bdb lock)
* look RESTful
* attempt to be complete or even consistent
* have tests yet

Implementation
==============

Currently using Restlet running on Jetty. This layer is relatively small and the bulk of the code
is in actual command implementation and response formatting.

Discussion
----------

The command implementation looks so similar to the CLI commands (with some duplication), it may
make sense to evaluate breaking a CLI command into 3 pieces: CLI argument parsing/binding, actual
execution, and output. This way the web api commands could provide specific implementations of
argument parsing and binding and output. The duplicated code (so far) is not large so this might
just add overhead for little gain.


Running
=======

First, build the whole project via the `parent` module.

To run the jetty server via maven, in the web module directory, run:

  mvn -o exec:java -Dexec.mainClass=org.locationtech.geogig.web.Main -Dexec.args=PATH_TO_YOUR_REPO

In Servlet Container
--------------------

Build the geogig.war like this:

  mvn package

The output should tell you where the war is. Something like:

  Building war: <project-home>/geogig/src/web/target/geogig-web-0.2-SNAPSHOT.war

Deploy the war to your container and ensure one of the two points to the full
path to your repository:

* servlet parameter `repository`
* java system property `org.locationtech.geogig.web.repository`

URLS
====

All endpoints respond to GET or POST operations:

|  /status
|  /log
|  /commit
|  /ls-tree
|  /updateref
|  /diff
|  /repo/manifest
|  /repo/objects/{id}
|  /repo/sendobject

Note: Unless `commit` is run with the `all` option or changes are staged using the command line,
nothing will happen. In other words, one cannot specify paths at the moment.

URL Parameters
--------------

Parameters may be provided as URL query items or as form-encoded POST body.

`status` and `log` accept `offset` and `limit` parameters to support paging.

`commit` requires a `message` parameter and allows an optional `all` parameter to stage everything first.

`log` accepts one or more `path` parameters.

`ls-tree` accepts an optional `ref` parameter (? tree-ish, not clear) and has
optional parameters for `showTree`, `onlyTree`, `recursive`, `verbose`

`updateref` requires a `name` and `newValue` parameter that specify the name of the ref to set and the value to set it to.

`diff` requires an `oldRefSpec` and `newRefSpec` parameter and will find the differences between the two commits.  It also takes an optional `pathFilter` parameter to only find differences that match the filter.

`repo/manifest` takes an optional `remotes` parameter that will also list remote branches if set to true.

An optional `callback` parameter in JSON requests will result in a JSONP response.

An optional `output_format` parameter can specify the response type (see Content-Type below)

Content-Type
------------

The default `Accept` value is assumed to be `application/json`. `text/xml` can also be specified.

Additionally, the format can be specified by providing the `output_format` parameter
as either `xml` or `json`.

Examples
========

Note: piping output into `json_pp` or `xmllint` will help.

JSON Status:

  curl -XPOST localhost:8182/status

Invalid Request:

  curl -XPOST localhost:8182/invalid

JSONP Status:

  curl -XPOST localhost:8182/status?callback=handle

XML Status (piped to xmllint for formatting):

  curl -XPOST -H 'Accept: text/xml' localhost:8182/status | xmllint --format - 

The Future
==========

It would be trivial to expand the URL routing to one or more directory roots containing one
or more geogig repositories. For example:

  http://host/{directory}/{repo}/{command} 

To consider:

* authentication/authorization
* async processing if needed?

