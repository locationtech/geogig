Feature: "show" command
    In order to know about a given element
    As a Geogig User
    I want to display information about it

Scenario: Try to show the description of a feature using only its path
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show Points/Points.1"
     Then the response should contain "ATTRIBUTES"
      And the response should contain "FEATURE TYPE ID"
      And the response should contain "sp"
      And the response should contain "pp"
      And the response should contain "ip"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points/Points.1}"
      And the response should contain variable "{@PointsTypeID}"

  Scenario: Try to show the description of a commit
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show HEAD"
     Then the response should contain "Commit"
      And the response should contain "Author"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Try to show the description of a tree
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show HEAD:Points"
     Then the response should contain "TREE ID"
      And the response should contain "DEFAULT FEATURE TYPE ATTRIBUTES"
      And the response should contain "sp"
      And the response should contain "pp"
      And the response should contain "ip"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points}"
      And the response should contain variable "{@PointsTypeID}"

  Scenario: Try to show the description of a feature
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show HEAD:Points/Points.1"
     Then the response should contain "ATTRIBUTES"
      And the response should contain "FEATURE TYPE ID"
      And the response should contain "sp"
      And the response should contain "pp"
      And the response should contain "ip"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points/Points.1}"
      And the response should contain variable "{@PointsTypeID}"

  Scenario: Try to show the description of a feature using its SHA-1
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show a47ca38e5c3e92c94dec9e8ea597c642003ec878"     
     Then the response should contain "FEATURE"
      And the response should contain "STRING"
      And the response should contain "INTEGER"
      And the response should contain "POINT"
      And the response should contain variable "{@ObjectId|localrepo|a47ca38e5c3e92c94dec9e8ea597c642003ec878}"

  Scenario: Try to show the description of a feature with the --raw modifier
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show HEAD:Points/Points.1 --raw"          
     Then the response should contain "STRING"
      And the response should contain "INTEGER"
      And the response should contain "POINT urn:ogc:def:crs:EPSG::4326"
      And the response should contain "sp"
      And the response should contain "pp"
      And the response should contain "ip"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points/Points.1}"

  Scenario: Try to show the description of a 2 features with the --raw modifier
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show HEAD:Points/Points.1 HEAD:Points/Points.2 --raw"          
      And the response should contain "HEAD:Points/Points.1"
      And the response should contain "HEAD:Points/Points.2"
      And the response should contain "STRING"
      And the response should contain "INTEGER"
      And the response should contain "POINT"
      And the response should contain "sp"
      And the response should contain "pp"
      And the response should contain "ip"
      And the response should contain variable "{@ObjectId|localrepo|HEAD:Points/Points.1}"

  Scenario: Try to show the description of a featuretype
    Given I have a repository
      And I stage 6 features
      And I run the command "commit -m TestCommit"
     When I run the command "show Points"          
     Then the response should contain "TREE ID"
