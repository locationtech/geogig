Feature: "sqlserver import" command
    In order to import data to Geogig
    As a Geogig User
    I want to import one or more tables from a SQL Server database

  Scenario: Try importing into an empty directory
    Given I am in an empty directory
     When I run the command "sqlserver import --table geogig_sqlserver_test" on the SQL Server database
     Then the response should start with "Not in a geogig repository"
      
  Scenario: Try to import a SQL Server table
    Given I have a repository
     When I run the command "sqlserver import --table geogig_sqlserver_test" on the SQL Server database
     Then the response should contain "Import successful."

  Scenario: Try to import a full SQL Server database
    Given I have a repository
     When I run the command "sqlserver import --all" on the SQL Server database
     Then the response should contain "Import successful."
     
  Scenario: Try to import a SQL Server table that doesn't exit in the database
    Given I have a repository
     When I run the command "sqlserver import --table nonexistant_table" on the SQL Server database
     Then the response should contain "Could not find the specified table."
     
  Scenario: Try to import without specifying table or -all
    Given I have a repository
     When I run the command "sqlserver import" on the SQL Server database
     Then the response should contain "No tables specified for import. Specify --all or --table <table>."     
     
  Scenario: Try to import with table and -all
    Given I have a repository
     When I run the command "sqlserver import --table geogig_sqlserver_test --all" on the SQL Server database
     Then the response should contain "Specify --all or --table <table>, both cannot be set."       