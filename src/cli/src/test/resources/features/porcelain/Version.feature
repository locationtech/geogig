@Version
Feature: "version" command
    In order to view GeoGig version information
    As a Geogig User
    I want to display information about it
    
  Scenario: I want to view the GeoGig version
    Given I am in an empty directory
     When I run the command "version"
     Then the response should contain "Project Version"
      And the response should contain "Build Time"
      And the response should contain "Git Commit ID"
