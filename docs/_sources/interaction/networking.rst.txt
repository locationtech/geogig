.. _networking:

GeoGig Networking
=================

In order for GeoGig clients to interact with a repository remotely (clone, push, pull, etc) it must be exposed over the network as a service. This can be accomplished in two different ways: through the ``geogig serve`` command or using the GeoServer plugin.

The ``geogig serve`` console command starts up an embedded HTTP server and can be used to expose either a single repository or several repositories from a single file system directory, as explained in the :ref:`web_repository_management` section.

Once started, the embedded server exposes the available repositories at the ``http://localhost:8182/repos`` URL and the GeoServer plugin at the ``http://localhost:8080/geoserver/geogig/repos`` URL.


For example, cloning a repository named ``NaturalEarth`` can be executing either
``geogig clone http://localhost:8182/repos/NaturalEarth NaturalEarthClone``
or
``geogig clone http://localhost:8080/geoserver/geogig/repos/NaturalEarth NaturalEarthClone``
respectively, in order to clone the remote repository to a local folder called ``NaturalEarthClone``

Cloning will automatically set up the remote repository URL as a remote called ``origin``. Changes made to the local clone can be pushed to the remote by executing ``geogig push origin``. Changes made in the remote can be pulled to the local clone by executing ``geogig fetch origin``. See the :ref:`repo.remotes` section for more information on how to interact with remote repositories.
