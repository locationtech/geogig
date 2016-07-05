Feature: "geopkg describe" command
    In order to understand the structure of a table in a GeoPackage file
    As a Geogig User
    I want Geogig to describe the table

  Scenario: Try describing a GeoPackage table from an empty directory
    Given I am in an empty directory
     When I run the command "geopkg describe --table test" on an existing GeoPackage file
     Then the response should contain "Could not find the specified table."
      
  Scenario: Try describing a GeoPackage table
    Given I have a repository
     When I run the command "geopkg describe --table Points" on an existing GeoPackage file
     Then the response should contain "Table : Points"
     
  Scenario: Try to describe a GeoPackage table that doesn't exit in the database
    Given I have a repository
     When I run the command "geopkg describe --table nonexistant_table" on an existing GeoPackage file
     Then the response should contain "Could not find the specified table."
