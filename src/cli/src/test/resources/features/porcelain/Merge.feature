Feature: "merge" command
    In order to combine two or more geogig histories into one
    As a Geogig User
    I want to merge one or more commit histories into my current branch

  Scenario: Try to merge one branch to a parent branch
    Given I have a repository
      And I have several branches
     When I run the command "merge branch1 -m MergeMessage"
     Then the response should contain "2 features added"
     When I run the command "log --first-parent"
     Then the response should contain "MergeMessage"
      And the response should contain "Commit5"
      And the response should contain variable "{@ObjectId|localrepo|HEAD~1}"
      And the response should contain "Commit1"
      And the response should contain variable "{@ObjectId|localrepo|HEAD~2}"
      And the response should not contain "Commit4"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should not contain variable "{@ObjectId|localrepo|branch1~1}"
      And the response should not contain variable "{@ObjectId|localrepo|branch2}"

  Scenario: Try to merge the same branch twice
    Given I have a repository
      And I have several branches
     When I run the command "merge branch1 -m MergeMessage"
     Then the response should contain "2 features added"
     When I run the command "merge branch1 -m MergeMessage2"
     Then the response should contain "The branch has already been merged."
      And it should exit with non-zero exit code
      
  Scenario: Try to merge without specifying any commits
    Given I have a repository
      And I have several branches
     When I run the command "merge -m MergeMessage"
     Then it should answer "No commits provided to merge."
      And it should exit with non-zero exit code
      
  Scenario: Try to merge a nonexistent branch
    Given I have a repository
      And I have several branches
     When I run the command "merge nonexistent"
     Then the response should start with "Commit not found"
      And it should exit with non-zero exit code
     
  Scenario: Try to merge from an empty directory
    Given I am in an empty directory
     When I run the command "merge branch1"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code

  Scenario: Try to merge two conflicting branches
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
     Then the response should contain "CONFLICT: Merge conflict in Points/Points.1"
      And it should exit with non-zero exit code     

  Scenario: Try to perform an octopus merge with conflicts
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1 branch2"
     Then the response should contain "Cannot merge more than two commits when conflicts exist"
      And it should exit with non-zero exit code 
  
  Scenario: Try to perform an octopus merge
    Given I have a repository
      And I have several branches
     When I run the command "merge branch1 branch2"
     Then the response should contain "Merge branch refs/heads/branch1"
     Then the response should contain "Merge branch refs/heads/branch2"
        
  Scenario: Try to merge two conflicting branches using --ours strategy
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1 --ours"
     Then the response should contain "Merge branch refs/heads/branch1"   
        
  Scenario: Try to merge two conflicting branches using --ours and --theirs strategy
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1 --ours --theirs"
     Then the response should contain "Cannot use both --ours and --theirs" 
     
  Scenario: Try to merge two conflicting branches using --theirs strategy
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1 --theirs"
     Then the response should contain "Merge branch refs/heads/branch1"     
     
  Scenario: Try to abort a conflicted merge
    Given I have a repository
      And I have a merge conflict state
     When I run the command "merge branch1 --abort"
     Then the response should contain "Merge aborted successfully"
     When I run the command "status"
     Then the response should contain "nothing to commit"        
     
  Scenario: Try to abort when there is no conflict
    Given I have a repository
     When I run the command "merge --abort"
     Then the response should contain "There is no merge to abort"
      And it should exit with non-zero exit code            
