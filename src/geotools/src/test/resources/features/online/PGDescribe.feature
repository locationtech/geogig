Feature: "pg describe" command
    In order to understand the structure of a table in a PostGIS database
    As a Geogig User
    I want Geogig to describe the table

  Scenario: Try describing a PostGIS table from an empty directory
    Given I am in an empty directory
     When I run the command "pg describe --table geogig_pg_test" on the PostGIS database
     Then the response should contain "Table : geogig_pg_test"
      
  Scenario: Try describing a PostGIS table
    Given I have a repository
     When I run the command "pg describe --table geogig_pg_test" on the PostGIS database
     Then the response should contain "Table : geogig_pg_test"
     
  Scenario: Try to describe a PostGIS table that doesn't exit in the database
    Given I have a repository
     When I run the command "pg describe --table nonexistant_table" on the PostGIS database
     Then the response should contain "Could not find the specified table."
