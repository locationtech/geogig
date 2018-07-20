Feature: "commit" command
    In order to finalize a set of changes that have been staged
    As a Geogig User
    I want to create a commit and add it to the repository

    
  Scenario: Try to commit with timestamp
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -t 2010-04-22T19:53:23Z -m msg"
     When I run the command "log --utc"
     Then the response should contain "2010-04-22"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"
    
  Scenario: Try to commit with timestamp in millisecs
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -t 1000000000 -m msg"
     When I run the command "log"
     Then the response should contain "1970-01"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Try to commit current staged features
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Try to commit current staged features using a message with blank spaces
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m "A message with spaces""
     Then the response should contain "3 features added" 
      And the response should contain "A message with spaces"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Try to perform multiple commits
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "commit -m Test2"
     Then the response should contain "1 changed"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Try to commit without providing a message
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit"
     Then it should answer "No commit message provided"
      And it should exit with non-zero exit code
     
  Scenario: Try to commit using a previous commit
    Given I have a repository
      And I have several commits
      And I have staged "points1"      
     When I run the command "commit -c HEAD~1"
     Then the response should not contain "No commit message provided"          
     
  Scenario: Try to commit from an empty directory
    Given I am in an empty directory
     When I run the command "commit -m Test"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code
     
  Scenario: Try to commit when no changes have been made
    Given I have a repository
     When I run the command "commit -m Test"
     Then the response should start with "Nothing to commit"
      And it should exit with non-zero exit code

  Scenario: Try to commit when there is a merge conflict
    Given I have a repository
      And I have a merge conflict state
     When I run the command "commit -m Message"
     Then the response should contain "Cannot run operation while merge or rebase conflicts exist"
      And it should exit with non-zero exit code
     
  Scenario: Try to amend last commit
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m Test"
      And I have staged "points2"
     When I run the command "commit --amend"
     Then the response should contain "2 features added"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"


  Scenario: Try to amend last commit, when no previous commit has been made
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit --amend"
     Then the response should contain "Cannot amend"        
     
  Scenario: Try to commit without message while solving a merge conflict
    Given I have a repository
      And I have a merge conflict state
     When I run the command "checkout -p Points/Points.1 --theirs"
      And I run the command "add"
      And I run the command "commit"     
     Then the response should contain "Merge branch refs/heads/branch1"
      And the response should contain "Conflicts:"
      And the response should contain "Points/Points.1"             
     
  Scenario: Try to commit only points
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
      And I have staged "lines2"
     When I run the command "commit -m Test Points"
     Then the response should contain "2 features added"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"
