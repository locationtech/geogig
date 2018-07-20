Feature: "geopkg list" command
    In order to know all of the features available in a GeoPackage file
    As a Geogig User
    I want to list all of the features

  Scenario: Try listing from an empty directory
    Given I am in an empty directory
     When I run the command "geopkg list" on an existing GeoPackage file
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try listing from a valid directory
    Given I have a repository
     When I run the command "geopkg list" on an existing GeoPackage file
     Then the response should contain "Points"

