Feature: "rebuild-graph" command
    In order to fix a geogig repository
    As a Geogig User
    I want to rebuild the graph

Scenario: I try to rebuild the graph
    Given I have a repository
      And I have several commits
      And the repository has a truncated graph database
     When I run the command "rebuild-graph"
     Then the response should contain "The following graph elements (commits) were incomplete or missing and have been fixed:"
      And the response should contain 4 lines

Scenario: I try to rebuild the graph with quiet argument
    Given I have a repository
      And I have several commits
      And the repository has a truncated graph database
     When I run the command "rebuild-graph --quiet"
     Then the response should contain "3 graph elements (commits) were fixed."
     
Scenario: I try to rebuild the graph when it is not broken
    Given I have a repository
      And I have several commits
     When I run the command "rebuild-graph"
     Then the response should contain "No missing or incomplete graph elements"
     