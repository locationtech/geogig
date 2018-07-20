Feature: "squash" command
    In order to modify history of the repository
    As a Geogig User
    I want to squash commits

Scenario: Squash commits
    Given I have a repository
      And I have several commits
     When I run the command "squash HEAD~2 HEAD"
      And I run the command "log --oneline"
     Then the response should contain 2 lines
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Squash commits with message
    Given I have a repository
      And I have several commits
     When I run the command "squash HEAD~2 HEAD -m squashed"
      And I run the command "log --oneline"
     Then the response should contain 2 lines  
      And the response should contain "squashed"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Squash commits in wrong order
    Given I have a repository
      And I have several commits
     When I run the command "squash HEAD HEAD~2"
     Then the response should contain "wrong order"
     
 Scenario: Squash commits with only one commit provided
    Given I have a repository
      And I have several commits
     When I run the command "squash HEAD~2"
     Then the response should contain "2 commit references must be supplied"     