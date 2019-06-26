Feature: "pg list" command
    In order to know all of the features available on a PostGIS database
    As a Geogig User
    I want to list all of the features

  Scenario: Try listing wihtout a current repository
    Given I am in an empty directory
     When I run the command "pg list" on the PostGIS database
     Then the response should contain "geogig_pg_test"
      
  Scenario: Try listing with a current repository
    Given I have a repository
     When I run the command "pg list" on the PostGIS database
     Then the response should contain "geogig_pg_test"

