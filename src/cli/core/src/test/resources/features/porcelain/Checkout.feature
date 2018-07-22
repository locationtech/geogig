Feature: "checkout" command
	In order to view an alternate version of the data
	As a Geogig User
	I want to be able to checkout out branches
	
  Scenario: Create a new branch and check it out
  	Given I have a repository
  	  And I have several commits
  	 When I run the command "branch newBranch"
  	 Then the response should contain "Created branch refs/heads/newBranch"
  	 When I run the command "checkout newBranch"
	  And I run the command "status"
	 Then the response should contain "# On branch newBranch"
	 
  Scenario: Try to checkout something while not in a repository
  	Given I am in an empty directory
  	 When I run the command "checkout noBranch"
  	 Then the response should contain "Not in a geogig repository"
  	  And it should exit with non-zero exit code
  	 
  Scenario: Try to checkout without specifying a path
  	Given I have a repository
  	 When I run the command "checkout"
     Then the response should contain "no branch or paths specified"
      And it should exit with non-zero exit code
     
  Scenario: Try to checkout multiple things at once
    Given I have a repository
     When I run the command "branch newBranch"
      And I run the command "branch noBranch"
      And I run the command "checkout noBranch newBranch"
     Then the response should contain "too many arguments"
      And it should exit with non-zero exit code
     
  Scenario: Try to checkout a branch that doesn't exist
    Given I have a repository
     When I run the command "checkout noBranch"
     Then the response should contain "'noBranch' not found in repository"
      And it should exit with non-zero exit code
     
  Scenario: Try to make a change but don't commit and then checkout a different branch without forcing
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m Commit1"
      And I run the command "branch newBranch"  
  	 Then the response should contain "Created branch refs/heads/newBranch"  
     When I run the command "checkout newBranch"
      And I have unstaged "points2"
      And I run the command "checkout master"
     Then the response should contain "Working tree and index are not clean. To overwrite local changes, use the --force option"
      And it should exit with non-zero exit code
      
  Scenario: Try to make a change but don't commit and then checkout a different branch with forcing
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m Commit1"
      And I run the command "branch newBranch"  
  	 Then the response should contain "Created branch refs/heads/newBranch"  
     When I run the command "checkout newBranch"
      And I have unstaged "points2"
      And I run the command "checkout -f master"
      And I run the command "status"
     Then the response should not contain "# Changes not staged for commit:"
      And the response should contain "# On branch master"
      
  Scenario: Try to make a change to a feature and revert back to an old version using path filtering
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m Commit1"
      And I modify a feature
      And I run the command "checkout -p Points/Points.1"
      And I run the command "status"
     Then the response should contain "nothing to commit"
           
  Scenario: Try to get rid of changes that I have made with path filtering with multiple paths
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m Commit1"
      And I have staged "lines1"
      And I run the command "commit -m Commit2"
      And I modify a feature
      And I have unstaged "lines2"
      And I run the command "checkout -p Lines Points/Points.1"
      And I run the command "status"
     Then the response should contain "nothing to commit"
      
  Scenario: Try to bring a feature from a different branch into this branch
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m Commit1"
      And I have staged "points2"
      And I run the command "commit -m Commit2"
      And I have staged "points3"
      And I run the command "commit -m Commit3"
      And I run the command "branch -c newBranch"
      And I have staged "lines1"
      And I run the command "commit -m Commit4"
      And I have staged "lines2"
      And I run the command "commit -m Commit5"
      And I have staged "lines3"
      And I run the command "commit -m Commit6"
      And I run the command "checkout master"
      And I run the command "checkout newBranch -p Lines/Lines.1"
      And I run the command "status"
     Then the response should contain "Lines/Lines.1"
      And the response should contain "added"
      And the response should contain "# Changes not staged for commit:"
      
  Scenario: Try to revert using both --ours and --theirs
    Given I have a repository           
      When I run the command "checkout -p Points/Points.1 --ours --theirs"
     Then the response should contain "Cannot use both --ours and --theirs"
      And it should exit with non-zero exit code
     
  Scenario: Try to revert a feature where the version you want doesn't exist
    Given I have a repository
      And I have staged "points2"
     When I modify a feature
      And I run the command "checkout -p Points/Points.1"
     Then the response should contain "'Points/Points.1' didn't match a feature in the tree"
      And it should exit with non-zero exit code

  Scenario: Try to revert an unmerged feature
    Given I have a repository
      And I have a merge conflict state
     When I run the command "checkout -p Points/Points.1"
     Then the response should contain "path Points/Points.1 is unmerged"
      And it should exit with non-zero exit code 
          
  Scenario: Try to revert a feature to the --theirs version and fix the conflict
    Given I have a repository
      And I have a merge conflict state
     When I run the command "checkout -p Points/Points.1 --theirs"
      And I run the command "add"
      And I run the command "commit -m Commit"
     Then the response should contain "Committed"

  Scenario: Try to checkout a specific commit
    Given I have a repository
      And I have several commits
     When I create a detached branch
     Then the response should contain "You are in 'detached HEAD' state"
