Feature: "apply" command
    In order to modify feature
    As a Geogig User
    I want to insert a feature according to a definition in a file

Scenario: Insert from a file
    Given I have a repository
      And I stage 6 features
      And I have an insert file
     When I run the command "insert -f ${currentdir}/insert"
     Then the response should contain "1 features successfully inserted"
               
Scenario: Try to insert a feature passing a wrong file
    Given I have a repository
      And I stage 6 features      
      And I have an insert file
     When I run the command "insert -f wrong.file"
     Then the response should contain "Insert file cannot be found"     

Scenario: Try to insert a feature in a tree that does not exist in the repo
    Given I have a repository
      And I have staged "lines1"     
      And I have an insert file
     When I run the command "insert -f ${currentdir}/insert"
     Then the response should contain "The parent tree does not exist: Points"
     