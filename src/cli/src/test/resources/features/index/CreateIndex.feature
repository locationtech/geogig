Feature: "index create" command
    In order to improve query performance on a feature tree
    As a Geogig User
    I want to create an index on an attribute of the feature tree

  Scenario: Try to create an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should not contain "Size: 2"
      And the response should contain the index ID for tree "Points"
      
  Scenario: Try to create an index with extra attributes
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes sp"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should not contain "Size: 2"
      And the response should contain the index ID for tree "Points"


  Scenario: Try to create an index on a nonexistent tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree nonexistent"
     Then the response should contain "Can't find feature tree 'nonexistent'"
     
  Scenario: Try to create an index on a nonexistent attribute
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --attribute nonexistent"
     Then the response should contain "property nonexistent does not exist"
     
  Scenario: Try to create an index on a non-geometry attribute
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --attribute sp"
     Then the response should contain "property sp is not a geometry attribute"
     
  Scenario: Try to create an index with the full history
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --index-history"
     Then the response should contain "Index created successfully"
      And the response should contain "Size: 3"
      And the response should contain "Size: 2"
      And the response should contain the index ID for tree "Points"

