Web-Api Documentation
==============================

This will walk you through GeoGig's web-api and all of its currently supported functionality. This doc also assumes that you already know what any given command does, this is strictly how to use these commands. First I will explain an easy way to get the web-api up and running.

If you don't already have the GeoGig source from GitHub and a GeoGig repository set up do that first. Next, to get the web-api up and running after you have built the latest GeoGig go into your repository and run ``geogig serve``. This will set up a jetty server with your repository and give you access to the web-api. Now you just need to open up a web browser and go to localhost:8182/log to make sure it comes up. After you have gotten it up and running now you can test any of the commands listed here.

All web-api commands have transaction support, which means you can run the web-api commands beginTransaction to start a transaction and then use the id that is returned to do other commands on that transaction instead of the actual repository. After you are done with everything on the transaction you just have call endTransaction through the web-api and pass it the transaction id. Some commands require a transaction to preserve the stability of the repository, those that require it will have ``(-T)`` next to the name of the command in this doc. To pass the transaction id you just need to use the transactionId option and set it equal to the id that beginTransaction returns. Some commands also have other options that are required for that command to work they will have a ``(-R)`` next to them. Any options that have notes associated with them have and asterisk next to them.

.. note:: All web-api command response are formatted for xml by default, however you can get a JSON response by adding this option to the url ``output_format=JSON``.

.. note:: All web-api commands have a variable at the top of the response indicating success or failure, so you can still have a 200 status on the request and have a failure. This can happen when the command runs into an internal error.

Porcelain Commands Supported
-----------------------------------------------

- **Add** (-T)

	 **Currently Supported Options:**
	 
		1) **path** - the path to the feature you want to add
		
			**Type:** String
			
			**Default:** null
				   
	 **Example:**   
 	 
 	 ::

	   localhost:8182/add?path=tree/fid&transactionId=id
	   
	 **Output:**
	
	 ::
	   
	   Returns whether or not the add succeeded or not.

- **Blame**

	**Currently Supported Options:**
	
		a) **commit** - the branch or commit to blame from
		
			**Type:** String
			
			**Default:** null
			
		b) **path** (-R) - the path of the feature
		
			**Type:** String
			
			**Default:** null
	
	**Example:**  
		
 	::
 
 	  localhost:8182/blame?path=pathToFeature&commit=commitId
	
	**Output:**
	
	::
	   
	  Returns all of the attribute values of a feature and the commit that they were last changed.
	   	
- **Branch**

	 **Currently Supported Options:**
	 
		a) **list** (-R) - true to list any branches
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **remotes** - true to list remote branches
		
			**Type:** Boolean
			
			**Default:** false
	
	 **Example:**  
		
 	 ::

	   localhost:8182/branch?list=true&remote=true
	
	 **Output:**
	
	 ::
	   
	   Returns a list of all the branches on the repo, if remote is specified it lists remote branches and the name of the remote they belong to.
	   	
- **Checkout** (-T)

	 **Currently Supported Options:**
	 
		a) **branch*** - the name of the branch to checkout
		
			**Type:** String
			
			**Default:** null
			
		b) **ours*** - true to use our version of the feature specified
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **theirs*** - true to use their version of the feature specified
		
			**Type:** Boolean
			
			**Default:** false
			
		d) **path*** - the path to the feature that will be updated
		
			**Type:** String
			
			**Default:** null

	 **Examples:**  
		
 	 ::

	   localhost:8182/checkout?branch=master&transactionId=id
	       	  localhost:8182/checkout?path=tree/fid&ours=true&transactionId=id
	
	 **Output:**
	
	 ::
	   
	   Returns the branch you were one and the branch you checked out, if path was specified it returns the path and the strategy chosen.
	   	
 .. note:: You must specify either branch OR path not both. If path is specified then you MUST specify either ours or theirs.
 
- **Commit** (-T)

	**Currently Supported Options:**
	
		a) **message** - the message for this commit
		
			**Type:** String
			
			**Default:** null
			
		b) **all** - true to commit everything in the working tree
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **authorName** - the author of the commit
		
			**Type:** String
			
			**Default:** null
			
		d) **authorEmail** - the email of the the author of the commit
		
			**Type:** String
			
			**Default:** null

	**Example:**  
		
 	::

	 localhost:8182/commit?authorName=John&authorEmail=john@example.com&message=something&all=true&transactionId=id
	
	**Output:**
	
	::
	   
	   Returns the commit id and a count of the things that were added, changed and deleted.
	   	
- **Diff**

	**Currently Supported Options:**
	
		a) **oldRefSpec** (-R) - the old ref spec to diff against
		
			**Type:** String
			
			**Default:** null
			
		b) **newRefSpec** - the new ref spec to diff against
		
			**Type:** String
			
			**Default:** null
			
		c) **pathFilter** - a path to filter by
		
			**Type:** String
			
			**Default:** null
			
		d) **showGeometryChanges** - true to show geometry changes
		
			**Type:** Boolean
			
			**Default:** false
			
		e) **page** - the page number to build the response
		
			**Type:** Integer
			
			**Default:** 0
			
		f) **show** - the number of elements to display in the response per page
		
			**Type:** Integer
			
			**Default:** 30

	**Example:**  
		
 	::

	  localhost:8182/diff?oldRefSpec=commitId1&newRefSpec=commitId2&showGeometryChanges=true&show=100
	
	**Output:**
	
	::
	   
	   Returns the path of the feature before and after as well as the object id before and after, if showGeometryChanges is specified it will also return the geometry of the feature.
	   	
- **Fetch**

	**Currently Supported Options:**
	
		a) **prune** - true to prune remote tracking branches locally that no longer exist
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **all** - true to fetch from all remotes
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **remote*** - the remote to fetch from
		
			**Type:** String
			
			**Default:** origin

	**Example:**  
		
 	::
 	
	  localhost:8182/fetch?prune=true&remote=origin
	
	**Output:**
	
	::
	   
	   Returns the name of the remote, the branch name before and after and the value before and after.
	   		  
 .. note:: If remote is not specified it will try to fetch from a remote named origin.

- **Log**

	**Currently Supported Options:**
	
		a) **limit** - the number of commits to print
		
			**Type:** Integer
			
			**Default:** null
			
		b) **offset** - the offset to start listing at
		
			**Type:** Integer
			
			**Default:** null
			
		c) **path** - a list of paths to filter commits by
		
			**Type:** List<String>
			
			**Default:** Empty List
			
		d) **since** - the start commit id to list commits
		
			**Type:** String
			
			**Default:** null
			
		e) **until** - the end commit id to list commits
		
			**Type:** String
			
			**Default:** null
			
		f) **sinceTime** - the start time to list commits from
		
			**Type:** String
			
			**Default:** null
			
		g) **untilTime** - the end time to list commits from
		
			**Type:** String
			
			**Default:** null
			
		h) **page** - the page number to build the response
		
			**Type:** Integer
			
			**Default:** 0
			
		i) **show** - the number of elements to display in the response per page
		
			**Type:** Integer
			
			**Default:** 30
			
		j) **firstParentOnly** - true to only show the first parent of a commit
		
			**Type:** Boolean
			
			**Default:** false
			
		k) **countChanges** - if true, each commit will include a count of each change type compared to its first parent
		
			**Type:** Boolean
			
			**Default:** false
			
		l) **returnRange** - true to only show the first and last commit of the log, as well as a count of the commits in the range
		
			**Type:** Boolean
			
			**Default:** false
			
		m) **summary*** - if true, return all changes from each commit
		
			**Type:** Boolean
			
			**Default:** false

	**Examples:**  
		
 	::

	  localhost:8182/log?path=treeName&firstParentOnly=true
	  localhost:8182/log?summary=true&path=treeName&output_format=csv
	
	**Output:**
	
	::
	   
	   Returns a list of the commits with a given range, if countChanges is specified it also returns the number of adds, modifies and deletes for each commit, if summary with csv output_format specified it downloads a file in csv format of a summary of changes for each commit.
	   			 
 .. note:: You can get the summary downloaded as a .csv file by specifying ``output_format=csv``, this is the only option in the web-api that supports this format.

- **Merge** (-T)

	**Currently Supported Options:**
	
		a) **noCommit** - true to merge without creating a commit afterwards
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **commit*** (-R) - the branch or commit to merge into the currently checked out ref
		
			**Type:** String
			
			**Default:** null
			
		c) **authorName** - the author of the merge commit
		
			**Type:** String
			
			**Default:** null
			
		d) **authorEmail** - the email of the author of the merge commit
		
			**Type:** String
			
			**Default:** null

	**Example:**  
		
 	::

	  localhost:8182/merge?commit=branch1&noCommit=true&transactionId=id
	
	**Output:**
	
	::
	   
	   Returns the object id of both branches being merged and the common ancestor's id as well as the merge commit id if one was made, the number of conflicts there were if there were any, the list of changes that resulted from the merge.
	   		  
 .. note:: You can also pass a ref name for the commit option, instead of a commit hash.

- **Pull**

	**Currently Supported Options:**
	
		a) **remoteName*** - the name of the remote to pull from
		
			**Type:** String
			
			**Default:** origin
			
		b) **all** - true to fetch all
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **ref*** - the ref to pull
		
			**Type:** String
			
			**Default:** Currently Checked Out Branch
			
		d) **authorName** - the author of the merge commit
		
			**Type:** String
			
			**Default:** null
			
		e) **authorEmail** - the email of the author of the merge commit
		
			**Type:** String
			
			**Default:** null

	**Example:**  
		
 	::

	  localhost:8182/pull?remoteName=origin&all=true&ref=master:master
	
	**Output:**
	
	::
	   
	   Returns the result of Fetch, the remote name, the ref name, the number of adds, modifies and removes and the merge result if one was made.
	   		  
 .. note:: If you don't specify the remoteName it will try to pull from a remote named   origin. Also, if ref is not specified it will try to pull the currently checked out branch. The ref option should be in this format remoteref:localref, with the localref portion being optional. If you should opt out of specifying the localref it will just use the same name as the remoteref.

- **Push**

	**Currently Supported Options:**
	
		a) **all** - true to push all refs
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **ref*** - the ref to push
		
			**Type:** String
			
			**Default:** Currently Checked Out Branch
			
		c) **remoteName*** - the name of the remote to push to
		
			**Type:** String
			
			**Default:** origin

	**Example:**  
		
 	::

	  localhost:8182/push?ref=master:master&remoteName=origin
	  
	
	**Output:**
	
	::
	   
	   Returns whether or not it succeeded in pushing data.
	   	
 .. note:: If you don't specify the remoteName it will try to push to a remote named origin. Also, if ref is not specified it will try to push the currently checked out branch. The ref option should be in this format localref:remoteref, with the remoteref portion being optional. If you should opt out of specifying the remoteref it will just use the same name as the localref.

- **Remote**

	**Currently Supported Options:**
	
		a) **list*** - true to list the names of your remotes
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **remove** - true to remove the given remote
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **ping** - true to ping the given remote
		
			**Type:** Boolean
			
			**Default:** false
			
		d) **update** - true to update the given remote
		
			**Type:** Boolean
			
			**Default:** false
			
		e) **verbose** - true to show more info for each repo
		
			**Type:** Boolean
			
			**Default:** false
			
		f) **remoteName*** - the name of the remote to add or remove
		
			**Type:** String
			
			**Default:** null
			
		g) **newName** - the new name of the remote to update
		
			**Type:** String
			
			**Default:** null
			
		h) **remoteURL** - the URL to the repo to make a remote
		
			**Type:** String
			
			**Default:** null
			
		i) **username** - the username to access the remote
		
			**Type:** String
			
			**Default:** null
			
		j) **password** - the password to access the remote
		
			**Type:** String
			
			**Default:** null

	**Examples:**  
		
 	::

	  localhost:8182/remote?list=true&verbose=true
	  localhost:8182/remote?remove=true&remoteName=origin
	  localhost:8182/remote?remoteName=origin&remoteURL=urlToRepo.com
	  localhost:8182/remote?ping=true&remoteName=origin
	  localhost:8182/remote?update=true&newName=origin&remoteName=remote1&remoteURL=urlToRepo.com
	
	**Output:**
	
	::
	   
	   Returns a list of remotes, if verbose was specified it returns the remote url and username, if ping was specified it returns whether or not the ping was a success, if remove was specified it returns the name of the remote that was removed, if update was specified it returns the name of the remote that was updated, if a remote was created it returns the name of the new remote.
	   	
- **Remove** (-T)

	**Currently Supported Options:**
	
		a) **path** (-R) - the path to the feature to be removed
		
			**Type:** String
			
			**Default:** null
			
		b) **recursive** - true to remove a tree and all features under it
		
			**Type:** Boolean
			
			**Default:** false

	**Examples:**  
		
 	::

	  localhost:8182/remove?path=treeName/fid&transactionId=id
	  localhost:8182/remove?path=treeName&recursive=true&transactionId=id
	
	**Output:**
	
	::
	   
	   Returns the path that was deleted.
	   	
- **Status**

	**Currently Supported Options:**
	
		a) **limit** - the number of staged and unstaged changes to make
		
			**Type:** Integer
			
			**Default:** 50
			
		b) **offset** - the offset to start listing staged and unstaged changes
		
			**Type:** Integer
			
			**Default:** 0


	**Example:**  
		
 	::

	  localhost:8182/status?limit=100
	
	**Output:**
	
	::
	   
	   Returns the branch name of the currently checked out branch as well as a list of the staged, unstaged and unmerged features.
	   	
- **Tag**

	**Currently Supported Options:**
	
		a) **list** (-R) - true to list the names of your tags
		
			**Type:** Boolean
			
			**Default:** false

	**Example:**  
		
 	::

	  localhost:8182/tag?list=true
	
	**Output:**
	
	::
	   
	   Returns a list of the tags.
	   	
- **Version**

	**Currently Supported Options:**
	
		none

	**Example:**  
		
 	::

	  localhost:8182/version
	
	**Output:**
	
	::
	   
	   Returns all of the version information for your version of GeoGig.
	   	
Plumbing Commands Supported
-------------------------------------------------------

- **BeginTransaction**

	**Currently Supported Options:**
	
		none

	**Example:**  
		
 	::

	  localhost:8182/beginTransaction
	
	**Output:**
	
	::
	   
	   Returns the id of the transaction that was started.
	   	
- **EndTransaction** (-T)

	**Currently Supported Options:**
	
		a) **cancel** - true to abort all changes made in this transaction
		
			**Type:** Boolean
			
			**Default:** false

	**Example:**  
		
 	::

	  localhost:8182/endTransaction?cancel=true&transactionId=id
	
	**Output:**
	
	::
	   
	   Returns nothing if it succeeded or the transaction id if it failed.
	   	
- **FeatureDiff**

	**Currently Supported Options:**
	
		a) **path** (-R) - the path to feature
		
			**Type:** String
			
			**Default:** null
			
		b) **newTreeish*** - the id or branch of the newer commit
		
			**Type:** String
			
			**Default:** ObjectId.NULL
			
		c) **oldTreeish*** - the id or branch of the older commit
		
			**Type:** String
			
			**Default:** ObjectId.NULL
			
		d) **all** - true to show all attributes not just changed ones
		
			**Type:** Boolean
			
			**Default:** false

	**Example:**  
		
 	::
	 
	  localhost:8182/featurediff?path=treeName/fid&newTreeish=commitId1&oldTreeish=commitId2
	
	**Output:**
	
	::
	   
	   Returns the list of attributes for that feature with the before and after values, the changetype and if it is the geometry it returns the CRS with it.
	   	
 .. note:: If no newTreeish is specified then it will use the commit that HEAD is pointing to. If no oldTreeish is specified then it will assume you want the diff to include the initial commit.

- **LsTree**

	**Currently Supported Options:**
	
		a) **showTree** - true to display trees in the response
		
			**Type:** Boolean
			
			**Default:** false
			
		b) **onlyTree** - true to display only trees in the response
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **recursive** - true to recurse through the trees
		
			**Type:** Boolean
			
			**Default:** false
			
		d) **verbose** - true to print out the type, metadataId and Id of the object
		
			**Type:** Boolean
			
			**Default:** false
			
		e) **path*** - reference to start at
		
			**Type:** String
			
			**Default:** null

	**Example:**  
		
 	::

	  localhost:8182/ls-tree?showTree=true&recursive=true&verbose=true
	
	**Output:**
	
	::
	   
	   Returns the path to each node and if verbose is specified it returns the metadataId, type and objectId.
	   		  
 .. note:: If path is not specified it will use the WORK_HEAD.

- **RebuildGraph**

	**Currently Supported Options:**
	
		a) **quiet** - If true, limit the output of the command
		
			**Type:** Boolean
			
			**Default:** false
			
	**Example:**  
		
 	::
 
 	  localhost:8182/rebuildgraph?quiet=true
	
	**Output:**
	
	::
	   
	   Returns the number of updated graph elements, if quiet is not specified it returns the objectId of each updated node.
	   	
- **RefParse**

	**Currently Supported Options:**
	
		a) **name** (-R) - the name of the ref to parse
		
			**Type:** String
			
			**Default:** null

	**Example:**  
		
 	::

	  localhost:8182/refparse?name=master
	
	**Output:**
	
	::
	   
	   Returns the ref name and objectId, if it is a symbolic ref it returns the target as well.
	   	
- **UpdateRef**

	**Currently Supported Options:**
	
		a) **name** (-R) - the name of the ref to update
		
			**Type:** String
			
			**Default:** null
			
		b) **delete*** - true to delete this ref
		
			**Type:** Boolean
			
			**Default:** false
			
		c) **newValue*** - the new value to change the ref to
		
			**Type:** String
			
			**Default:** ObjectId.NULL

	**Example:**  
		
 	::

	  localhost:8182/updateref?name=master&newValue=branch1
	
	**Output:**
	
	::
	   
	   Returns the same things as Ref parse
	   	   		  
 .. note:: You must specify either delete OR newValue for the command to work.

Web-Api Specific
-----------------------------

- **GetCommitGraph**

    The purpose of the GetCommitGraph function is to traverse the entire commit graph. It starts at the specified commitId and works its way down the graph to either the initial commit or the specified depth. Since it traverses the actual commit graph, unlike log, it will display multiple parents and will list every single commit that runs down each parents history.

	**Currently Supported Options:**
	
		a) **depth** - the depth to search to
		
			**Type:** Integer
			
			**Default:** 0
			
		b) **commitId** (-R) - the id of the commit to start at
		
			**Type:** String
			
			**Default:** ObjectId.NULL
			
		c) **page** - the page number to build the response
		
			**Type:** Integer
			
			**Default:** 0
			
		d) **show** - the number of elements to list per page
		
			**Type:** Integer
			
			**Default:** 30

	**Example:**  
		
 	::

	  localhost:8182/getCommitGraph?show=100
	
	**Output:**
	
	::
	   
	   Returns the same format as log.
	   		
- **ResolveConflict** (-T)

    This command is used to resolve a conflict at the provided path with the provided feature objectId.  This can be used in conjunction with the output response of a MergeFeature request.
    
	**Currently Supported Options:**
	
		a) **path** (-R) - the path to the feature you want to add
		
			**Type:** String
			
			**Default:** null
			
		b) **objectid** (-R) - the object id of the feature
		
			**Type:** String
			
			**Default:** null 
 
	**Example:**  
		
 	::
 			
	  localhost:8182/resolveconflict?path=pathToFeature&objectid=featureObjectId
	
	**Output:**
	
	::
	   
	   Returns whether or not it resolved successfully.
	   	
- **RevertFeature** (-T)

    This command can be used to revert the changes to a single feature in a commit.
	
	**Currently Supported Options:**
	
		a) **authorName** - the author of the merge commit
		
			**Type:** String
			
			**Default:** null
			
		b) **authorEmail** - the email of the author of the merge commit
		
			**Type:** String
			
			**Default:** null
			
		c) **commitMessage** - the commit message for the revert
		
			**Type:** String
			
			**Default:** null
			
		d) **mergeMessage** - the message for the merge of the revert commit
		
			**Type:** String
			
			**Default:** null
			
		e) **newCommitId** (-R) - the commit that contains the version of the feature that we want to undo
		
			**Type:** String
			
			**Default:** null
			
		f) **oldCommitId** (-R) - the commit that contains the version of the feature to revert to
		
			**Type:** String
			
			**Default:** null
			
		g) **path** (-R) - the path to the feature you want to revert
		
			**Type:** String
			
			**Default:** null
 			
	**Example:**  
		
 	::
 
 	    localhost:8182/revertfeature?authorName=John&authorEmail=John@example.com&commitMessage="Reverted Feature"&mergeMessage="Merge of reverted feature"&newCommitId=commitId1&oldCommitId=commitId2&path=pathToFeature
	
	**Output:**
	
	::
	   
	   Returns the same format as Merge.
	   	 	  
Repo Commands
-----------------------------

These commands can be used by using the ``repo/`` endpoint, instead of the standard ``/`` endpoint.

 .. note:: The output format for all repo commands is plain text.


- **MergeFeature**

    This endpoint can be used to merge two features into a new one.  It will return the ObjectId of the new feature when the operation completes.  This endpoint must be accessed by using a POST request that contains a json object to tell GeoGig how to merge the feature.  The following is an example of the json POST data to merge a feature with 3 attributes.
    
    .. code-block:: none
    
       {
         path: 'featureType/feature',
         ours: 'objectId for left feature',
         theirs: 'objectId for right feature',
         merges: {
            attr1: {
                ours: true // use the value from the left feature
            },
            attr2: {
                theirs: true // use the value from the right feature
            },
            attr3: {
                value: 'custom value' // use our own value
            }
         }
       }

    **Example:**  
		
    ::

      localhost:8182/repo/mergefeature
	
    **Output:**
	
    ::
	   
      Returns the id of the merged feature.
	   		
- **Manifest**

    This endpoint can be used to get a list of all refs in the repository and what they point to.  Similar to the Branch command with the list option from above.
    
    **Example:**  
		
    ::

      localhost:8182/repo/manifest
	
    **Output:**
	
    ::
	   
      Returns the list of refs and the ids they point to. 
	   		  
Issues
=======

The main concern with the web-api currently is that it doesn't have any kind of authentication on it, which means that anyone with the url can potentially destroy your repo or steal you data with commands like updateref and pull.

There is also a lot of room for improvement and optimization. There are also several commands that still need to be exposed through the web-api. 
