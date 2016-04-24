Feature: "revert" command
	In order to undo committed changes
	As a Geogig user
	I want to revert a series of commits and commit those changes
	
  Scenario: Try to revert something while not in a geogig repository
  	Given I am in an empty directory
  	  And I run the command "revert master"
  	 Then the response should contain "Not in a geogig repository"
  	  And it should exit with non-zero exit code
  	 
  Scenario: Try to revert with nothing specified for reverting
    Given I have a repository
      And I run the command "revert"
     Then the response should contain "nothing specified for reverting"
      And it should exit with non-zero exit code
     
  Scenario: Try to revert one commit
    Given I have a repository
      And I have several commits
     When I run the command "revert master"
      And I run the command "log"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      And the response should contain "Subject: Revert 'Commit4'"
      
  Scenario: Try to revert a commit that doesn't exist
    Given I have a repository
      And I have several commits
     When I run the command "revert doesntExist"
     Then the response should contain "Couldn't resolve 'doesntExist' to a commit, aborting revert"
      And it should exit with non-zero exit code
     When I run the command "log"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      And the response should not contain "Subject: Revert 'Commit4'"
     
  Scenario: Try to revert multiple commits
   Given I have a repository
     And I have several commits
    When I run the command "revert master~1 master~2"
     And I run the command "log"
     Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      And the response should contain "Subject: Revert 'Commit2'"
      And the response should contain "Subject: Revert 'Commit3'"
      
  Scenario: Try to revert multiple commits but with one nonexistant commit
   Given I have a repository
     And I have several commits
    When I run the command "revert master~1 blah"
    Then the response should contain "Couldn't resolve 'blah' to a commit, aborting revert"
     And it should exit with non-zero exit code
    When I run the command "log"
    Then the response should contain "Subject: Commit1"
      And the response should contain "Subject: Commit2"
      And the response should contain "Subject: Commit3"
      And the response should contain "Subject: Commit4"
      And the response should not contain "Subject: Revert of commit"
            	
	
  Scenario: Try to revert with conflict and abort
   Given I have a repository
     And I have several commits     
    When I run the command "revert HEAD~3"
    Then the response should contain "could not apply" 
     And the response should contain "CONFLICT: conflict in Points/Points.1"
     And it should exit with non-zero exit code    
    When I run the command "revert --abort"
    Then the response should contain "aborted"
    
  Scenario: Try to revert without commiting
   Given I have a repository
     And I have several commits          
     And I run the command "revert master --no-commit"        
    When I run the command "log"
    Then the response should not contain "Revert"    
    
  Scenario: Try to revert with conflict and continue
   Given I have a repository
     And I have several commits
    When I run the command "revert HEAD~3"
    Then the response should contain "could not apply"
    And it should exit with non-zero exit code
    When I have staged "points1"     
    When I run the command "revert --continue"
     And I run the command "log"
    Then the response should contain "Revert"
               
