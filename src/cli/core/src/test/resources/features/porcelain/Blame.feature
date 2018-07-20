Feature: "blame" command
    In order to know the history of a single feature
    As a Geogig User
    I want to see who edited each attribute of a feature

  Scenario: Try to run blame with a valid path
    Given I have a repository
      And I have several commits
     When I run the command "blame Points/Points.1"
     Then the response should contain 3 lines     
      
  Scenario: Try to run blame with a valid path in porcelain mode
    Given I have a repository
      And I have several commits
     When I run the command "blame --porcelain Points/Points.1"
     Then the response should contain 3 lines  
     And the response should contain "1001"  
     
  Scenario: Try to run blame with the --no-values switch
    Given I have a repository
      And I have several commits
     When I run the command "blame --no-values Points/Points.1"
     Then the response should contain 3 lines 
      And the response should not contain "1001"         
           
  Scenario: Try to run blame with a wrong path
    Given I have a repository
      And I have several commits
     When I run the command "blame wrongpath"
     Then the response should contain "The supplied path does not exist"     
     
  Scenario: Try to run blame with more than one path
    Given I have a repository
      And I have several commits
     When I run the command "blame Point/Points.1 Points/Points.2"
     Then the response should contain "Only one path allowed"        
     
  Scenario: Try to run blame with no arguments
    Given I have a repository
      And I have several commits
     When I run the command "blame"
     Then the response should contain "A path must be specified"  
          
  Scenario: Try to run blame with a feature type
    Given I have a repository
      And I have several commits
     When I run the command "blame Points"
     Then the response should contain "The supplied path does not resolve to a feature"
     
  Scenario: Try to reset from an empty directory
    Given I am in an empty directory
     When I run the command "blame"
     Then the response should contain "Not in a geogig repository"          