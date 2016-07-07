.. _networking:

GeoGig Networking
=================

In order for geogig clients to be able interact with a repository remotely (clone, push, pull, etc), it must be exposed over a network as a service. This can be accomplished in 2 different ways: through the ``geogig serve`` command or with the GeoServer plugin.

The ``geogig serve`` console command starts up an embedded HTTP server and can be used to expose either a single repository or several repositories from a single file system directory, as explanied in the :ref:`web_repository_management` section.

Once started, the embedded server exposes the available repositories at the ``http://localhost:8182/repos`` URL, while the GeoServer plugin at the ``http://localhost:8080/geoserver/geogig/repos`` URL.


For example, cloning a repository named ``NaturalEarth`` can be executing either
``geogig clone http://localhost:8182/repos/NaturalEarth NaturalEarthClone``
or
``geogig clone http://localhost:8080/geoserver/geogig/repos/NaturalEarth NaturalEarthClone``
respectively, in order to clone the remote repository to a local folder called ``NaturalEarthClone``

Cloning will automatically set up the remote repository URL as a remote called ``origin``. Then changes made to the local clone
can be pushed up to the remote by simply executing ``geogig push origin``, and changes made in the remote pulled to the local clone
by executing ``geogig fetch origin``. See the :ref:`repo.remotes` section for more information on how to interact with remote repositories.
