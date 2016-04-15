Feature: "clone" command
    In order to build on the work in an existing repository
    As a Geogig User
    I want to clone that repository to my local machine
     
  Scenario: Try to clone without specifying a repository
    Given I am in an empty directory
     When I run the command "clone"
     Then it should answer "You must specify a repository to clone."
      And it should exit with non-zero exit code
     
  Scenario: Try to clone with too many parameters
    Given I am in an empty directory
     When I run the command "clone repository directory extra"
     Then it should answer "Too many arguments provided."
      And it should exit with non-zero exit code
     
  Scenario: Try to clone a remote repository
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone remoterepo localrepo"
     Then the response should contain "Cloning into 'localrepo'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should contain "Commit1"
      
  Scenario: Try to clone a remote repository with blank spaces
    Given I am in an empty directory
  	  And there is a remote repository with blank spaces
     When I run the command "clone "remote repo" localrepo"
     Then the response should contain "Cloning into 'localrepo'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should contain "Commit1"      
      
  Scenario: Try to make a shallow clone of a remote repository
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone --depth 2 remoterepo localrepo"
     Then the response should contain "Cloning into 'localrepo'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should not contain "Commit1"
      
  Scenario: Try to clone a remote repository with a branch specified
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone -b branch1 remoterepo localrepo"
     Then the response should contain "Cloning into 'localrepo'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should not contain "Commit5"
      And the response should not contain "Commit4"
      And the response should contain "Commit3"
      And the response should contain "Commit2"
      And the response should contain "Commit1"
