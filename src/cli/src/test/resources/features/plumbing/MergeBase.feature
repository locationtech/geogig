Feature: "merge-base" command
    In order to know the common parent of 2 commits
    As a Geogig User
    I want to see the common ancestor of 2 commits before a merge
    
  Scenario: I try to view the common parent of 2 commits
      Given I have a repository
      And I have several branches
      And I run the command "merge branch1 -m MergeMessage"
     When I run the command "merge-base HEAD HEAD~2"
     Then the response should contain 1 lines
      And it should exit with zero exit code
     
  Scenario: I try to view the common parent with too many arguments
    Given I have a repository
      And I have several branches
      And I run the command "merge branch1 -m MergeMessage"
     When I run the command "merge-base commit1 commit2 commit3"
     Then the response should contain "Two commit references must be provided"
  
  Scenario: I try to view the parent using a bad left commit reference
      Given I have a repository
      And I have several branches
      And I run the command "merge branch1 -m MergeMessage"
     When I run the command "merge-base badCommit HEAD"
     Then the response should contain "badCommit does not resolve to any object"
     
  Scenario: I try to view the parent using a bad right commit reference
      Given I have a repository
      And I have several branches
      And I run the command "merge branch1 -m MergeMessage"
     When I run the command "merge-base HEAD badCommit"
     Then the response should contain "badCommit does not resolve to any object"
     
     