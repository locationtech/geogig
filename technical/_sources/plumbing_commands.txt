Plumbing commands
==================

Commands described in the user documentation (so-called *porcelain* commands) are meant to be used by a human user, and their output might not be well suited to be parsed or for machine consumption. Also, the output is not guaranteed to be stable, and it might change if, for instance, internationalization is added to GeoGig in the future.

When a stable, machine-readable output is needed, the so-called plumbing commands have to be used instead of the porcelain commands, to guarantee that the output they produce is suitable for being used by other applications, such as a GeoGig front-end, or a script that automates some GeoGig-based task.

This document describes plumbing commands and how they replace the corresponding porcelain commands. Detail description of their output format can also be found here.

ls-tree
-------

The ls-tree command replaces the ``ls`` command and shows a list of elements (trees and features) under a given path, with the following format:

- If the --verbose option is not used, then it prints outs a list of element paths, separated by line breaks. There is no information printed about whether the path corresponds to a tree or to a feature

- If the --verbose option is used, each line correponding to an element has this format:

::

	<feature_type_id> <blank_space> <element_type<feature|tree>> <object_id> <blank_space> <path> <blank_space> <xmin;xmax;ymin;ymax> [<blank_space> <size> <blank_space> num_trees]

Size and number of trees are only printed if the eeent described is a tree 

Paths are absolute paths to the tree. Elements are shown in depthfirst order, so elements under a given tree will appear inmediatly after it.

The ``verbose``, ``-r``, ``-d`` and ``-t`` options are available, with the same effect as in the case of using the ``ls`` commands.

The following are some examples of the output produced by the ``ls-tree`` command

::

	$geogig ls-tree -v parks
	6beb800506a526e151e8f77c422c111cd54bcf7e tree 292f917fbd5153fcfb3def60f5cc57f628ae1252 elevation 18.666297912597656;18.715967178344727;45.776702880859375;45.811668395996094 2 0
	1e6988eab76ab6b9da5da4d174278cca26049ab8 tree 3a2530e0818647b70afc17d09b1d56201aeb6fd3 landuse 18.66728973388672;18.71497344970703;45.77732849121094;45.811126708984375 3 0

	$geogig ls-tree -v -r
	6beb800506a526e151e8f77c422c111cd54bcf7e feature c42b927161372297a4c04640cd42a641da2c14be elevation/1 18.69426727294922;18.69426727294922;45.80892562866211;45.80892562866211
	6beb800506a526e151e8f77c422c111cd54bcf7e feature 43c0c94a353406eb4e0c10d616d54dea3e7a875f elevation/2 18.694978713989258;18.694978713989258;45.808895111083984;45.808895111083984
	1e6988eab76ab6b9da5da4d174278cca26049ab8 feature da06caf087e0eea927896b9fd4b8a4cdc34ffc70 landuse/1 18.676733016967773;18.71497344970703;45.77738571166992;45.81097412109375
	1e6988eab76ab6b9da5da4d174278cca26049ab8 feature 3fc0459c87a4f46df3ec01d0dc64ce7de8ae6511 landuse/2 18.670719146728516;18.672962188720703;45.7775993347168;45.77836608886719
	1e6988eab76ab6b9da5da4d174278cca26049ab8 feature e7d870b11be957a488c026ef5e4c8123145fbb3e landuse/3 18.707529067993164;18.714677810668945;45.77732849121094;45.78892517089844

	$geogig ls-tree -r -t
	elevation
	landuse
	elevation/1
	elevation/1
	landuse/1
	landuse/2
	landuse/3

diff-tree
-----------

The ``diff-tree`` command shows a list of differences between two commits. It uses the same syntax of the ``diff`` command and its ouput is a list of lines with the following formt

::

	<path> <blank_space> <old_objectid> <blank_space> <new_object_id>

And additional ``--describe`` switch is available, which causes the output to contain the values of fields in the affected paths

Output format is as follows

::

	<path>
	<change_type><blank_space><field_name1>
	[<old_value_of_field_1>]
	[<new_value_of_field_1>]
	<change_type><blank_space><field_name2>
	[<old_value_of_field_2>]
	[<new_value_of_field_2>]
	.
	.
	.

Change type is a single character: A (added), M (modified), R (remove) or U (unchanged)

both old and new values for a given field appear only if the field has been modified (M). Otherwise, a single value is found, with a meaning according to the type of change.

If several paths are affected, the set of lines describing a changed path are separated by a blank line

rev-list
---------

The ``rev-list`` command is the plumbing equivalent to the ``log`` porcelain command.

The output of rev-list is a list of commits separated by blank lines, with the following format

::

	"commit" + <blank_space> + <id_of_commit>
	"tree" + <blank_space> + <id_of_tree>
	"author" + <blank_space> + <author_name> + <blank_space> + <author_email> + <blank_space> + <timestamp> + <blank_space> + <timezon_offset> + "\n"
	"committer" + <blank_space> + <commiter_name> + <blank_space> + <commiter_email> + <blank_space> + <timestamp> + <blank_space> + <timezone_offset> + "\n"
	"parent" + <blank_space> + <id_of_parent> + "\n"
	"message" + "\n"
	"\t" + <commit_message>

If several parents exist, the line starting with "parent" will appear repeated.

If the commit message contains line breaks, all lines will appear indented, with a tab character starting each line.

In case of using the ``--changed`` option, the format is as follows:

::

	"commit" + <blank_space> + <id_of_commit>
	"tree" + <blank_space> + <id_of_tree>
	"author" + <blank_space> + <author_name> + <blank_space> + <author_email> + <blank_space> + <timestamp> + <blank_space> + <timezon_offset> + "\n"
	"committer" + <blank_space> + <commiter_name> + <blank_space> + <commiter_email> + <blank_space> + <timestamp> + <blank_space> + <timezone_offset> + "\n"
	"parent" + <blank_space> + <id_of_parent> + "\n"
	"message" + "\n"
	"\t" + <commit_message>
	"changes" + "\n"
	"\t"  + <path> + <blank_space> + <id_of_old_version> + <blank_space> + <id_of_new_version> + "\n"

The last line is repeated as many times as affected features are found.


cat
----

The ``cat`` command replaces the ``show`` command produces a text description of elements in a GeoGig repository. The format for the different types of objects is as follows. An example has been added in each case.


Commit
~~~~~~~

::

	"id" + "\t" + <commit_id> + "\n"
	"COMMIT"\n
	"tree" + "\t" +  <tree id> + "\n"
	"parents" + "\t" +  <parent id> [+ " " + <parent id>...]  + "\n"
	"author" + "\t" +  <author name>  + " " + <author email>  + "\t" + <author_timestamp> + "\t" + <author_timezone_offset> + "\n"
	"committer" + "\t" +  <committer name>  + " " + <committer email>  + "\t" + <committer_timestamp> + "\t" + <committer_timezone_offset> + "\n"      
	"message" + "\t" +  <message> + "\n"

::

	id    509a481257c5791f50f5a35087e432247f9dc8b7
	COMMIT	
	tree    6bc0644ba38372860254c61a62009448ebd8c1e0
	parents    8c08469ffc54f6cc9132855f0415c79cf3fc7785
	author    volaya    volaya@boundlessgeo.com    1358773135891    3600000
	committer    volaya    volaya@boundlessgeo.com    1358773135891    3600000
	message    Updated geometry


Tree  
~~~~~~

::

	"id" + "\t" + <tree_id> + "\n"
	TREE\n 
	"size" + "\t" +  <size> + "\n"
	"numtrees" + "\t" +  <numtrees> + "\n"
	"BUCKET" + "\t" +  <bucket_idx> + "\t" + <ObjectId> + "\t" + <bounds> + "\n"
	or 
	"REF" + "\t" +  <ref_type> + "\t" + <ref_name> + "\t" + <ObjectId> + "\t" + <MetadataId> + "\t" + <bounds> + "\n"
	.
	.

::

	id    0bbed3603377adfbd3b32afce4d36c2c2e59d9d4
	TREE	
	size    50
	numtrees    0
	REF    FEATURE    parks.34    38cadc88ef6dad9f38871d704523ee77f69a7f1d    6350a6955b124119850f5a6906f70dc02ebb31c9    -122.86117933535783;-122.854350067846;42.31833119598368;42.32102693871578;EPSG:4326
	REF    FEATURE    parks.13    b734bc70a8061966e15502c7a0399df61b884dc4    6350a6955b124119850f5a6906f70dc02ebb31c9    -122.86880014388446;-122.86561021610196;42.34400227832745;42.34567119406094;EPSG:4326
	REF    FEATURE    parks.42    eef727418a6cd64960eee0a4e54325e284174218    6350a6955b124119850f5a6906f70dc02ebb31c9    -122.85186496040123;-122.85030419922936;42.3158100546772;42.317125842793224;EPSG:4326
	.
	.
	.

  
Feature
~~~~~~~

::

	"id" + "\t" + <feature_id> + "\n"
	"FEATURE\n"
	<attribute_type_1> + "\t" +  <attribute_value_1> + "\n"
	.
	.
	.     
	<attribute_class_n> + "\t" +  <attribute_value_n> + "\n"

	Attribute type can be one of the following strings: ``NULL, BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, STRING, BOOLEAN_ARRAY, BYTE_ARRAY, SHORT_ARRAY, INTEGER_ARRAY, LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, STRING_ARRAY, POINT, LINESTRING, POLYGON, MULTIPOINT, MULTILINESTRING, MULTIPOLYGON, GEOMETRYCOLLECTION, GEOMETRY, UUID, BIG_INTEGER, BIG_DECIMAL, DATETIME, DATE, TIME, TIMESTAMP``

	[TODO: detail format of some of these data types]


::

	id    ff51bfc2a36d02a3a51d72eef3e7f44de9c4e231
	FEATURE
	STRING    Medford School District
	DOUBLE    636382.400857
	DOUBLE    3818.6667552
	STRING    Abraham Lincoln Elementary
	LONG    4
	STRING    Medford School District
	STRING    School Field
	MULTIPOLYGON    MULTIPOLYGON (((-122.83646412838807 42.36016644633764, -122.83706843181271 42.36018038487805, -122.83740062537728 42.360187694790284, -122.83773129525122 42.36019528458837, -122.83795404148778 42.36020136945975, -122.83819236923999 42.36020660256662, -122.83846546872873 42.360518040102995, -122.83876233613934 42.36084768643743, -122.83979986790222 42.361999744796655, -122.83876583032126 42.36206395843249, -122.8387666181915 42.36241475445113, -122.8350544594257 42.362400655348836, -122.83505311158638 42.36190072779918, -122.8352814492704 42.36189781560542, -122.83546514962634 42.36183970799634, -122.8355995051357 42.361675638841625, -122.83649163970789 42.36166473464665, -122.83646412838807 42.36016644633764)))
	STRING    Public


For array types, values are written as a space-separated list of single values, enclosed between square brackets


Feature type
~~~~~~~~~~~~~~~~~

::

	"id" + "\t" + <feature_type_id> + "\n"
	"FEATURETYPE\n"
	"name" + "\t" +  <feature_type_name> + "\n"
	<attribute_name> + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <true|false>> + "\n"
	<attribute_name> + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <true|false>> + "\n"
	.
	.
	.
  
 For geometry attributes, and additional substring is added at the end of the corresponding line, containing the SRS id.

 ::

	id 49852c03b8dd3c93fcbda7137abda9ad53a9311a
	FEATURETYPE
	name    http://www.opengis.net/gml:parks
	the_geom    MULTIPOLYGON    0    1    true    EPSG:4326
	owner    STRING    0    1    true
	agency    STRING    0    1    true
	name    STRING    0    1    true
	usage    STRING    0    1    true
	parktype    STRING    0    1    true
	area    DOUBLE    0    1    true
	perimeter    DOUBLE    0    1    true

blame
-----

The blame command has a ``--porcelain`` option that generates machine-readable output with the following format:

::

	<name_of_field> <blank_space> <commit_id> <blank_space> <committer_name> <blank_space> <commiter_email> <blank_space> <commit_timestamp> <blank_space> <commit_timezone_offset>


The following is an example of the porcelain output of the ``blame`` command

::

	parktype 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	area 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	perimeter 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	the_geom 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	name 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	owner 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000
	usage a1d6e2c8d377ea90c7639b3834d7ece3ad161d91 volaya volaya@boundlessgeo.com 1367236528690 7200000
	agency 2d132099c2ede0c9ea2306317cfba4796a62abeb volaya volaya@boundlessgeo.com 1367236628965 7200000