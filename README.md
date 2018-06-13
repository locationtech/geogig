GeoGig - Geospatial Distributed Version Control System
======================================================

Welcome to the GeoGig project, exploring the use of distributed management of spatial data. GeoGig draws inspiration from [Git](http://git-scm.com/), but adapts its core concepts to handle versioning of geospatial data. Users are able to import raw geospatial data (currently from Shapefiles, PostGIS or SpatiaLite) in to a repository where every change to the data is tracked. These changes can be viewed in a history, reverted to older versions, branched in to sandboxed areas, merged back in, and pushed to remote repositories. GeoGig is written in Java, available under the BSD License.

<!-- For background reading see these two papers on the spatial distributed versioning [Concept ](http://boundlessgeo.com/whitepaper/new-approach-working-geospatial-data-part-1/),  [Implementation](http://boundlessgeo.com/whitepaper/distributed-versioning-geospatial-data-part-2/), and [Potential Development](http://boundlessgeo.com/whitepaper/distributed-versioning-geospatial-data-part-3/). -->

Details
-------

Project Lead: [Gabriel Roldan](https://github.com/groldan)

Status:

Version 1.2.0 has been released and it is available for [download](https://github.com/locationtech/geogig/releases/tag/v1.2.0).

The build is actively monitored at [LocationTech Build Server](https://hudson.locationtech.org/geogig/).

License
-------

GeoGig is proudly open source:

* Source code is distributed under an [Eclipse Distribution License (EDL)](LICENSE.txt) unless otherwise stated.
* For details on third-party dependencies review [NOTICE](NOTICE.txt)

Download
--------

The latest release [Version 1.2.0](https://github.com/locationtech/geogig/releases/tag/v1.2.0) is available on GitHub.

The previous release [Version 1.1.0](https://github.com/locationtech/geogig/releases/tag/v1.1.0) is also available on GitHub.

Installation
------------

Unzip geogig-<version>.zip to an applications directory, and then add the unzipped geogig/bin/ folder to your PATH.

Upgrading
---------

If you are upgrading from a previous version of GeoGig, please see the relevant [Upgrade Guides](http://geogig.org/upgrade/).

Running
-------

See the [QuickStart](http://geogig.org/#install) guide for an introduction to GeoGig. Additional information available at the full [Manual](http://geogig.org/docs/index.html).

Developing
----------

If you want to get involved in the development of GeoGig, build GeoGig yourself or know more about the technical details behind GeoGig, check the [developers section](https://github.com/locationtech/geogig/blob/master/doc/technical/source/developers.rst).

See [CONTRIBUTING](CONTRIBUTING.md) for details on making GitHub pull request.

Participation
-------------

[![Join the chat at https://gitter.im/locationtech/geogig](https://badges.gitter.im/locationtech/geogig.svg )](https://gitter.im/locationtech/geogig?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Everyone is invited to participate in GeoGig and help in its development. Check the [How to help ](https://github.com/locationtech/geogig/blob/master/helping.rst) section to read about how you can help us improve GeoGig.
