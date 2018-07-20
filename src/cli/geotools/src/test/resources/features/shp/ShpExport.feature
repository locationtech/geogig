Feature: "shp export" command
    In order to export data to Geogig
    As a Geogig User
    I want to export from the repository to a shapefile

  Scenario: Try exporting from an empty directory
    Given I am in an empty directory
     When I run the command "shp export Points Points.shp" 
     Then the response should start with "Not in a geogig repository"
     
  Scenario: Try exporting a feature type
    Given I have a repository
      And I stage 6 features
     When I run the command "shp export -o Points Points.shp"
     Then the response should contain "Points exported successfully to Points.shp"
     
  Scenario: Try exporting an inexistent feature type
  	Given I have a repository
      And I stage 6 features
     When I run the command "shp export WRONGTABLE Points.shp" 
     Then the response should contain "pathspec 'WRONGTABLE' did not match any valid path"       
  
  Scenario: Try exporting with mixed feature types
  	Given I have a repository
      And I have several feature types in a path
     When I run the command "shp export -o Points Points.shp" 
     Then the response should contain "The selected tree contains mixed feature types"
      And the response should contain "Use --defaulttype or --featuretype <feature_type_ref> to export"
     
  Scenario: Try exporting with mixed feature types using the default feature type
  	Given I have a repository
      And I have several feature types in a path
     When I run the command "shp export -o --defaulttype Points Points.shp" 
     Then the response should contain "Points exported successfully to Points.shp"   
     
  Scenario: Try exporting with mixed feature types using --alter
  	Given I have a repository
      And I have several feature types in a path
     When I run the command "shp export -o --defaulttype Points Points.shp" 
     Then the response should contain "Points exported successfully to Points.shp"        
     
  Scenario: Try exporting a table from HEAD  
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "shp export -o HEAD:Points CommitedPoints.shp"
     Then the response should contain "Points exported successfully to CommitedPoints.shp"
