Feature: "branch" command
    In order to work separately from the main history
    As a Geogig User
    I want to be able to create, delete and list branches
    
  Scenario: Try to create a branch while not in a repository
    Given I am in an empty directory
      And I run the command "branch newBranch"
     Then the response should contain "Not in a geogig repository"
      
  Scenario: Try to delete a branch while not in a repository
    Given I am in an empty directory
      And I run the command "branch --delete newBranch"
     Then the response should contain "Not in a geogig repository"
      And it should exit with non-zero exit code 
     
  Scenario: Try to list branches while not in a geogig repository
    Given I am in an empty directory
      And I run the command "branch"
     Then the response should contain "Not in a geogig repository"
      And it should exit with non-zero exit code 
     
  Scenario: Try to create a branch without having made any commits
    Given I have a repository
      And I run the command "branch newBranch"
     Then the response should contain "no commits yet, branch cannot be created."
      And it should exit with non-zero exit code    
     
  Scenario: Try to create a branch with a non-existent start point
    Given I have a repository
      And I have several commits
      And I run the command "branch newBranch nonexistent"
     Then the response should contain "nonexistent does not resolve to a repository object"
     
  Scenario: Try to create a branch off of master
    Given I have a repository
      And I have several commits
      And I run the command "branch newBranch master"
     Then the response should contain "Created branch refs/heads/newBranch"
     
  Scenario: Try to checkout a branch as soon as it is created
    Given I have a repository
      And I have several commits
      And I run the command "branch -c newBranch"
     When I run the command "branch"
     Then the response should contain "* newBranch"
     
  Scenario: Try to create a branch off of master while not on master
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m Commit1"
      And I run the command "branch -c newBranch"
      And I have staged "lines1"
      And I run the command "commit -m Commit2"
     When I run the command "branch -c newestBranch master"
      And I run the command "log"
     Then the response should contain "Commit1"
      And the response should contain variable "{@ObjectId|localrepo|master}"
      And the response should not contain "Commit2"
      And the response should not contain variable "{@ObjectId|localrepo|newBranch}"
     
  Scenario: Try to create a branch with the same name as an existing branch
    Given I have a repository
      And I have several branches
     When I run the command "branch branch1"
     Then the response should contain "A branch named 'branch1' already exists."
      And it should exit with non-zero exit code  
     
  Scenario: Try to list local branches
    Given I have a repository
      And I have several branches
     When I run the command "branch"
     Then the response should contain "* master"
      And the response should contain "branch1"
      And the response should contain "branch2"
      
  Scenario: Try to list local branches with verbose option
    Given I have a repository
      And I have several branches
     When I run the command "branch -v"
     Then the response should contain "* master"
      And the response should contain "Commit5"
      And the response should contain "branch1"
      And the response should contain "Commit4"
      And the response should contain "branch2"
      And the response should contain "Commit3"
      
  Scenario: Try to delete a branch
    Given I have a repository
      And I have several branches
     When I run the command "branch --delete branch1"
     Then the response should contain "Deleted branch 'branch1'."
     
  Scenario: Try to delete a branch without specifying a name
    Given I have a repository
      And I have several branches
     When I run the command "branch --delete"
     Then the response should contain "no name specified for deletion"  
     And it should exit with non-zero exit code  
     
  Scenario: Try to delete multiple branches
    Given I have a repository
      And I have several branches
     When I run the command "branch --delete branch1 branch2"
     Then the response should contain "Deleted branch 'branch1'"
      And the response should contain "Deleted branch 'branch2'"
     When I run the command "branch"
     Then the response should contain "* master"
      And the response should not contain "branch1"
      And the response should not contain "branch2"
     
  Scenario: Try to delete the branch you are on
    Given I have a repository
      And I have several branches
     When I run the command "checkout branch1"
      And I run the command "branch --delete branch1"
     Then the response should contain "Cannot delete the branch you are on" 
      And it should exit with non-zero exit code 
      
  Scenario: Try to rename a branch
    Given I have a repository
      And I have several branches
     When I run the command "branch --rename branch1 superAwesomeBranch"
     Then the response should contain "renamed branch 'branch1' to 'superAwesomeBranch'"
     When I run the command "branch -v"
     Then the response should contain "* master"
      And the response should contain "superAwesomeBranch"
      And the response should contain "Commit4"
      And the response should not contain "branch1"

  Scenario: Try to rename a branch that you are on
    Given I have a repository
      And I have several branches
     When I run the command "checkout branch1"
      And I run the command "branch --rename superAwesomeBranch"
     Then the response should contain "renamed branch 'branch1' to 'superAwesomeBranch'"
     When I run the command "branch -v"
     Then the response should contain "master"
      And the response should contain "* superAwesomeBranch"
      And the response should contain "Commit3"
      And the response should not contain "branch1"     
      
  Scenario: Try to rename a branch that you are on to a name that already exists
    Given I have a repository
      And I have several branches
     When I run the command "checkout branch1"
      And I run the command "branch --rename branch2"
     Then the response should contain "Cannot rename branch to 'branch2' because a branch by that name already exists"
     When I run the command "branch --rename --force branch2"
     Then the response should contain "renamed branch 'branch1' to 'branch2'"
     When I run the command "branch -v"
     Then the response should contain "master"
      And the response should contain "* branch2"
      And the response should contain "Commit3"
      And the response should not contain "branch1"
      And the response should not contain "Commit4"
      
  Scenario: Try to rename a branch without being in a repository
    Given I am in an empty directory
     When I run the command "branch --rename branch2"
    Then the response should contain "Not in a geogig repository"
    
  Scenario: Try to rename a branch without specifying a name
    Given I have a repository
      And I have several branches
     When I run the command "branch --rename"
     Then the response should contain "You must specify a branch to rename."
      And it should exit with non-zero exit code 
     
  Scenario: Try to rename a branch to the same name
    Given I have a repository
      And I have several branches
     When I run the command "branch --rename branch1 branch1"
     Then the response should contain "Done"