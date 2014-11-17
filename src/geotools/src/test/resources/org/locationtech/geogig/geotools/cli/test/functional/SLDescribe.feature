Feature: "sl describe" command
    In order to understand the structure of a table in a SpatiaLite database
    As a Geogig User
    I want Geogig to describe the table

  Scenario: Try describing a SpatiaLite table from an empty directory
    Given I am in an empty directory
     When I run the command "sl describe --table Regions" on the SpatiaLite database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try describing a SpatiaLite table
    Given I have a repository
     When I run the command "sl describe --table Regions" on the SpatiaLite database
     Then the response should contain "Table : Regions"
     
  Scenario: Try to describe a SpatiaLite table that doesn't exist in the database
    Given I have a repository
     When I run the command "sl describe --table nonexistant_table" on the SpatiaLite database
     Then the response should contain "Could not find the specified table."
