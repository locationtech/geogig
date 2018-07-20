Feature: "clean" command
    In order to remove unwanted changes
    As a Geogig User
    I want to remove all untracked features

  Scenario: Try to remove all the untracked features
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean"
     And  I run the command "status"
     Then the response should not contain "Points/Points.1"

  Scenario: Try to remove all the untracked features in a tree
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean Points"
     And I run the command "status"
     Then the response should not contain "Removing Points/Points.1"
     Then the response should contain "Lines/Lines.1"
     
  Scenario: Try to remove all the untracked features in a tree that does not exist
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean fakeTree"
     Then the response should contain "did not match any tree"
      And it should exit with non-zero exit code  
       
  Scenario: Try to remove all the untracked features in a path that is not a tree
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean Points/Points.1"
     Then the response should contain "did not resolve to a tree"
      And it should exit with non-zero exit code         
      
  Scenario: Try to know which untracked features would be removed in a non-existent tree
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean -n fake/tree"
     Then the response should contain "pathspec 'fake/tree' did not match any tree"
     
  Scenario: Try to know which untracked features would be removed
    Given I have a repository
      And I have unstaged "points1"
      And I have unstaged "points2"
      And I have unstaged "lines1"
     When I run the command "clean -n"
     Then the response should contain "Would remove Points/Points.1"
     