Feature: Automatic index updating
    In order to keep an index up to date with the canonical tree
    As a Geogig User
    Whenever I make changes to a repository, the index should be automatically updated

  Scenario: Committing a modified indexed feature updates the indexes
    Given I have a repository
      And I have staged "points1"
     When I run the command "commit -m noIndex"
      And the response should contain "1 features added"
      And the response should not contain "Updated index"
     When I run the command "index create --tree Points"
     Then the response should contain "Index created successfully"
      And I have staged "points1_modified"
     When I run the command "commit -m withIndex"
     Then the response should contain "Updated index"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          
  Scenario: Merging updates the index
    Given I have a repository
      And I have several branches
      And I run the command "index create --tree Points"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
     When I run the command "merge branch1 -m MergeMessage"
     Then the response should contain "2 features added"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
          
  Scenario: Rebasing a branch generates indexes for each rebased commit
    Given I have a repository
      And I have several branches
      And I run the command "index create --tree Points"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
     When I run the command "rebase master branch1"
     Then the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          |    Points.3   | 
      And the repository's "HEAD~1:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
          
  Scenario: Cherry-picking a commit updates the index
    Given I have a repository
      And I have several branches
      And I run the command "index create --tree Points"
      And the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
     When I run the command "cherry-pick branch1"
     Then the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.3   | 
          
  Scenario: Creating a branch updates the index
    Given I have a repository
      And I have several commits
      And I run the command "index create --tree Points"
     Then the repository's "HEAD:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   |
          |    Points.3   |
      And the repository's "HEAD~3" should not have an index
     When I run the command "branch newBranch HEAD~3"
     Then the repository's "HEAD~3:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 
      And the repository's "newBranch:Points" index should have the following features:
          |     index     | 
          |    Points.1   | 
          |    Points.2   | 