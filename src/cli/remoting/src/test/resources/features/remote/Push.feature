Feature: "push" command
    In order to share my changes with a remote repository
    As a Geogig User
    I want to push my commits to that remote
     
  Scenario: Try to push from an empty directory
    Given I am in an empty directory
     When I run the command "push origin"
     Then the response should start with "Not in a geogig repository"

  Scenario: Try to push with no changes
    Given I clone a remote repository
     When I run the command "push"
     Then the response should contain "Nothing to push."

  Scenario: Try to push to origin
    Given I clone a remote repository
     When I modify and add a feature
      And I run the command "commit -m Commit6"
     Then the response should contain "Committed, counting objects"
      And I run the command "push"
     Then the response should contain "Saving missing revision objects changes for refs/heads/master"
     
  Scenario: Try to push a symbolic reference
    Given I clone a remote repository
     When I modify and add a feature
      And I run the command "commit -m Commit6"
     Then the response should contain "Committed, counting objects"
      And I run the command "push origin HEAD"
     Then it should answer "Push failed: Cannot push to a symbolic reference"     
     
  Scenario: Try to push when the remote has changes
    Given I clone a remote repository
      And the remote repository has changes
      And I modify and add a feature
     When I run the command "commit -m modified"
     Then the response should contain "1 changed"
     When I run the command "push"
     Then the response should contain "Push failed: The remote repository has changes"
  