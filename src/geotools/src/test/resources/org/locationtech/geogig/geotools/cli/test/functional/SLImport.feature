Feature: "sl import" command
    In order to import data to Geogig
    As a Geogig User
    I want to import one or more tables from a SpatiaLite database

  Scenario: Try importing into an empty directory
    Given I am in an empty directory
     When I run the command "sl import --table Regions" on the SpatiaLite database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try to import a SpatiaLite table
    Given I have a repository
     When I run the command "sl import --table Regions" on the SpatiaLite database
     Then the response should contain "Import successful."

  Scenario: Try to import a full SpatiaLite database
    Given I have a repository
     When I run the command "sl import --all" on the SpatiaLite database
     Then the response should contain "Import successful."
     
  Scenario: Try to import a SpatiaLite table that doesn't exit in the database
    Given I have a repository
     When I run the command "sl import --table nonexistant_table" on the SpatiaLite database
     Then the response should contain "Could not find the specified table."

  Scenario: Try to import without specifying table or -all
    Given I have a repository
     When I run the command "sl import" on the SpatiaLite database
     Then the response should contain "No tables specified for import. Specify --all or --table <table>."
     
  Scenario: Try to import with table and -all
    Given I have a repository
     When I run the command "sl import --table Regions --all" on the SpatiaLite database
     Then the response should contain "Specify --all or --table <table>, both cannot be set."  
