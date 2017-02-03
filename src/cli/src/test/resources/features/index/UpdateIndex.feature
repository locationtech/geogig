Feature: "index update" command
    In order to modify the extra attributes stored on index trees
    As a Geogig User
    I want to update an index to change the extra attributes

  Scenario: Try to update an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"

  Scenario: Try to update a nonexistent index
    Given I have a repository
      And I have several commits
      And I run the command "index update --tree nonexistent"
     Then the response should contain "Can't find feature tree 'nonexistent'"
     
  Scenario: Try to update an index with the full history
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp --index-history"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain "Size: 2"
      And the response should contain the index ID for tree "Points"

  Scenario: Try to add attributes to an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp --add"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"

  Scenario: Try to replace attributes of an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp --overwrite"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      And the response should contain the index ID for tree "Points"

  Scenario: Try to change existing attributes without specifying add or overwrite
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Extra attributes already exist on index, specify add or overwrite to update."

  Scenario: I try to update the index without specifying a tree
    Given I have a repository
    And I have several commits
    And I run the command "index create --tree Points --extra-attributes ip"
    Then the response should contain "Index created successfully"
    When I run the command "index update --extra-attributes sp"
    Then the response should contain "The following option is required: --tree"

  Scenario: I try to update the index for a non-existent attribute
    Given I have a repository
    And I have several commits
    And I run the command "index create --tree Points --extra-attributes ip"
    Then the response should contain "Index created successfully"
    When I run the command "index update --tree Points --attribute fakeAttrib"
    Then the response should contain "property fakeAttrib does not exist"

  Scenario: I try to update the index leaving the extra-attribute param empty
    Given I have a repository
    And I have several commits
    And I run the command "index create --tree Points --extra-attributes ip"
    Then the response should contain "Index created successfully"
    When I run the command "index update --tree Points --extra-attributes"
    Then the response should contain "Expected a value after parameter --extra-attributes"

  Scenario: I try to update the index for a non-existent extra-attribute
    Given I have a repository
    And I have several commits
    And I run the command "index create --tree Points --extra-attributes ip"
    Then the response should contain "Index created successfully"
    When I run the command "index update --tree Points --extra-attributes fakeAttrib"
    Then the response should contain "FeatureType Points does not define attribute 'fakeAttrib'"

  Scenario: I try to overwrite a non-existent extra-attribute
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes fakeAttrib --overwrite"
     Then the response should contain "FeatureType Points does not define attribute 'fakeAttrib'"

  Scenario: I try to add a non-existent attribute to the index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Extra attributes already exist on index, specify add or overwrite"

  Scenario: I try to update the index for the full history when there is only one commit
    Given I have a repository
      And I have staged "points1"
      And I run the command "commit -m "point1 added"
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --index-history --overwrite"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 1"
      And the response should contain the index ID for tree "Points"

  Scenario: I try to update the index for the full history without specifying overwrite
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --index-history"
     Then the response should contain "Extra attributes already exist on index"
      And the response should contain "specify add or overwrite to update"

  Scenario: I try to update the index in an empty repository
    Given I have a repository
     When I run the command "index update --tree Points"
     Then the response should contain "Can't find feature tree 'Points'"
