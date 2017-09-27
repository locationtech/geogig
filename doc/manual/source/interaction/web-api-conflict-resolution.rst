.. _conflict_resolution:

Web API: Conflict Resolution
############################

Some commands may trigger conflicts that must be resolved before continuing.  If you are using a command that can trigger merge conflicts, you should check the response for ``response/Merge/conflicts``.  The only exception to this is :ref:`command_reportMergeScenario`, in which each Feature element should be checked for conflicts.  Once merge conflicts have been reported, the transaction that was used will be in a conflicted state until they are resolved.  There are a couple options for resolving conflicts:

1. **MergeFeature and ResolveConflict:**  If you would like to resolve a conflict on an attribute-by-attribute basis, the best way to do so would be to use the :ref:`command_mergefeature` endpoint.  This allows for the creation of a new feature that combines the desired attributes of two sides of a conflicting feature.  The result of that endpoint is a GeoGig object ID that can be passed to the :ref:`command_resolveconflict` endpoint.
    
2. **Checkout and Add:**  If you would like to simply select the whole version of a feature on ``ours`` or ``theirs`` side of a conflict, you can use the :ref:`command_checkout` endpoint while specifying the path of the feature.  This will put the desired feature into the working tree where you can then use the :ref:`command_add` endpoint while specifying the path of the feature to add it to the staging area and resolve the conflict.
    
Once all conflicts have been resolved (you can use the :ref:`command_status` endpoint to verify), a new commit must be made to complete the original merge operation.  This is done through the :ref:`command_commit` endpoint where you can optionally specify a message that describes how you solved the conflict.  After the commit is made, you may resume working on your data.
