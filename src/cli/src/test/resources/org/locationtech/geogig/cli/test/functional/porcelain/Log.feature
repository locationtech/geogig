Feature: "log" command
    In order to know the history of commits on a repository
    As a Geogig User
    I want to log them to the console
  Scenario: Try to show a log of a repository with a single commit.
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m TestCommit"
     Then the response should contain "3 features added"
     When I run the command "log"
     Then the response should contain "Subject: TestCommit"
     
  Scenario: Try to show a log of a repository with several commits.
    Given I have a repository
      And I have several commits
      And I run the command "log"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      
  Scenario: Try to show a log of the commits that have changed the points feature
  	Given I have a repository
  	  And I have several commits
  	  And I run the command "log --path Points"
  	 Then the response should contain "Subject: Commit1"
  	  And the response should contain "Subject: Commit2"
  	  And the response should contain "Subject: Commit4"
  	  
  Scenario: Try to show a log of the commits that have changed the lines feature
    Given I have a repository
      And I have several commits
      And I run the command "log --path Lines"
     Then the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      
  Scenario: Try to show a log of the commits that have changed the points and lines features
    Given I have a repository
      And I have several commits
      And I run the command "log --path Points Lines"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      
  Scenario: Try to show only the last two commits.
    Given I have a repository
      And I have several commits
      And I run the command "log -n 2"
     Then the response should not contain "Subject: Commit1"
      And the response should not contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      
  Scenario: Try to show the log, skipping the last 2 commits
    Given I have a repository
      And I have several commits
      And I run the command "log --skip 2"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should not contain "Subject: Commit3"
      And the response should not contain "Subject: Commit4"
      
  Scenario: Try to show the last 2 commits before the most recent
    Given I have a repository
      And I have several commits
      And I run the command "log -n 2 --skip 1"
     Then the response should not contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should not contain "Subject: Commit4"
      
  Scenario: Try to show a log from an empty directory
    Given I am in an empty directory
     When I run the command "log"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code

  Scenario: Try to show a log of all branches
    Given I have a repository
      And I have several branches
     When I run the command "log --all"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain "Commit5"    
      
  Scenario: Try to show a log of a single branch
    Given I have a repository
      And I have several branches
     When I run the command "log --branch branch1"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should not contain "Commit4"
      And the response should not contain "Commit5"          
       
  Scenario: Try to show a log of all branches specifying the 'until' commit
    Given I have a repository
      And I have several branches
     When I run the command "log --all HEAD..HEAD~1"
     Then the response should contain "Cannot specify 'until' commit when listing all branches"
      And it should exit with non-zero exit code
  
  Scenario: Try to show a log of all branches with decoration
    Given I have a repository
      And I have several branches
     When I run the command "log --all --decoration"
     Then the response should contain "Commit1"
      And the response should contain "Commit2"
      And the response should contain "Commit3"
      And the response should contain "Commit4"
      And the response should contain "Commit5"
     Then the response should contain "HEAD"
     Then the response should contain "master"    
  
  Scenario: Try to show a log of a repository with a single commit.
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m TestCommit"
     Then the response should contain "3 features added"
     When I run the command "log --oneline"
     Then the response should contain 1 lines
      And the response should contain "TestCommit"
     
  Scenario: Try to show a log of a repository with a single commit and decoration
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "commit -m TestCommit"
     Then the response should contain "3 features added"
     When I run the command "log --oneline --decoration"
     Then the response should contain 1 lines
      And the response should contain "(HEAD, refs/heads/master) TestCommit"     
      
  Scenario: Try to show a log of a repository showing only names of affected elements
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m TestCommit"
      And I have staged "points2"
      And I have staged "points3"
      And I have staged "lines1"
      And I run the command "commit -m TestCommit"
     When I run the command "log --names-only"
     Then the response should contain 13 lines
      And the response should contain "Points.2"
      And the response should contain "Points.3"
      And the response should contain "Lines.1"   
      
Scenario: Try to show a log of a repository showing full descriptions of affected elements
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m TestCommit"
      And I have staged "points2"
      And I have staged "points3"
      And I have staged "lines1"
      And I run the command "commit -m TestCommit"
     When I run the command "log --summary"
     Then the response should contain 24 lines
 