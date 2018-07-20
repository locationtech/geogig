Feature: "conflicts" command
    In order to know which features are conflicted 
    As a Geogig User
    I want to get a list of conflicted elements

  Scenario: Try to list conflicts
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
     And I run the command "conflicts"
     Then the response should contain "Ancestor"
      And the response should contain "Ours"
      Then the response should contain "Theirs"    
     
Scenario: Try to list conflicts showing diffs
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
     And I run the command "conflicts --diff"
     Then the response should contain "StringProp1_1 -> StringProp1_2"
     
Scenario: Try to list conflicts showing only ids
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
     And I run the command "conflicts --ids-only"
     Then the response should contain 1 lines
     
Scenario: Try to list conflicts showing only ids and diffs
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
     And I run the command "conflicts --ids-only --diff"
     Then the response should contain "Cannot use --diff and --ids-only at the same time"    
     
Scenario: Try to list conflicts when no conflicts exist
    Given I have a repository
      And I have staged "points1" 
     When I run the command "conflicts"
     Then the response should contain "No elements need merging"       
     
Scenario: Try to list conflicts showing only refspec
    Given I have a repository
      And I have conflicting branches
     When I run the command "merge branch1"
      And I run the command "conflicts --refspecs-only"
     Then the response should contain "Points/Points.1"
     
