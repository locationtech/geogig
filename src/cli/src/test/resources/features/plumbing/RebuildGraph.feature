Feature: "rebuild-graph" command
    In order to fix a geogig repository
    As a Geogig User
    I want to rebuild the graph

  Scenario: I try to rebuild the graph
    Given I have a repository
      And I have several branches
     When I run the command "rebuild-graph"

  Scenario: I try to rebuild the graph with quiet argument
    Given I have a repository
      And I have several branches
     When I run the command "rebuild-graph --quiet"
     
  Scenario: I try to rebuild the graph when it is not broken
    Given I have a repository
      And I have several branches
     When I run the command "rebuild-graph"
     Then the response should contain "No missing or incomplete graph elements"
     