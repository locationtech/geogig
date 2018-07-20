Feature: "cat" command
    In order to know the content of a given element
    As a Geogig User
    I want to display its content

Scenario: Try to show the content of a tree.
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "cat HEAD:Points"
     Then the response should contain "Points.1"
      And the response should contain "Points.2"
      And the response should contain "Points.3"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points}"
     
Scenario: Try to show the content of a feature.
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "cat HEAD:Points/Points.1"
     Then the response should contain "1000"
      And the response should contain "POINT (1 1)"
      And the response should contain "StringProp1_1"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points/Points.1}"
   
Scenario: Try to show the content of a feature that does not exist
    Given I have a repository           
     When I run the command "cat WORK_HEAD:Points/Points.1"     
     Then it should exit with non-zero exit code
      And the response should contain "refspec did not resolve to any object"

Scenario: Try to show the content of a feature in the working tree.
    Given I have a repository
      And I have 6 unstaged features
     When I run the command "cat WORK_HEAD:Points/Points.1"
     Then the response should contain "1000"
      And the response should contain "POINT (1 1)"
      And the response should contain "StringProp1_1"
      And the response should contain variable "{@ObjectId|localrepo|WORK_HEAD:Points/Points.1}"
           
Scenario: Try to show the content of HEAD.
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "cat HEAD"
     Then the response should contain "COMMIT"
      And the response should contain "JohnDoe@example.com"
      And the response should contain "TestCommit"

Scenario: Try show the binary content of HEAD
    Given I have a repository
      And I stage 6 features
     When I run the command "commit -m TestCommit"
     When I run the command "cat --binary HEAD"
     Then the response should contain 1 lines
