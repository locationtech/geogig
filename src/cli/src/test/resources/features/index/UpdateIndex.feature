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
      
  Scenario: Try to add attributes to an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp --add"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      
  Scenario: Try to replace attributes of an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp --overwrite"
     Then the response should contain "Index updated successfully"
      And the response should contain "Size: 3"
      
  Scenario: Try to change existing attributes without specifying add or overwrite
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index update --tree Points --extra-attributes sp"
     Then the response should contain "Extra attributes already exist on index, specify add or overwrite to update."