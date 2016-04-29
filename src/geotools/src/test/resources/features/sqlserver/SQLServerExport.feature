Feature: "sqlserver export" command
    In order to export data to Geogig
    As a Geogig User
    I want to export from the repository into a SQL Server database

  Scenario: Try exporting from an empty directory
    Given I am in an empty directory
     When I run the command "sqlserver export Points Points" on the SQL Server database
     Then the response should start with "Not in a geogig repository"
     
  Scenario: Try exporting a feature type
    Given I have a repository
      And I stage 6 features
     When I run the command "sqlserver export -o Points MyPoints" on the SQL Server database
     Then the response should contain "Points exported successfully to MyPoints"
     
  Scenario: Try exporting an inexistent feature type
    Given I have a repository
      And I stage 6 features
     When I run the command "sqlserver export WRONGTABLE Points" on the SQL Server database
     Then the response should contain "pathspec 'WRONGTABLE' did not match any valid path"  
     
Scenario: Try exporting to a table that already exists
    Given I have a repository
      And I stage 6 features
     When I run the command "sqlserver export Points geogig_sqlserver_test" on the SQL Server database
     Then the response should contain "The selected table already exists. Use -o to overwrite"     
  
  Scenario: Try exporting a table from HEAD  
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "sqlserver export -o HEAD:Points CommitedPoints" on the SQL Server database
     Then the response should contain "Points exported successfully to CommitedPoints"    
