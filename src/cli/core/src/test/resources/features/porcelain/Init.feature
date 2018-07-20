Feature: "init" command
    In order to start versioning my spatial datasets
    As a repository Owner
    I want to create a new repository on a directory of my choice

  Scenario: Create repository in the current empty directory
    Given I am in an empty directory
     When I run the command "init"
     Then it should answer "Initialized empty Geogig repository in ${currentdir}/.geogig"
      And the repository shall exist

  Scenario: Create repository specifying initial configuration
    Given I am in an empty directory
     When I run the command "init --config foo.bar=baz"
     Then the repository shall exist
     When I run the command "config foo.bar"
     Then it should answer "baz"

  Scenario: Create repository specifying the target directory
    Given I am in an empty directory
     When I run the command "init roads"
     Then it should answer "Initialized empty Geogig repository in ${currentdir}/roads/.geogig"
      And if I change to the respository subdirectory "roads"
     Then the repository shall exist

  Scenario: Try to init a repository when already inside a repository
    Given I have a repository
     When I run the command "init"
     Then the response should start with "Reinitialized existing Geogig repository"
      And the repository shall exist

  Scenario: Try to init a repository from inside a repository subdirectory
    Given I have a repository
      And I am inside a repository subdirectory "topp/shapes"
     When I run the command "init"
     Then the response should start with "Reinitialized existing Geogig repository in"
      And the repository shall exist
    
  Scenario: Init specifying repo URI
    Given I am in an empty directory
     When I run the command "init ${repoURI}"
     Then the repository at "${repoURI}" shall exist
     