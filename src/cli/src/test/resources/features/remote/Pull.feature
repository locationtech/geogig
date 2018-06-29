Feature: "pull" command
    In order to integrate changes from a remote repository to my working branch
    As a Geogig User
    I want to pull all new commits from that repository
     
  Scenario: Try to pull from an empty directory
    Given I am in an empty directory
     When I run the command "pull origin"
     Then the response should start with "Not in a geogig repository"
     
  Scenario: Try to pull from origin
    Given I have a repository with a remote
     When I run the command "pull origin --rebase"
     When I run the command "branch --all"
     Then the response should contain "origin/master"
      And the response should not contain "origin/branch1"
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|remoterepo|master~1}"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should contain "Commit1"
      And the response should not contain variable "{@ObjectId|remoterepo|branch1}"
      And the response should contain "Commit1"
      
  Scenario: Try to pull from origin after first pull
    Given I have a repository with a remote
     When I run the command "pull origin"
     When I run the command "branch --all"
     Then the response should contain "origin/master"
     When I run the command "pull origin"
     Then the response should contain "master already up to date."
   
  Scenario: Try to pull a certain depth from origin
    Given I have a repository with a remote
     When I run the command "pull origin --depth 3"
     Then the response should contain "Depth operations can only be used on a shallow clone."
