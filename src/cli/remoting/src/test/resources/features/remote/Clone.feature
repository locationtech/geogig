Feature: "clone" command
    In order to build on the work in an existing repository
    As a Geogig User
    I want to clone that repository to my local machine
     
  Scenario: Try to clone without specifying a repository
    Given I am in an empty directory
     When I run the command "clone"
     Then it should answer "You must specify a repository to clone."
      And it should exit with non-zero exit code
      
  Scenario: Try to clone into the current directory
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone ${remoterepo} ."
     Then it should answer "Cannot clone into your current working directory."
      And it should exit with non-zero exit code
     
  Scenario: Try to clone with too many parameters
    Given I am in an empty directory
     When I run the command "clone repository directory extra"
     Then it should answer "Too many arguments provided."
      And it should exit with non-zero exit code
     
  Scenario: Try to clone a remote repository
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone ${remoterepo} ${repoURI}"
     Then the response should contain "Cloning into '${repoURI}'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|localrepo|master}"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should not contain variable "{@ObjectId|localrepo|branch1}"
      And the response should contain "Commit1"
  
  #annotate with FileSystemReposOnly because other URI providers don't allow spaces
  @FileSystemReposOnly
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
      And the response should contain variable "{@ObjectId|localrepo|HEAD~2}"
      
  Scenario: Try to clone a remote repository that does not exist
    Given I am in an empty directory
     When I run the command "clone nonexistentrepo ${localrepo}"
     Then the response should contain "is not a geogig repository"
      And the repository at "${localrepo}" shall not exist 
      
  Scenario: Try to make a shallow clone of a remote repository
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone --depth 2 ${remoterepo} ${repoURI}"
     Then the response should contain "Cloning into '${repoURI}'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should contain "Commit5"
      And the response should contain "Commit4"
      And the response should contain variable "{@ObjectId|localrepo|master~1}"
      And the response should not contain "Commit3"
      And the response should not contain "Commit2"
      And the response should not contain "Commit1"
      And the response should not contain variable "{@ObjectId|remoterepo|branch1}"
      
  Scenario: Try to clone a remote repository with a branch specified
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone -b branch1 ${remoterepo} ${repoURI}"
     Then the response should contain "Cloning into '${repoURI}'..."
      And the response should contain "Done."
     When I run the command "log"
     Then the response should not contain "Commit5"
      And the response should not contain "Commit4"
      And the response should not contain variable "{@ObjectId|remoterepo|master}"
      And the response should contain "Commit3"
      And the response should contain "Commit2"
      And the response should contain "Commit1"
      And the response should contain variable "{@ObjectId|localrepo|branch1}"
      And the response should contain variable "{@ObjectId|localrepo|branch1~1}"

  Scenario: Try to do a sparse clone of a remote repository with no branch specified
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone --filter someFilter ${remoterepo} ${repoURI}"
     Then the response should contain "Sparse Clone: You must explicitly specify a remote branch"
  
  Scenario: Try to clone a remote repository that has already been cloned
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone ${remoterepo} ${repoURI}"
      And I run the command "clone ${remoterepo} ${repoURI}"
     Then the response should contain "Destination repository already exists"
     
  Scenario: Try to clone a repository to itself
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone ${remoterepo} ${remoterepo}"
     Then the response should contain "Source and target repositories are the same"
     
  #annotate with FileSystemReposOnly because other kind of repo to file would succeed
  @FileSystemReposOnly
  Scenario: Try to clone a repository to the same parent folder without specifying target 
    Given I am in an empty directory
      And there is a remote repository
     When I run the command "clone ${remoterepo}"
     Then the response should contain "Destination repository already exists"

