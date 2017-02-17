Feature: "index create" command
    In order to improve query performance on a feature tree
    As a Geogig User
    I want to create an index on an attribute of the feature tree

  Scenario: Try to create an index
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should not contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "HEAD~1:Points" should not have an index
      And the repository's "HEAD~2:Points" should not have an index
      And the repository's "HEAD~3:Points" should not have an index

  Scenario: Try to create an index with extra attributes
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes sp"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should not contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "HEAD~1:Points" should not have an index
      And the repository's "HEAD~2:Points" should not have an index
      And the repository's "HEAD~3:Points" should not have an index
      
  Scenario: Try to create an index with user-specified bounds
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --bounds -45,-45,45,45"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should not contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-45,-45,45,45"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "HEAD~1:Points" should not have an index
      And the repository's "HEAD~2:Points" should not have an index
      And the repository's "HEAD~3:Points" should not have an index
      
  Scenario: Try to create an index with too few bounds parameters
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --bounds -45,-45,45"
     Then the response should contain "Invalid bbox parameter: '-45,-45,45'. Expected format: <minx,miny,maxx,maxy>"
     
  Scenario: Try to create an index with invalid bounds parameters
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --bounds -45,-45,45,A"
     Then the response should contain "Invalid bbox parameter: '-45,-45,45,A'. Expected format: <minx,miny,maxx,maxy>"

  Scenario: Try to create an index on a nonexistent tree
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree nonexistent"
     Then the response should contain "Can't find feature tree 'nonexistent'"
     
  Scenario: Try to create an index on a nonexistent attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --attribute nonexistent"
     Then the response should contain "property nonexistent does not exist"
     
  Scenario: Try to create an index on a non-geometry attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --attribute sp"
     Then the response should contain "property sp is not a geometry attribute"
     
  Scenario: Try to create an index with the full history
    Given I have a repository
      And I have several branches
     When I run the command "index create --tree Points --index-history"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "HEAD~1:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD~1:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD~1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch2:Points" index should not track the extra attribute "sp"
      And the repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repository's "branch2:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "branch1:Points" index should not track the extra attribute "sp"
      And the repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repository's "branch1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
          
  Scenario: Try to create an index with the full history and extra attributes
    Given I have a repository
      And I have several branches
     When I run the command "index create --tree Points --extra-attributes sp,ip --index-history"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "HEAD~1:Points" index should track the extra attribute "sp"
      And the repository's "HEAD~1:Points" index should track the extra attribute "ip"
      And the repository's "HEAD~1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch2:Points" index should track the extra attribute "sp"
      And the repository's "branch2:Points" index should track the extra attribute "ip"
      And the repository's "branch2:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "branch1:Points" index should track the extra attribute "sp"
      And the repository's "branch1:Points" index should track the extra attribute "ip"
      And the repository's "branch1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 

  Scenario: Try to create an index without specifying a tree
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree"
     Then the response should contain "Expected a value after parameter"
     
  Scenario: Try to create a full history index on an empty repository
    Given I have a repository
     When I run the command "index create --tree Points"
     Then the response should contain "Can't find feature tree"

  Scenario: Try to create a full history index on a repository with a single commit
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m "point1 added"
     When I run the command "index create --tree Points --index-history"
     Then the response should contain "Index updated"
      And the response should contain "Size: 1"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 

  Scenario: Try to create an index with an incorrect extra attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes invalidAttrib"
     Then the response should contain "FeatureType Points does not define attribute"

  Scenario: Try to create an index on a tree with an empty extra-attribute param
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes"
     Then the response should contain "Expected a value after parameter"
