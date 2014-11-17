Feature: "hooks" functionality
    In order to control how geogig commands are executed
    As a Geogig User
    I want to set hooks in my repo

  Scenario: Set a pre-commit hook and perform a wrong commit
    Given I have a repository
      And I stage 6 features
      And I set up a hook
     When I run the command "commit -m Test"
      Then it should exit with non-zero exit code  
     #Then the response should contain "Commit messages must have at least 5 letters"
     
  Scenario: Set a pre-commit hook and perform a valid commit
    Given I have a repository
      And I stage 6 features
      And I set up a hook
     When I run the command "commit -m LongerMessage "
      Then it should exit with zero exit code  
      And the response should not contain "Commit messages must have at least 5 letters"       
     