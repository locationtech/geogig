Feature: "ls-tree" command
    In order to know what is in a repository
    As a Geogig User
    I want to list the feature in the working tree 

Scenario: Show a list of features in the root tree recursively
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree -r"
     Then the response should contain "Points/Points.1"
     Then the response should contain "Lines/Lines.1"   
     
Scenario: Show a list of features in the root tree recursively including trees
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree -r -t -v"
     Then the response should contain "Points/Points.1"
     Then the response should contain "Lines/Lines.1"     
     Then the response should contain "tree"              
     
Scenario: Show a list of features in the root tree non-recursively
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree"
     Then the response should not contain "tree"
     Then the response should not contain "Points/Points.1"
      And the response should contain "Points"
      And the response should contain "Lines"
     Then the response should not contain "Points/Points.1"
      And the response should not contain variable "{@PointsTypeID}"

Scenario: Show a verbose list of trees in the root tree non-recursively
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree -t -v"
     Then the response should contain "tree"
      And the response should contain "Points 1.0;2.0;1.0;2.0 2"
      And the response should contain "Lines 1.0;2.0;1.0;2.0 1 0"
      And the response should not contain "Points/Points.1"             
      And the response should not contain "Lines/Lines.1"

Scenario: Show a verbose list of features in the root tree recursively, not including children
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree -d -v"
     Then the response should contain "tree"
     Then the response should not contain "Points/Points.1"           
      And the response should contain "Points 1.0"
      And the response should contain "Lines 1.0"

Scenario: Show a verbose list of features in a path
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree -v Points"
     Then the response should not contain "tree"
     Then the response should contain "Points.1"
      And the response should contain variable "{@PointsTypeID}"
     
Scenario: Show a list of features using STAGE_HEAD as non-recursively, including trees
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
     When I run the command "ls-tree STAGE_HEAD -t -v"
	 Then the response should contain "tree"
      And the response should contain "Points 1.0;2.0;1.0;2.0 2"
      And the response should contain "Lines 1.0;2.0;1.0;2.0 1 0"
      And the response should not contain "Points/Points.1"

Scenario: Show a list of trees using HEAD as origin, recursively
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
      And I run the command "commit -m Test"
     When I run the command "ls-tree HEAD -t"
     Then the response should contain "Points"
     Then the response should contain "Lines"  
     Then the response should not contain "Points/Points.1" 

Scenario: Show a verbose list of features using HEAD as origin, recursively
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
      And I run the command "commit -m Test"
     When I run the command "ls-tree HEAD -r -v"
     Then the response should contain "Points/Points.1"
      And the response should contain variable "{@PointsTypeID}"
     Then the response should contain "Lines/Lines.1"
      And the response should contain variable "{@LinesTypeID}"
     Then the response should not contain "tree"
      And the response should contain 3 lines
     
Scenario: Show a list of features in a path, using HEAD as origin
    Given I have a repository
      And I have staged "points1"
      And I have staged "points2"
      And I have staged "lines1"
      And I run the command "commit -m Test"
     When I run the command "ls-tree HEAD:Points"
     Then the response should contain "Points.1"
     Then the response should not contain "Lines.1"

Scenario: Show a list from an empty directory
    Given I am in an empty directory
     When I run the command "ls-tree"
     Then the response should start with "Not in a geogig repository"
      And it should exit with non-zero exit code

Scenario: Run ls-tree on an empty repository
    Given I have a repository
     When I run the command "ls-tree -r"
     Then it should answer ""
         