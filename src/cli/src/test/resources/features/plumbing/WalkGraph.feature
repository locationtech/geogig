Feature: "walk-graph" command
    In order to walk through objects in the history tree
    As a Geogig User
    I want to see the object information of each object in the tree
    
  Scenario: I try to walk the graph with no arguments
    Given I have a repository
      And I have several commits
     When I run the command "walk-graph"
     Then the response should contain "Reference not provided"

  Scenario: I try to walk the graph with bad arguments
    Given I have a repository
      And I have several commits
     When I run the command "walk-graph garbage"
     Then the response should contain "Can't resolve reference"
  
  Scenario: I try to walk the graph
    Given I have a repository
      And I have several commits
     When I run the command "walk-graph master"
     Then the response should contain "TREE"
      And the response should contain "FEATURETYPE"
      And the response should contain "FEATURE"
      And the response should not contain "Points"
      And the response should contain 12 lines
      
  Scenario: I try to walk the graph with verbose enabled
    Given I have a repository
      And I have several commits
     When I run the command "walk-graph -v master"
     Then the response should contain "TREE"
      And the response should contain "FEATURETYPE"
      And the response should contain "FEATURE"
      And the response should contain "Points"
      And the response should contain "Lines"
      And the response should contain 12 lines