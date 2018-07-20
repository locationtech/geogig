Feature: "rev-list" command
    In order to know the history of commits on a repository
    As a Geogig User
    I want to see a list of commits
  
 Scenario: Try to show only a range of commits.
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD~3..HEAD~1"
     Then the response should not contain " Commit4"
      And the response should not contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3" 
      
 Scenario: Try to show a range of commits and provide additional commits.
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD~3..HEAD~1 HEAD~2"
     Then the response should contain "Only one value accepted when using <since>..<until> syntax"

  Scenario: Try to show a log of a repository with a single commit.
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m TestCommit"
     Then the response should contain "3 features added"
     When I run the command "rev-list HEAD"
     Then the response should contain "TestCommit"
      And the response should contain variable "{@ObjectId|localrepo|master}"
     
  Scenario: Try to show a log of a repository with several commits.
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"

  Scenario: Try to show a log of the commits that have changed the points feature
  	Given I have a repository
  	  And I have several commits
  	  And I run the command "rev-list HEAD --path Points"
  	 Then the response should contain "Commit1"
  	  And the response should contain "Commit2"
  	  And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|localrepo|master~3}"
  	  And the response should not contain "Commit3"

  Scenario: Try to show only the last two commits.
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD -n 2"
     Then the response should not contain " Commit1"
      And the response should not contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain variable "{@ObjectId|localrepo|master~2}"
      And the response should contain "Commit4"
      
Scenario: Try to get commits list without starting commit
    Given I have a repository
      And I have several commits
      And I run the command "rev-list"     
     Then the response should contain "No starting commit provided"
      And it should exit with non-zero exit code

  Scenario: Try to show the log, skipping the last 2 commits
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD --skip 2"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain variable "{@ObjectId|localrepo|master~3}"
      And the response should not contain "Commit3"
      And the response should not contain "Commit4"
      And the response should not contain variable "{@ObjectId|localrepo|master}"
      
  Scenario: Try to show the list of commits, with the changes introduced by each one
    Given I have a repository
      And I have several commits
      And I run the command "rev-list HEAD --changed"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain "Points.1"
      And the response should contain "Points.2"      
      And the response should contain "Lines.1"
      
  Scenario: Try to show a log from an empty directory
    Given I am in an empty directory
     When I run the command "rev-list HEAD"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code              
       

 