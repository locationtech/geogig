Feature: "geopkg list" command
    In order to know all of the features available in a GeoPackage file
    As a Geogig User
    I want to list all of the features

  Scenario: Try listing whithout a current repository
    Given I am in an empty directory
     When I run the command "geopkg list" on an existing GeoPackage file
     Then the response should contain "Points"
      
  Scenario: Try listing from a valid directory
    Given I have a repository
     When I run the command "geopkg list" on an existing GeoPackage file
     Then the response should contain "Points"

