.. _updating:

Updating the repository data.
==============================

If you have modified your original data, you should update the data in the GeoGig repository, so it is kept synchronized. This section extends what we have already seen about importing data, by considering some special situations that might arise when importing into a repository that already has some previous data. Along with the next section, which covers the GeoGig commands used for exporting data from the GeoGig repository to a file or database, they describe all the tools and commands needed to set a bidirectional communication between a GeoGig repository and external files and databases. These files and databases keep a snapshot of the repository accessible to external applications such as a desktop GIS, which can work on them and modify them.

Let's assume that our GeoGig repository already has some data, which has been imported from a shapefile. That puts the data in the GeoGig working tree, and we have after that staged and committed it to the repository database. Now we have changed the original shapefile adding some new features and editing the old ones, and we want that to be reflected in the GeoGig repository, creating a new version of the data it stores. The way to do this is re-importing the shapefile, running the ``geogig shp import`` command as we did in the first import.

Importing without additional options actually removes the previous tree at the path you specify (or, if you specify no path, the path taken from the data source, into which where feature are to be imported), and then adds those features from the data source. Remember that this is happening only in the working tree, your previous data is safely stored in the repository database.

However, you might want to perform a different modification of the working tree, such as adding a set of new features from a different shapefile or table. An import without additional options will cause the previous data to be removed, but in this case, we do not want that to happen, since we want both the previous data, and the newly imported one to be merged in the working tree. In that case, and to perform a safe import and add new features without removing he previous ones, the ``--add`` option has to be used. Only additions will be reflected in the working tree. This option is to be used basically when you want to extend the data under a given tree, adding extra features.


Combining different feature types
-----------------------------------

We have assumed that the new features to import have the same feature type as the ones already in the import path, but, as we know, features under the same path do not have to necessarily share the same feature type. In the case of shapefiles, several shapefiles containing features with different feature types can be imported to the same path. In the case of importing from a database, several tables can be imported into the same path in the GeoGig repository.

Imagine that you already imported a shapefile containing polygons with a given set of attributes, and now you want to import into the same path another shapefile with polygons, which contain the same attributes but with an extra one containing the area of each polygon. In that case, feature types do not match, and the situation is different, but GeoGig provide tools to solve it.

By default, GeoGig will not let you import into an existing tree if the feature type of the data to import is not the same one as the feature type of the existing tree. This is to prevent trees with mixed feature types to appear, so in case you want that to happen, you have to explicitly tell GeoGig to do it.

If the feature type is different, GeoGig will try to match it to the default existing feature type before aborting the import operation. This is done to allow using different data formats to be used seamlessly, since they might store a given feature type differently. Let's see an example.

Let's say that you have imported a shapefile with an attribute called ``Area``. The feature type will appear as follows.

``the_geom``: MULTIPOLYGON
``Area``: DOUBLE

Imagine that you export it to a GeoJSON file, make some changes and then re-import it. The feature type of the data to import is the following.

``Area`` : DOUBLE
``geom`` : MULTIPOLYGON

Although the feature type is the same, the GeoTools library used by GeoGig understands the file to import in a different manner, putting the geometry field as last instead of first, and using a different name. GeoGig will, however, understand that the feature type is the same as the existing feature type and will correctly set that existing feature type as the feature type of the imported feature types.

If you do not use the "--add" option, the full destination tree is removed before importing and the new imported data is used to replace the previous data. Trees with mixed feature types cannot appear, in this case, but GeoGig will not let you import a layer with a different feature type, anyway. This is done to prevent unchanged features being reported as changed merely because the feature type (although being the same) has a different definition, as in the example above. If you really want to import something with a different feature type (as in the shapefile example mentioned, where a new area field is added), you must use the ``--force-featuretype`` switch.

If ``--add`` and ``--force-featuretype`` are used, features are imported, regardless of their feature type. The default feature type remains unchanged and the tree will contain features of several different feature types.

Apart from these options, an additional option is available to control how the import command should behave when importing features with a feature type different to the default feature type of the specified path: ``--alter``.


If ``--alter`` is used, the feature type of the features to import will be set as the new default feature type of the destination tree. All the features that already existed in it will be modified to match that feature type. After this type of import operation, all features in the destination path will have the path's default feature type. If the new imported features have extra attributes, the features already in the repository will have null values for those fields. If attributes in the features already in the repository do not exist in the new imported attributes, they will be deleted.

Here is an example to help you understand the above ideas. Let's assume we have a tree named ``points`` with one single feature, with the following attributes:

- ``xcoord: 25``
- ``ycoord: 30``
- ``elevation: 332.3``

Now we import a shapefile into that tree, which contains just another point with the following attributes:

- ``xcoord: 55``
- ``ycoord: 10``
- ``name: point.1``

Using no options, you will have a tree with just one feature, namely the one in the shapefile you have just imported.

Using the ``--add`` option, you will end up having both elements in your tree. That is,

- ``xcoord: 25``
- ``ycoord: 30``
- ``elevation: 332.3``


- ``xcoord: 55``
- ``ycoord: 10``
- ``name: point.1``

No element is changed and the default feature also remains unchanged and set to the feature type of the first feature.

Using the ``--alter`` option instead, you will end up with the tree containing the elements shown below

- ``xcoord: 25``
- ``ycoord: 30``
- ``name: NULL``


- ``xcoord: 55``
- ``ycoord: 10``
- ``name: point.1``

The feature that was already in the tree has been changed to adapt to the feature type of the newly imported feature. That feature type is now the default feature type of the tree.

When importing from a database, if the "--all" option is selected and a destination path is supplied, the ``--add`` option is automatically added. Otherwise, importing each table would overwrite the features imported previously, and only features from the last table would appear on the selected path after importing. The ``--alter`` and ``--add`` options cannot be used simultaneously.
