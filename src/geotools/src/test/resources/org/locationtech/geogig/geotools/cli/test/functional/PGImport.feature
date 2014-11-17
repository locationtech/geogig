Feature: "pg import" command
    In order to import data to Geogig
    As a Geogig User
    I want to import one or more tables from a PostGIS database

  Scenario: Try importing into an empty directory
    Given I am in an empty directory
     When I run the command "pg import --table geogig_pg_test" on the PostGIS database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try to import a PostGIS table
    Given I have a repository
     When I run the command "pg import --table geogig_pg_test" on the PostGIS database
     Then the response should contain "Import successful."

  Scenario: Try to import a full PostGIS database
    Given I have a repository
     When I run the command "pg import --all" on the PostGIS database
     Then the response should contain "Import successful."
     
  Scenario: Try to import a PostGIS table that doesn't exit in the database
    Given I have a repository
     When I run the command "pg import --table nonexistant_table" on the PostGIS database
     Then the response should contain "Could not find the specified table."
     
  Scenario: Try to import without specifying table or -all
    Given I have a repository
     When I run the command "pg import" on the PostGIS database
     Then the response should contain "No tables specified for import. Specify --all or --table <table>."     
     
  Scenario: Try to import with table and -all
    Given I have a repository
     When I run the command "pg import --table geogig_pg_test --all" on the PostGIS database
     Then the response should contain "Specify --all or --table <table>, both cannot be set."       