Feature: "index drop" command
    In order to remove an unnecessary index
    As a Geogig User
    I want to drop an index from the repository

  Scenario: Try to drop an index
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
     When I run the command "index drop --tree Points"
     Then the response should contain "Index successfully dropped."
      And the repository's "HEAD:Points" should not have an index

  Scenario: Try to drop a nonexistent index
    Given I have a repository
      And I have several commits
     When I run the command "index drop --tree nonexistent"
     Then the response should contain "Can't find feature tree 'nonexistent'"

  Scenario: I try to drop the index without specifying a tree
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index drop"
     Then the response should contain "The following option is required: --tree"

  Scenario: I try to drop the index for a non-existent attribute
    Given I have a repository
      And I have several commits
     When I run the command "index create --tree Points --extra-attributes ip"
     Then the response should contain "Index created successfully"
     When I run the command "index drop --tree Points --attribute fakeAttrib"
     Then the response should contain "A matching index could not be found."

  Scenario: I try to drop an index in an empty repository
    Given I have a repository
     When I run the command "index drop --tree Points"
     Then the response should contain "Can't find feature tree 'Points'"
