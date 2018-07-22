Feature: "remote" command
    In order to track changes from other repositories
    As a Geogig User
    I want to add and remove remotes for the current repository

  Scenario: Try to add and list remote from an empty directory
    Given I am in an empty directory
     When I run the command "remote add myremote http://myremote.com"
     Then the response should contain "Not in a geogig repository"
     
  Scenario: Try to add and list a remote to the current repository
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "remote list"
     Then it should answer "myremote"
     
  Scenario: Try to add several remotes and list them
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "remote add myremote2 http://myremote2.org"
      And I run the command "remote list"
     Then the response should contain "myremote"
      And the response should contain "myremote2"
     
  Scenario: Try to add a remote that already exists
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "remote add myremote http://myremote2.org"
     Then it should answer "Could not add, a remote called 'myremote' already exists."
      And it should exit with non-zero exit code
     
  Scenario: Try to add a remote and list with verbose mode
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "remote list -v"
     Then the response should contain "myremote http://myremote.com (fetch)"
      And the response should contain "myremote http://myremote.com (push)"
     
  Scenario: Try to add a remote that tracks a specific branch
    Given I have a repository
     When I run the command "remote add -t branch myremote http://myremote.com"
      And I run the command "config remote.myremote.fetch"
     Then it should answer "+refs/heads/branch:refs/remotes/myremote/branch"
     
  Scenario: Try to add a remote that tracks all branches
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "config remote.myremote.fetch"
     Then it should answer "+refs/heads/*:refs/remotes/myremote/*;+refs/tags/*:refs/tags/*"

  Scenario: Try to add, remove, and list a remote
    Given I have a repository
     When I run the command "remote add myremote http://myremote.com"
      And I run the command "remote rm myremote"
      And I run the command "remote list"
     Then it should answer ""
     
  Scenario: Try to remove a remote from an empty repository
    Given I am in an empty directory
     When I run the command "remote rm myremote"
     Then the response should contain "Not in a geogig repository"
      And it should exit with non-zero exit code
     
  Scenario: Try to remove a remote that doesn't exist
    Given I have a repository
     When I run the command "remote rm myremote"
     Then it should answer "Could not find a remote called 'myremote'."
     And it should exit with non-zero exit code
     
  Scenario: Try to list remotes from an empty repository
    Given I am in an empty directory
     When I run the command "remote list"
     Then the response should contain "Not in a geogig repository"
      And it should exit with non-zero exit code 
    
     
