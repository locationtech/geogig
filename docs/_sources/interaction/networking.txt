.. _networking:

GeoGig Networking
=================

In order for geogig clients to be able interact with a repository remotely, it must be exposed over a network as a service. This can  be accomplished in 2 different ways both of which are still under active development. With a local clone of the geogig source tree, mvn can be used to start a development server, and GeoServer can be built with GeoGig support. Both methods are described below.

More background information on GeoGig remotes can be found elsewhere in the manual and the technical docs.

Standalone Testing Server
=========================

To start the geogig network develoment server from a local clone of the GeoGig source tree in the src/parent directory::

    mvn jetty:run -pl ../web/app -f pom.xml -Dorg.geogig.web.repository=/Users/jj0hns0n/data/gisdata-repo/
    
Output similar to the following indicates everything is working correctly.
::

    [INFO] --- jetty-maven-plugin:7.1.6.v20100715:run (default-cli) @ geogig-web-app ---
    [INFO] Configuring Jetty for project: GeoGig WebApp
    [INFO] webAppSourceDirectory /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp does not exist. Defaulting to /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp
    [INFO] Reload Mechanic: automatic
    [INFO] Classes = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/target/classes
    [INFO] Context path = /
    [INFO] Tmp directory = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/target/tmp
    [INFO] Web defaults = org/eclipse/jetty/webapp/webdefault.xml
    [INFO] Web overrides =  none
    [INFO] web.xml file = file:/Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp/WEB-INF/web.xml
    [INFO] Webapp directory = /Users/jj0hns0n/projects/geohub-dev/geogig/src/web/app/src/main/webapp
    [INFO] Starting jetty 7.1.6.v20100715 ...
    2013-05-27 13:36:45.081:INFO::jetty-7.1.6.v20100715
    2013-05-27 13:36:46.553:INFO::No Transaction manager found - if your webapp requires one, please configure one.
    2013-05-27 13:36:46.663:INFO::Started SelectChannelConnector@0.0.0.0:8080
    [INFO] Started Jetty Server
    [INFO] Starting scanner at interval of 5 seconds.

Test that the network service is working correctly by attempting to clone it.::

    $ geogig clone http://localhost:8080/geogig/ gisdata-repo-clone

Sample Log of the clone operation 
::

    2013-05-27 13:33:06.616:INFO:/:GeoGIG: [Restlet] Attaching application: org.geogig.web.Main@5fff83a9 to URI: /geogig null null
    May 27, 2013 1:33:06 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getdepth   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/manifest   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getdepth   -   200 -http://localhost:8080  Java/1.6.0_45   -
    May 27, 2013 1:34:51 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:51    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=2cdf52bcf5cf5aed78eab15ec56c81b3554136c7   200 -   0   3   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=2d74a9a48b2382a0fc77cbd72b4bde16e1e73b9b   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=3dbaa0649dfd166fb24ccd96739b8db5eb77da93   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/getparents commitId=3840823f112a1667aee39f29fe2acba57042f9bc   200 -   0   1   http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:52 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:52    127.0.0.1   -   127.0.0.1   8080    POST    /geogig/repo/batchobjects   -   200 -   192 34  http://localhost:8080   Java/1.6.0_45   -
    May 27, 2013 1:34:58 PM org.restlet.engine.log.LogFilter afterHandle
    INFO: 2013-05-27    13:34:58    127.0.0.1   -   127.0.0.1   8080    GET /geogig/repo/manifest   -   200 -http://localhost:8080  Java/1.6.0_45   -

The getdepth, manifest, getparents and batchobjects operations performed are described in more detail in the technical documentation.
