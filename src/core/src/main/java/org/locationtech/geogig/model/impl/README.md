
There can be different "clustering" strategies to build a DAG (e.g. canonical, quad tree), that
differ on the computation for the final position of a Node within the graph.

Adding a Node to a DAG consists of the following steps:

A unique identifier is calculated for the Node. The clustering strategy is responsible of computing the
Node identifier based on the Node property of interest, and the maximum possible depth of the DAG.

The Node's unique identifier contains all the information needed to know where in the DAG the Node would lie
at any given depth, so that when more nodes are added to the leaf DAG node containing the Node and it has
to be split into sub-nodes, the leaf nodes can be assigned the corresponding bucket.

The leaf Node object is added to the Node index with the assigned identifier and saved for the last step of
building the final Tree.


A DAG vertex knows its list of children node identifiers, as well as it's own depth in the graph, so that when split
into sub-vertexes the sub-vertex container for each of its leaf nodes can be computed by extracting the corresponding
bucket index from the node identifier at a depth equal to the DAG vertex plus one.

