Feature: "checkout" command
	In order to view an alternate version of the data
	As a Geogig User
	I want to be able to checkout out branches
	
  Scenario: Try to checkout a remote branch
    Given I have a repository with a remote
     When I run the command "fetch origin"
      And I run the command "checkout branch1"
     Then the response should contain "Branch 'branch1' was set up to track remote branch 'branch1' from origin"
      And the response should contain "Switched to a new branch 'branch1'"
