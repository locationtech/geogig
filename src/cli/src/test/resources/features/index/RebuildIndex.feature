Feature: "index rebuild" command
    In order to improve query performance on old commits
    As a Geogig User
    I want to build indexes on all commits in the history of a feature tree

  Scenario: Try to rebuild an index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index rebuild --tree Points"
     Then the response should contain "3 trees were rebuilt."
      And the response should contain "Size: 3"

  Scenario: Try to rebuild a nonexistent index
    Given I have a repository
      And I have several commits
      And I run the command "index rebuild --tree Points"
     Then the response should contain "a matching index could not be found"