Feature: General features of CLI
    In order to use the CLI
    As a Geogig User
    I want to use some basic functionality
      
  Scenario: Use command alias
    Given I have a repository  
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "config alias.ci commit"
      And I run the command "ci -m message"
     Then the response should contain "3 features added"  
     
  Scenario: Show command candidate when command is mistyped
    Given I have a repository      
     When I run the command "brunch"
     Then the response should contain "Did you mean this?"
      And the response should contain "branch"
      And it should exit with non-zero exit code 
     
  Scenario: Show command candidates when command is mistyped
    Given I have a repository      
     When I run the command "confit"
     Then the response should contain "Did you mean one of these?"
      And the response should contain "config"
      And the response should contain "commit"
      And it should exit with non-zero exit code      
     