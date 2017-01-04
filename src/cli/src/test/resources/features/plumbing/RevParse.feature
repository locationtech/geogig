Feature: "rev-parse" command
    As a Geogig User
    I want to determine if I am in a repository

Scenario: I try to print out the repository location"
    Given I have a repository
     When I run the command "rev-parse --resolve-geogig-uri"
     Then the response should contain "localrepo"

Scenario: I check if I am in a repository
    Given I have a repository
     When I run the command "rev-parse --is-inside-work-tree"
     Then the response should contain "true"
     
Scenario: I try to check if I'm in a repository when in an empty directory
    Given I am in an empty directory
     When I run the command "rev-parse --is-inside-work-tree"
     Then the response should contain "Not in a geogig repository"
 
Scenario: I try to print the repository location when in an empty directory
    Given I am in an empty directory
     When I run the command "rev-parse --resolve-geogig-uri"
     Then the response should contain "Not in a geogig repository"
  
Scenario: I try to use a refspec with another argument
    Given I have a repository
      And I stage 6 features
     When I run the command "rev-parse STAGE_HEAD/Points.1 --is-inside-work-tree"
     Then the response should contain "if refSpec is given"
    
Scenario: I try to use a refspec with a bad ref
    Given I have a repository
      And I have several commits
     When I run the command "rev-parse bad_ref"
     Then the response should contain "fatal: ambiguous argument"
  
Scenario: I try to resolve the geogig uri when not in a repository
    Given I am in an empty directory
     When I run the command "rev-parse"
     Then the response should contain "Not in a geogig repository"