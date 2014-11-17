There are two types of OSM data entries stored for a geogig repo in which OSM data is used:

- Entries corresponding to download/update operations, which relate an OSM changeset to a commit in the geogig repo
- Entries corresponding to mappings, which store the tree ids before and after mapping operations.
- Entries corresponding to filters, which stores filters used when downloading OSM data

All entries are stored in the correpsoning files in a folder named ``osm`` under the ``.geogig`` folder

The changeset entries are stored in a file named ``log``. When a dowload operation is performed and a filter or mapping is used, a new entry is added to the file. The entry contains the ObjectId of the tree after the OSM data was imported updated, the latest changeset id and its timestamp. This can be used later to know if the OSM in the repo is up to date.

The filter and map descriptions are stored, so when a new dowload/update operation is run, there is no need to specify them again.

The filter description corresponding to a download operation is stored in a file named ``filterXXX``, where XXX is the entry id, that is, the object id of the tree after the download operation.

Mapping information is stored in a folder named ``map`` under the ``osm`` folder. For a mapping operation affecting a tree named ``mytree``, the following files are created:

- a file named ``mytree`` which contains a list of mapping used for that tree in the hitory of the repo. Each entry is a single line with to object ids, corresponding to the ids of the repostiory tree before ad after the mapping. If this file already exists, a new entry will be added, with the ids corresponing to the current mapping operation being logged
- a file named with the object id of the repo tree after the mapping operation, which contains the mapping description.

If the mapping affects several trees, the above will be repeated for each of them.

These files together allow to track the different mappings used for a given tree across the history of the repo.
