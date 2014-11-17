Feature: "sl list" command
    In order to know all of the features available on a SpatiaLite database
    As a Geogig User
    I want to list all of the features

  Scenario: Try listing from an empty directory
    Given I am in an empty directory
     When I run the command "sl list" on the SpatiaLite database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try listing from a valid directory
    Given I have a repository
     When I run the command "sl list" on the SpatiaLite database
     Then the response should contain "Regions"
      And the response should contain "HighWays"
      And the response should contain "Towns"

