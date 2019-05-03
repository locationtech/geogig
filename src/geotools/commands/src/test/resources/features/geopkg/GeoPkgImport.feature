Feature: "geopkg import" command
    In order to import data to Geogig
    As a Geogig User
    I want to import one or more tables from a GeoPackage file

  Scenario: Try importing into an empty directory
    Given I am in an empty directory
     When I run the command "geopkg import --table Points" on an existing GeoPackage file
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try to import a GeoPackage table
    Given I have a repository
     When I run the command "geopkg import --table Points" on an existing GeoPackage file
     Then the response should contain "Import successful."

  Scenario: Try to import a full GeoPackage file
    Given I have a repository
     When I run the command "geopkg import --all" on an existing GeoPackage file
     Then the response should contain "Import successful."
     
  Scenario: Try to import a GeoPackage table that doesn't exit in the file
    Given I have a repository
     When I run the command "geopkg import --table nonexistant_table" on an existing GeoPackage file
     Then the response should contain "Could not find the specified table."
     
  Scenario: Try to import without specifying table or -all
    Given I have a repository
     When I run the command "geopkg import" on an existing GeoPackage file
     Then the response should contain "No tables specified for import. Specify --all or --table <table>."     
     
  Scenario: Try to import with table and -all
    Given I have a repository
     When I run the command "geopkg import --table Points --all" on an existing GeoPackage file
     Then the response should contain "Specify --all or --table <table>, both cannot be set."       