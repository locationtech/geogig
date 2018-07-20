Feature: "cherry-pick" command
    In order to select specific changes to bring to the current branch
    As a Geogig User
    I want to cherry pick several commits from other branches

  Scenario: Try to cherry pick several commits
    Given I have a repository
      And I have several branches
     When I run the command "cherry-pick branch1 branch2"
	 Then the response should contain "Too many commits specified."
	  And it should exit with non-zero exit code
      
  Scenario: Try to cherry pick a single commit
    Given I have a repository
      And I have several branches
     When I run the command "cherry-pick branch1"
      And I run the command "log"
     Then the response should contain "Commit3"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"
      And the response should not contain "Commit2"
      And the response should not contain "Commit4"
      And the response should contain "Commit5"
      And the response should contain variable "{@ObjectId|localrepo|HEAD~1}"
      And the response should contain "Commit1"
      And the response should contain variable "{@ObjectId|localrepo|HEAD~2}"

  Scenario: Try to cherry pick a nonexistent branch
    Given I have a repository
      And I have several branches
     When I run the command "cherry-pick nonexistent"
     Then the response should contain "Commit not found"
      And it should exit with non-zero exit code
     
  Scenario: Try to cherry pick without specifying any commits
    Given I have a repository
      And I have several branches
     When I run the command "cherry-pick"
     Then it should answer "No commits specified."
     
  Scenario: Try to cherry pick from an empty directory
    Given I am in an empty directory
     When I run the command "cherry-pick branch1"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code
     
  Scenario: Try to cherry pick a commit that causes conflict
    Given I have a repository
      And I have conflicting branches
     When I run the command "cherry-pick branch1"
     Then the response should contain "CONFLICT: conflict in Points/Points.1"
      And it should exit with non-zero exit code
     
