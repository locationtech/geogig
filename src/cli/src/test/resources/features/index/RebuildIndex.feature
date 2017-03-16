Feature: "index rebuild" command
    In order to improve query performance on old commits
    As a Geogig User
    I want to build indexes on all commits in the history of a feature tree

  Scenario: Try to rebuild an index
    Given I have a repository
      And I have several branches
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index should not track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch2:Points" should not have an index
      And the repository's "branch1:Points" should not have an index
     When I run the command "index rebuild --tree Points"
     Then the response should contain "3 trees were rebuilt."
      And the response should contain "Size: 3"
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

  Scenario: Try to rebuild a nonexistent index
    Given I have a repository
      And I have several commits
      And I run the command "index rebuild --tree Points"
     Then the response should contain "A matching index could not be found."

  Scenario: I rebuild the index for an attribute on a tree
    Given I have a repository
      And I have several branches
      And I run the command "index create --tree Points --extra-attributes sp"
     Then the response should contain "Index created successfully"
      And the repository's "HEAD:Points" index should track the extra attribute "sp"
      And the repository's "HEAD:Points" index should not track the extra attribute "ip"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
      And the repository's "branch2:Points" should not have an index
      And the repository's "branch1:Points" should not have an index
     When I run the command "index rebuild --tree Points -a pp"
     Then the response should contain "3 trees were rebuilt."
      And the response should contain "Size: 3"
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

  Scenario: I try to rebuild the index for a non-existent attribute on a tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index rebuild --tree Points -a fakeAttrib"
     Then the response should contain "A matching index could not be found."

  Scenario: I try to rebuild the index for an attribute on a non-existent tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index rebuild --tree wrongTree -a pp"
     Then the response should contain "Can't find feature tree"

  Scenario: I try to rebuild the index for a non-existent attribute and tree
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index rebuild --tree fakeTree -a fakeAttrib"
     Then the response should contain "Can't find feature tree"

  Scenario: I try to rebuild the index without specifying the tree param
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
     When I run the command "index rebuild"
     Then the response should contain "The following option is required: --tree"

  Scenario: I try to rebuild the index on an empty repository
    Given I have a repository
     When I run the command "index rebuild --tree Points"
     Then the response should contain "Can't find feature tree"
