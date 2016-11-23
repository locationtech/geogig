Feature: "ls" command
    In order to explore the contents of a tree
    As a Geogig User
    I want to display information about it

  Scenario: I try to list contents in an empty directory
    Given I am in an empty directory
     When I run the command "ls"
     Then the response should contain "Not in a geogig repository"
  
  Scenario: I try to list and empty repository
    Given I have a repository
     When I run the command "ls"
     Then the response should contain "The working tree is empty"
      
  Scenario: I try to list and repository
    Given I have a repository
      And I have several commits
     When I run the command "ls"
     Then the response should contain "Root tree/"
      And the response should contain "Points/"
      And the response should contain "Lines/"

  Scenario: I try to show a recursive list in a repository
    Given I have a repository
      And I have several commits
     When I run the command "ls -r"
     Then the response should contain "Points.2"
      And the response should contain "Lines.1"

  Scenario: I try to show only the tree not the children
    Given I have a repository
      And I have several commits
     When I run the command "ls -d"
     Then the response should contain "Points/"
     
  Scenario: I try to show a verbose list of a tree
    Given I have a repository
      And I have several commits
     When I run the command "ls -v Points"
     Then the response should contain "Points/"
      And the response should contain "Points.1"
      And the response should contain "Points.2"
      And the response should contain "Points.3"
             
  Scenario: I try to show a list with seven digit IDs
    Given I have a repository
      And I have several commits
     When I run the command "ls -v -r -t -a 5"
     Then the response should contain 9 lines
      And the response should contain "Points.3"
      And the response should contain "Lines.3"