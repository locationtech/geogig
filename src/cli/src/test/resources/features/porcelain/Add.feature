Feature: "add" command
    In order to prepare for making a commit to the geogig repository
    As a Geogig User
    I want to stage my changes to the working tree

  Scenario: Try to add a conflicted feature
    Given I have a repository
      And I have conflicting branches
      And I run the command "merge branch1"
     When I run the command "add Points/Points.1"
     Then the response should contain "1 features and 1 trees staged for commit"
      And it should exit with zero exit code
     When I run the command "conflicts"
     Then the response should contain "No elements need merging"
     
  Scenario: Try to add features to the index
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "add"
     Then the response should contain "3 features and 2 trees staged for commit"
     
  Scenario: Try to add a specific feature type to the index
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "add Points"
     Then the response should contain "2 features and 1 trees staged for commit"
     
  Scenario: Try to add a using too many parameters
    Given I have a repository
      And I have unstaged "points1"      
     When I run the command "add Points Lines"
     Then the response should contain "Only a single path is supported so far"  
      And it should exit with non-zero exit code  
     
  Scenario: Try to add a specific feature to the index
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "add Points/Points.1"
     Then the response should contain "1 features and 1 trees staged for commit"
     
  Scenario: Try to add an empty feature type
    Given I have a repository
      And I have unstaged an empty feature type
     When I run the command "add"
     Then the response should contain "0 features and 1 trees staged for commit"     
     
  Scenario: Try to add an empty feature type to an unclean index
    Given I have a repository
      And I have unstaged "lines1"
      And I run the command "add"
      And I have unstaged an empty feature type
     When I run the command "add"
     Then the response should contain "1 features and 2 trees staged for commit"     
     
  Scenario: Try to add from an empty directory
    Given I am in an empty directory
     When I run the command "add"
     Then the response should start with "Not in a geogig repository"
     And it should exit with non-zero exit code
     
  Scenario: Try to add when no changes have been made
    Given I have a repository
     When I run the command "add"
     Then the response should contain "No unstaged elements"
    
  Scenario: Try to just stage a modified feature with add update
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have unstaged "points1_modified"
      And I have unstaged "lines1"
     When I run the command "add --update"
     Then the response should contain "2 features and 1 trees staged for commit"
     When I run the command "status"
     Then the response should contain "Changes to be committed"
      And the response should contain "3 total"
      And the response should contain "Changes not staged for commit"
      And the response should contain "2 total"
