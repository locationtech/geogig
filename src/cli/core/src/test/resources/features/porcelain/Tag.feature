Feature: "tag" command
	In order to store defined versions of my repository
	As a Geogig User
	I want to be create tags
	
  Scenario: List the available tags
  	Given I have a repository
  	  And I have several commits
  	  And I run the command "tag mytag -m msg"
	 When I run the command "tag"  	  
  	 Then the response should contain "mytag"

  Scenario: Create a new tag
  	Given I have a repository
  	  And I have several commits
  	 When I run the command "tag mytag -m msg"
  	 Then the response should contain "Created tag mytag ->"
      And the response should contain variable "{@ObjectId|localrepo|HEAD}"

  Scenario: Create a new tag for a given commit
  	Given I have a repository
  	  And I have several commits
  	 When I run the command "tag mytag HEAD^ -m msg"
  	 Then the response should contain "Created tag mytag ->"
      And the response should contain variable "{@ObjectId|localrepo|HEAD^}"
  	   	 
 Scenario: Delete a tag
  	Given I have a repository
  	  And I have several commits
  	  And I run the command "tag mytag -m msg"
  	 When I run the command "tag -d mytag"
  	 Then the response should contain "Deleted tag mytag"
  	 
 Scenario: Delete an inexistent tag
  	Given I have a repository
  	  And I have several commits
  	  And I run the command "tag mytag -m msg"
  	 When I run the command "tag -d wrongtag"
  	 Then the response should contain "Wrong tag name: wrongtag"  
  	  And it should exit with non-zero exit code	   	 

 Scenario: Try to create a tag with too many parameters provided
  	Given I have a repository
  	  And I have several commits  	  
  	 When I run the command "tag mytag HEAD^ extraparam -m msg"
  	 Then the response should contain "Too many parameters provided"  
  	  And it should exit with non-zero exit code
 
 Scenario: Try to delete a tag with too many parameters provided
  	Given I have a repository
  	  And I have several commits  	  
  	 When I run the command "tag -d mytag HEAD^"
  	 Then the response should contain "Too many parameters provided"  
  	  And it should exit with non-zero exit code
  	   	 
 Scenario: Try to create a tag with no message
  	Given I have a repository
  	  And I have several commits  	  
  	 When I run the command "tag mytag HEAD^"
  	 Then the response should contain "No tag message provided"
  	  And it should exit with non-zero exit code  
  	   	   	   	   	   	 
 Scenario: Try to create a tag with a wrong commit ref
  	Given I have a repository
  	  And I have several commits  	  
  	 When I run the command "tag mytag aaaaaaaa -m msg"
  	 Then the response should contain "Wrong reference: aaaaaaaa"
  	  And it should exit with non-zero exit code  
