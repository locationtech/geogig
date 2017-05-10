.. interaction.logging:

The GeoGig logging system
=========================

GeoGig has a logging system that you might need to check, especially when experiencing issues.

.. note:: This is not to be confused with the reflog that Git implements, and that GeoGig will eventually have as well. This is just a log of errors, warnings and messages of several types, useful to be able to trace the activity of GeoGig and analyze and debug it.

If a GeoGig command cannot be run and GeoGig know the reason for that (for instance, you have entered a wrong parameter or the repository is corrupted), it will show you an explanation in the console. It might happen, however, that an error appears during execution and GeoGig cannot handle that and turn it into a meaningful explanation. In that case, the most detailed description of the error that is possible to produce will be stored in the logging file and GeoGig will tell you to go to that file in case you want more information.

The logging file records information about the activity of GeoGig on a given repository, including, but not limited to, error traces. In case you are a programmer, this might help you understand what is happening. If not, you can use the content of the log file to provide more detailed information to GeoGig developers or when asking for a solution in the GeoGig mailing list, so it is important to know how to find the logging file.

By default, the logging file is located in your repository folder under ``.geogig/log/geogig.log``

GeoGig uses the `Logback <http://logback.qos.ch/>`_ framework for logging. Check its documentation to know more about how to configure logback.

Apart from the logging file you will find other files in the ``log`` folder. Metrics are collected in the ``metrics.csv`` file, but you must explicitly enable it. On the console, type the following.

::

    $ geogig config metrics.enabled true

Now, whenever you call a GeoGig command, the running time of all internal operations that are called will be saved to the metrics file.
