Feature: "rebase" command
    In order to linearize the geogig history
    As a Geogig User
    I want to rebase my local commits onto an existing branch

  Scenario: Try to rebase one branch to a parent branch
    Given I have a repository
      And I have several branches
     When I run the command "rebase master branch1"
      And I run the command "log"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain variable "{@ObjectId|localrepo|branch1}"
      And the response should not contain "Commit4"
      And the response should not contain variable "{@ObjectId|localrepo|branch2}"
      And the response should contain "Commit5"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"
      
  Scenario: Try to rebase one branch to a parent branch
    Given I have a repository
      And I have several branches
     When I run the command "rebase master branch1 --squash squashmessage"
      And I run the command "log"
     Then the response should contain "Commit1"
      And the response should not contain "Commit2"
      And the response should not contain "Commit3"      
      And the response should not contain "Commit4"
      And the response should not contain variable "{@ObjectId|localrepo|branch2}"
      And the response should contain "Commit5"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"
      And the response should contain "squashmessage"
            
  Scenario: Try to graft a branch onto another branch
    Given I have a repository
      And I have several branches
     When I run the command "rebase branch1 branch2 --onto master"
      And I run the command "log"
     Then the response should contain "Commit1"
      And the response should not contain "Commit2"
      And the response should not contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain "Commit5"
      
  Scenario: Try to rebase a nonexistant branch
    Given I have a repository
      And I have several branches
     When I run the command "rebase master nonexistant"
     Then it should answer "The branch reference could not be resolved."
      And it should exit with non-zero exit code
     
  Scenario: Try to rebase to a nonexistant upstream
    Given I have a repository
      And I have several branches
     When I run the command "rebase nonexistant branch1"
     Then it should answer "The upstream reference could not be resolved."
      And it should exit with non-zero exit code
     
  Scenario: Try to graft a branch onto a nonexistant branch
    Given I have a repository
      And I have several branches
     When I run the command "rebase master branch1 --onto nonexistant"
     Then it should answer "The onto reference could not be resolved."
      And it should exit with non-zero exit code
     
  Scenario: Try to rebase from an empty directory
    Given I am in an empty directory
     When I run the command "rebase master branch1"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code

  Scenario: Try to rebase with conflicts and skip
    Given I have a repository
      And I have conflicting branches
     When I run the command "rebase branch1 master"
     Then the response should contain "CONFLICT"
     And it should exit with non-zero exit code
     When I run the command "rebase --skip"
    And I run the command "log"
     Then the response should contain "Commit1"
      And the response should not contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|localrepo|branch1}"
      
  Scenario: Try to rebase with conflicts and continue
    Given I have a repository
      And I have conflicting branches
      And I run the command "rebase branch1 master"   
	  And I have unstaged "points1"
	  And I run the command "add"        
      And I run the command "rebase --continue"
      And I run the command "log"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|localrepo|branch1}"
      And the response should contain variable "{@ObjectId|localrepo|master~3}"

  Scenario: Try to rebase with conflicts and abort
    Given I have a repository
      And I have conflicting branches
     When I run the command "rebase master branch1"
     Then the response should contain "CONFLICT"
     And it should exit with non-zero exit code
     When I run the command "rebase --abort"
	 Then the response should contain "aborted successfully"  	       
     
 Scenario: Try to rebase --skip when no conflict exist
    Given I have a repository           
     When I run the command "rebase --skip"
	 Then the response should contain "Cannot skip"
	  And it should exit with non-zero exit code  	       
	 
 Scenario: Try to rebase --continue when no conflict exist
    Given I have a repository           
     When I run the command "rebase --continue"
	 Then the response should contain "Cannot continue"
	  And it should exit with non-zero exit code
	 
 Scenario: Try to rebase --abort when no conflict exist
    Given I have a repository           
     When I run the command "rebase --abort"
	 Then the response should contain "Cannot abort"
	  And it should exit with non-zero exit code
     
