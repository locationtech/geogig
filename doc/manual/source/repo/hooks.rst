Hooks
=====

GeoGig supports the usage of **hooks**, commands that are executed before or after a given operation is run.

Hooks are written in a supported scripting language and stored in the ``hooks`` directory in the GeoGig repository directory (``.geogig``).

There are two types of hooks, **pre-execution** and **post-execution**, which happen before or after a given operation, respectively.

To prevent erroneous behavior, don't have multiple files containing the same type of hook. (As in, don't have both ``pre_commit.py`` and ``pre_commit.js``.)

Pre-execution
-------------

When an operation is executed, GeoGig searches for a corresponding hook to run **before** the operation is run.

A pre-execution hook should have the prefix ``pre_`` and the name of the operation before which is to be executed, and the extension of the corresponding scripting language used. A Python hook to be run before executing a commit operation should be named ``pre_commit.py``.

All hooks have a global variable named ``params`` that can be used to check the current operation parameters. It is a map containing the actual values of fields in the object representing the operation. Those values represent the arguments used when calling the operation was invoked. Map keys are field names.

Here is an example of a Python pre-commit hook that illustrates the mechanism explained above. This hook ensures a minimum length for commit messages and converts the text to lower case. As it affects the ``commit`` operation, it should be named ``pre_commit.py``.

.. todo:: Above says JS is supported, but here is a Python example.

.. code-block:: python

   exception = Packages.org.geogig.api.hooks.CannotRunGeogigOperationException;
   msg = params.get("message");
   if (msg.length() < 30){
     throw new exception("Commit messages must have at least 30 letters");
   }

   params.put("message", msg.toLowerCase());

Pre-execution hooks can halt the normal execution of the operation, and thus, be used to perform diagnostic checks. To indicate that the operation shouldn't be executed, a ``CannotRunGeogigOperationException`` has to be thrown. Any other exception type is assumed to be due to an error in the script and will not block the execution of the operation.



Post-execution
--------------

GeoGig also supports post-execution hooks. Post-execution hooks run **after** the operation has been executed. Because of when they run, they are not expected to throw exceptions.

Post-execution hooks should be used to perform management tasks once the corresponding operation has been executed. for instance, a post-execution hook linked to the export operation (``post_export``) can be used to execute maintenance operations on the data exported from GeoGig, like building spatial indexes or vacuuming a PostGIS table whenever data is exported from GeoGig into it.

A post-execution hook is named using the prefix ``post_``, the name of the operation after which it is to be executed, and the extension of the corresponding scripting language used.


.. todo::

   This section is unclear:

   GeoGig also supports git-like hooks, written as executable console scripts. They have the same naming as the hooks described above, but a different extension (or no extension at all). If GeoGig finds a hook corresponding to a given operation, but it doesn't have the extension of one of the supported scripting languages, it will try to execute it (so you should make sure the file can be executed). No parameters are passed as arguments to these scripts.

   Pre-execution hooks written this way can also prevent the actual operation to be executed. If the exit code of the script is non-zero, the operation will not be run, having the same effect as throwing a ``CannotRunGeogigOperationException`` exception in the above Python example.


Samples
-------

GeoGig repositories contain a set of sample hooks. They are not active by default, but can be enabled by removing the ``.sample`` suffix from their filename.


Supported hooks
---------------

GeoGig supports hooks for the following operations:

* ``commit``
* ``rebase``
* ``checkout``
* ``apply``
* ``osmimport``
* ``import``
* ``export``

..  Commenting out the parameters until they can be fleshed out

  - ``commit``
    Parameters:
      - ``message``: the commit message.
      - ``committerName``: the name of the committer.
      - ``committerEmail``: the email of the committer.
  - ``rebase``
  - ``checkout``
  - ``apply`` (applying a patch)
  - ``osmimport``. For the osm import command.
  - ``import``. For all import commands (shp, pg and sl)
    Parameters:
      - ``all``: true if it should import all tables from the datastore. It is always true in the case of importing from shapefiles
      - ``table``: the name of the single table to import.  It equals ``null`` in the case of importing from shapefiles
      - ``dataStore``: the GeoTools datastore to import from
  - ``export``
    Parameters:
      - ``featureTypeName``: the path of the feature type to export
      - ``featureStore``: an instance of ``Supplier<SimpleFeatureStore>`` containing the GeoTools feature store to export to


Scripting API
-------------

When creating a hook, it might be necessary to access some of the functions of GeoGig. To do this, use the GeoGig scripting API.

A global variable named ``geogig`` is available to access the GeoGig API. It contains an instance of an object of type ``GeoGigAPI``, which wraps GeoGig operations and provides methods to easily access it.

.. note:: See the API documentation for detailed information about its methods.

To illustrate the usage of this, below is an example of a hook that prevents committing features with topologically incorrect geometries.

.. code-block:: javascript

   Validator = Packages.org.locationtech.jts.operation.valid.IsValidOp;
   var features = geogig.getFeaturesToCommit(null, true);
   for (var i = 0; i < features.length; i++) {
     var feature = features[i];
     geom = feature.getDefaultGeometry();
     op = new Validator(geom) ;
     if (!op.isValid()){
       geogig.throwHookException(op.getValidationError().getMessage());
     }


More elaborate hooks can be created making use of the API along with the GeoTools classes that GeoGig uses internally, such as re-projecting geometries before importing them into the repository.

Also, GeoGig commands can be called from the script, using the ``run()`` method from the ``geogig`` object. It takes the name of the class with the command to call as the first parameter. the second parameter contains the names and values of the parameters needed by the command to be executed.

The following is an example hook that triggers an OpenStreetMap "unmapping" operation whenever the ``mapped`` tree, which is supposed to contain mapped OSM data, is modified after a commit.

.. todo:: It is not clear what an unmapping operation is from the context here. A little explanation would be good.

.. code-block:: javascript

  var diffs = geogig.getFeaturesToCommit('mapped', false);
    if (diffs.length > 0){
      var params = {"path" : "mapped"};
      geogig.run("org.geogig.osm.internal.OSMUnmapOp", params);
    }

The above code would be placed in a file named ``post_commit.js``
