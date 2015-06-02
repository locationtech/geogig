The below is used to generate the index.html with github's page generator

---

Welcome to the GeoGit project, exploring the use of distributed management of spatial
data. GeoGit draws inspiration from [Git] (http://git-scm.com/), but adapts its core concepts to handle versioning
of geospatial data. Users are able to import raw geospatial data (currently from Shapefiles, 
PostGIS or SpatiaLite) in to a repository where every change to the data is tracked. These
changes can be viewed in a history, reverted to older versions, branched in to sandboxed
areas, merged back in, and pushed to remote repositories. GeoGit is written in Java, available
under the [BSD License](https://github.com/opengeo/GeoGit/blob/master/LICENSE.txt).

For background reading see these two papers on the spatial distributed versioning [Concept](http://opengeo.org/publications/distributedversioning/) and 
[Implementation](http://opengeo.org/publications/distributedversioningimplement/).

We are firmly in 'alpha' mode, working to release early and often, so please join us. Testing, 
documentation, ideas and patches are all greatly appreciated. Don't hesitate to 
[get in touch] (https://groups.google.com/a/opengeo.org/group/geogit/) with any issues, feedback or encouragement.

Download
--------

[Version 0.1.0](http://sourceforge.net/projects/geogit/files/geogit-0.1.0/geogit-cli-app-0.1.0.zip/download) from SourceForge. 
Documentation available for [download](http://sourceforge.net/projects/geogit/files/geogit-0.1.0/geogit_user_manual-0.1.tgz/download) and [online] (docs/index.html).

Installation
------------

Unzip the *geogit-cli-app-0.1.0.zip* to an applications directory, and then add the unzipped `geogit/bin/` folder to your **PATH**.

Running
-------

See the [QuickStart] (docs/quickstart.html) guide for an introduction to GeoGit. Additional 
information available at the full [Manual] (docs/index.html).

Participation
=============

The project is hosted on github:

* https://github.com/opengeo/GeoGit

Discussion takes place on our [geogit google group] (https://groups.google.com/a/opengeo.org/group/geogit/). 
Please join and introduce yourself, we'd love to help, and to figure out ways for you to get involved.

Participation is encouraged using the github *fork* and *pull request* workflow:

> * Include test case demonstrating functionality
> * Contributions are expected to pass all tests and not break the build
> * Include proper license headers on your contributed files

Issues to help out on are at our [issue tracker] (https://github.com/opengeo/GeoGit/issues).

For those who can't code help on documentation is always appreciated, all docs can be found at 
https://github.com/opengeo/GeoGit/tree/master/doc/ and contributed to by editing in ReStructuredText 
and using standard GitHub workflows.

Our build is [actively monitored using hudson] (http://hudson.opengeo.org/hudson/view/geogit/).
