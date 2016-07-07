Feature: "sqlserver describe" command
    In order to understand the structure of a table in a SQL Server database
    As a Geogig User
    I want Geogig to describe the table

  Scenario: Try describing a SQL Server table from an empty directory
    Given I am in an empty directory
     When I run the command "sqlserver describe --table geogig_sqlserver_test" on the SQL Server database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try describing a SQL Server table
    Given I have a repository
     When I run the command "sqlserver describe --table geogig_sqlserver_test" on the SQL Server database
     Then the response should contain "Table : geogig_sqlserver_test"
     
  Scenario: Try to describe a SQL Server table that doesn't exit in the database
    Given I have a repository
     When I run the command "sqlserver describe --table nonexistant_table" on the SQL Server database
     Then the response should contain "Could not find the specified table."
