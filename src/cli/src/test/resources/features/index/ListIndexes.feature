Feature: "index update" command
    In order to see what indexes are available on the repository
    As a Geogig User
    I want to list all of the indexes

  Scenario: Try to list indexes
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index create --tree Lines --extra-attributes sp"
     Then the response should contain "Index created successfully"
     When I run the command "index list"
     Then the response should contain "Lines"
      And the response should contain "[sp]"
      And the response should contain "Points"
      And the response should contain "[ip]"
      
  Scenario: Try to list indexes in a specific feature type tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index create --tree Lines --extra-attributes sp"
     Then the response should contain "Index created successfully"
     When I run the command "index list --tree Points"
     Then the response should not contain "Lines"
      And the response should not contain "[sp]"
      And the response should contain "Points"
      And the response should contain "[ip]"