Feature: "sl export" command
    In order to export data from Geogig
    As a Geogig User
    I want to export from the repository into a SpatiaLite database
 
  Scenario: Try exporting a table from HEAD  
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "sl export -o HEAD:Points Points" on the SpatiaLite database
     Then the response should contain "Points exported successfully to Points"
     
  Scenario: Try exporting from an empty directory
    Given I am in an empty directory
     When I run the command "sl export Points Points" on the SpatiaLite database
     Then the response should start with "Not in a geogig repository"
     
  Scenario: Try exporting a feature type
    Given I have a repository
      And I stage 6 features
     When I run the command "sl export -o Points Points" on the SpatiaLite database
     Then the response should contain "Points exported successfully to Points"
     
  Scenario: Try exporting an inexistent feature type
  	Given I have a repository
      And I stage 6 features
     When I run the command "sl export -o WRONGTABLE Points" on the SpatiaLite database
     Then the response should contain "Invalid reference"  
     
Scenario: Try exporting to a table that already exists
  	Given I have a repository
      And I stage 6 features
     When I run the command "sl export Points geogig_pg_test" on the SpatiaLite database
     Then the response should contain "The selected table already exists. Use -o to overwrite"     
           