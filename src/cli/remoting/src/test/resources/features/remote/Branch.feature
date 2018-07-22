Feature: "branch" command
    In order to work separately from the main history
    As a Geogig User
    I want to be able to create, delete and list branches

  Scenario: Try to list remote branches only
    Given I have a repository with a remote
      And I run the command "fetch origin"
     When I run the command "branch -r"
     Then the response should contain "origin/master"
      And the response should contain "origin/branch1"
      #And the response should contain "origin/HEAD"
      And the response should not contain "* master"
      
  Scenario: Try to list all branches
    Given I have a repository with a remote
      And I run the command "fetch origin"
     When I run the command "branch --all"
     Then the response should contain "origin/master"
      And the response should contain "origin/branch1"
      #And the response should contain "origin/HEAD"
      And the response should contain "* master"
