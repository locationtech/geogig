Feature: "reset" command
    In order to undo local changes
    As a Geogig User
    I want to reset the head, and optionally, the working tree and index to the state of another commit

  Scenario: Try to do a mixed reset of all local changes
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --mixed"
     Then the response should contain "Unstaged changes after reset:"
      And the response should contain 2 lines
     When I run the command "status"
     Then the response should contain "Changes not staged for commit"
      And the response should not contain "Changes to be committed"
      
  Scenario: Try to do a hard reset of all local changes
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --hard"
     Then it should answer ""
     When I run the command "status"
     Then the response should contain "nothing to commit"
     
  Scenario: Try to do a soft reset of all local changes
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --soft"
     Then it should answer ""
     When I run the command "status"
     Then the response should not contain "Changes not staged for commit"
      And the response should contain "Changes to be committed"
  
  Scenario: Try to do a mixed and hard reset of all local changes
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --hard --mixed"
     Then it should answer "you may only specify one mode."
     
  Scenario: Try to do a mixed and soft reset of all local changes
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --soft --mixed"
     Then it should answer "you may only specify one mode."
     
  Scenario: Try to reset from an empty directory
    Given I am in an empty directory
     When I run the command "reset"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code
     
  Scenario: Try to reset with no commits
    Given I have a repository
     When I run the command "reset"
     Then it should answer "Commit could not be resolved."
      And it should exit with non-zero exit code
     
  Scenario: Try to reset to a nonexistant commit
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset nonexistant"
     Then it should answer "Commit could not be resolved."
      And it should exit with non-zero exit code
     
  Scenario: Try to do a reset of a specific path
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --path Points"
     Then the response should contain "Unstaged changes after reset:"
      And the response should contain 2 lines
     When I run the command "status"
     Then the response should contain "Changes not staged for commit"
      And the response should not contain "Changes to be committed"
      
  Scenario: Try to do a reset of a non-used path
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --path Lines"
     Then it should answer ""
     When I run the command "status"
     Then the response should not contain "Changes not staged for commit"
      And the response should contain "Changes to be committed"
      
  Scenario: Try to do a reset of multiple paths
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --path Lines Points"
     Then the response should contain "Unstaged changes after reset:"
      And the response should contain 2 lines
     When I run the command "status"
     Then the response should contain "Changes not staged for commit"
      And the response should not contain "Changes to be committed"

  Scenario: Try to reset to the state of another branch
    Given I have a repository
      And I have several branches
     When I run the command "checkout branch2"
      And I run the command "reset branch1"
     Then the response should contain "Unstaged changes after reset:"
      And the response should contain 2 lines
     When I run the command "log"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain variable "{@ObjectId|localrepo|branch1}"
      And the response should not contain "Subject: Commit4"
      And the response should not contain "Subject: Commit5"
      And the response should not contain variable "{@ObjectId|localrepo|master}"
      
  Scenario: Try to do a reset with a mode and paths
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m Test"
     Then the response should contain "3 features added"
     When I modify and add a feature
      And I run the command "reset --hard --path Lines"
     Then it should answer "Ambiguous call, cannot specify paths and reset mode."
      And it should exit with non-zero exit code
     
  Scenario: Try to do a reset with removed feature
    Given I have a repository
      And I have several commits
      And I remove and add a feature
     When I run the command "reset"
     Then the response should contain "Unstaged changes after reset"