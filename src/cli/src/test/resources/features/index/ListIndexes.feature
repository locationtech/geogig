Feature: "index list" command
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
      And the response should contain index info ID for tree "Lines"
      And the response should contain "Points"
      And the response should contain "[ip]"
      And the response should contain index info ID for tree "Points"


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
      And the response should contain index info ID for tree "Points"

  Scenario: I try to list the indexes without specifying a tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index list --tree"
     Then the response should contain "Expected a value after parameter --tree"

  Scenario: I try to list the indexes, specifying a non-existent tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index list --tree fakeTree"
     Then the response should contain ""

  Scenario: I try to list the indexes when there are none
    Given I have a repository
      And I have several commits
     When I run the command "index list"
     Then the response should contain ""

  Scenario: I try to list the indexes in an empty repository
    Given I have a repository
     When I run the command "index list"
     Then the response should contain ""
