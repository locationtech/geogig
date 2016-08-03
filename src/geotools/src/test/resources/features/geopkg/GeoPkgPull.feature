Feature: "geopkg pull" command
    In order to import data to Geogig
    As a Geogig User
    I want to import one or more tables from a GeoPackage file with the interchange format
    
  Scenario: Try pulling into an empty directory
    Given I am in an empty directory
     When I run the command "geopkg pull --table Points" on an existing GeoPackage file
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try to pull a GeoPackage table without commit message
    Given I have a repository
     When I run the command "geopkg pull --table Points" on an existing interchange GeoPackage file
     Then the response should contain "Commit message not provided"
     
  Scenario: Try to pull a GeoPackage table
    Given I have a repository
     When I run the command "geopkg pull --table Points --message imported" on an existing interchange GeoPackage file
     Then the response should contain "Import successful."
     
  Scenario: Try to pull a GeoPackage table that doesn't exit in the file
    Given I have a repository
     When I run the command "geopkg pull --table nonexistant_table --message imported" on an existing interchange GeoPackage file
     Then the response should contain "Unable to import: No table to import."
     
  Scenario: Try to pull without specifying table
    Given I have a repository
     When I run the command "geopkg pull --message imported" on an existing interchange GeoPackage file
     Then the response should contain "Import successful."     
     
  Scenario: Try to pull a GeoPackage table with a conflict
    Given I have a repository
     When I run the command "geopkg pull --table Points --message imported" on an existing interchange GeoPackage file with a conflict
     Then the response should contain "CONFLICT: Merge conflict in"   