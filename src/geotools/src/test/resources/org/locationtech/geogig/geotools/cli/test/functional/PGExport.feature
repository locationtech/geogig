Feature: "pg export" command
    In order to export data to Geogig
    As a Geogig User
    I want to export from the repository into a PostGIS database

  Scenario: Try exporting from an empty directory
    Given I am in an empty directory
     When I run the command "pg export Points Points" on the PostGIS database
     Then the response should start with "Not in a geogig repository:"
     
  Scenario: Try exporting a feature type
    Given I have a repository
      And I stage 6 features
     When I run the command "pg export -o Points MyPoints" on the PostGIS database
     Then the response should contain "Points exported successfully to MyPoints"
     
  Scenario: Try exporting an inexistent feature type
  	Given I have a repository
      And I stage 6 features
     When I run the command "pg export WRONGTABLE Points" on the PostGIS database
     Then the response should contain "pathspec 'WRONGTABLE' did not match any valid path"  
     
Scenario: Try exporting to a table that already exists
  	Given I have a repository
      And I stage 6 features
     When I run the command "pg export Points geogig_pg_test" on the PostGIS database
     Then the response should contain "The selected table already exists. Use -o to overwrite"     
  
  Scenario: Try exporting a table from HEAD  
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "pg export -o HEAD:Points CommitedPoints" on the PostGIS database
     Then the response should contain "Points exported successfully to CommitedPoints"    
