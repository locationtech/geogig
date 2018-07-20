Feature: "index update" command
    In order to modify the extra attributes stored on index trees
    As a Geogig User
    I want to update an index to change the extra attributes

  Scenario: Try to update an index
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 

  Scenario: Try to update a nonexistent index
    Given I have a repository
      And I have several commits
     When I run the command "index update --tree nonexistent"
     Then the response should contain "Can't find feature tree 'nonexistent'"
     
  Scenario: Try to update an index with the full history
    Given I have a repository
      And I have several branches
     When I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch1:Points" should not have an index
      And the repository's "branch2:Points" should not have an index
     When I run the command "index update --tree Points --extra-attributes sp --index-history"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "HEAD~1:Points" index should track the extra attribute "sp"
      And the repository's "HEAD~1:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD~1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch2:Points" index should track the extra attribute "sp"
      And the repository's "branch2:Points" index should not track the extra attribute "ip"
      And the repository's "branch2:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "branch1:Points" index should track the extra attribute "sp"
      And the repository's "branch1:Points" index should not track the extra attribute "ip"
      And the repository's "branch1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 

  Scenario: Try to add attributes to an index
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
     When I run the command "index update --tree Points --extra-attributes sp --add"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 

  Scenario: Try to replace attributes of an index
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
     When I run the command "index update --tree Points --extra-attributes sp --overwrite"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
          
  Scenario: Try to update the bounds of an index
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes sp --bounds -45,-45,45,45"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-45,-45,45,45"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
     When I run the command "index update --tree Points --bounds -20,-45,20,45"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-20,-45,20,45"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
          
  Scenario: Try to update the bounds of an index with incorrect bounds parameter
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes sp --bounds -45,-45,45,45"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index bounds should be "-45,-45,45,45"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
     When I run the command "index update --tree Points --bounds -20,-45,20"
     Then the response should contain "Invalid bbox parameter: '-20,-45,20'. Expected format: <minx,miny,maxx,maxy>"

  Scenario: Try to change existing attributes without specifying add or overwrite
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Extra attributes already exist on index, specify add or overwrite to update."

  Scenario: I try to update the index without specifying a tree
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --extra-attributes sp"
     Then the response should contain "The following option is required: --tree"

  Scenario: I try to update the index for a non-existent attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --attribute fakeAttrib"
     Then the response should contain "A matching index could not be found."

  Scenario: I try to update the index leaving the extra-attribute param empty
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes"
     Then the response should contain "Expected a value after parameter --extra-attributes"

  Scenario: I try to update the index for a non-existent extra-attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes fakeAttrib"
     Then the response should contain "FeatureType Points does not define attribute 'fakeAttrib'"

  Scenario: I try to overwrite a non-existent extra-attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes fakeAttrib --overwrite"
     Then the response should contain "FeatureType Points does not define attribute 'fakeAttrib'"

  Scenario: I try to update the index for the full history when there is only one commit
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m "point1 added"
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --index-history --overwrite"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 1"
      And the response should contain the index ID for tree "Points"
      And the repository's "HEAD:Points" index bounds should be "-90,-180,90,180"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 

  Scenario: I try to update the index without updating anything
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points"
     Then the response should contain "Nothing to update..."
     
  Scenario: I try to update the index with the same extra attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes ip --overwrite"
     Then the response should contain "Nothing to update..."
     
  Scenario: I try to update the index by adding the same extra attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes sp,ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes ip --add"
     Then the response should contain "Nothing to update..."

  Scenario: I try to update the index in an empty repository
    Given I have a repository
     When I run the command "index update --tree Points"
     Then the response should contain "Can't find feature tree 'Points'"
