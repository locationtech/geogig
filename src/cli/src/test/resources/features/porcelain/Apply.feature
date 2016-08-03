Feature: "apply" command
    In order to apply changes stored in a patch file
    As a Geogig User
    I want to apply a patch to the repository

Scenario: Check if a correct patch can be applied
    Given I have a repository
      And I have 6 unstaged features
      And I have a patch file
     When I run the command "apply --check ${currentdir}/test.patch"
     Then the response should contain "Patch can be applied"
     
Scenario: Check if a wrong patch can be applied
    Given I have a repository
      And I have 6 unstaged features
      And I modify a feature
      And I have a patch file
     When I run the command "apply --check ${currentdir}/test.patch"
     Then the response should contain "Patch cannot be applied"     
     
Scenario: Check if a patch can be applied twice
    Given I have a repository
      And I have 6 unstaged features
      And I have a patch file
     When I run the command "apply ${currentdir}/test.patch"
      And I run the command "apply --check ${currentdir}/test.patch"
     Then the response should contain "Patch can be applied"       
     
Scenario: Apply a correct patch
    Given I have a repository
      And I have 6 unstaged features
      And I have a patch file
     When I run the command "apply ${currentdir}/test.patch"
     Then the response should contain "Patch applied succesfully"
     
Scenario: Try to apply a wrong patch
    Given I have a repository
      And I have 6 unstaged features
      And I modify a feature
      And I have a patch file
     When I run the command "apply ${currentdir}/test.patch"
     Then the response should contain "Error: Patch cannot be applied"
      And the response should contain "Points/Points.1" 
      And it should exit with non-zero exit code

Scenario: Try to partially apply a wrong patch
    Given I have a repository
      And I have 6 unstaged features
      And I modify a feature
      And I have a patch file
     When I run the command "apply --reject ${currentdir}/test.patch"
     Then the response should contain "Patch applied only partially"
      And the response should contain "0 changes were applied"
      And the response should contain "1 changes were rejected"  
      And it should exit with non-zero exit code     
            
Scenario: Try to apply an inexistent patch
    Given I have a repository
      And I have 6 unstaged features      
     When I run the command "apply ${currentdir}/test.patch"
     Then the response should contain "Patch file cannot be found"  
     And it should exit with non-zero exit code    
          
Scenario: List the content of a patch
    Given I have a repository
      And I have 6 unstaged features
      And I have a patch file
     When I run the command "apply --summary ${currentdir}/test.patch"
     Then the response should contain "Points/Points.1"      
     
Scenario: Apply a reversed patch that cannot be applied
  Given I have a repository
      And I have unstaged "points1_modified"
      And I have a patch file
     When I run the command "apply --reverse ${currentdir}/test.patch"
     Then the response should contain "Conflicting"   
     And it should exit with non-zero exit code   
     
Scenario: Apply a path and then the reverse version of that patch
	Given I have a repository
      And I have 6 unstaged features
      And I have a patch file
     When I run the command "apply ${currentdir}/test.patch"
      And I run the command "apply --reverse ${currentdir}/test.patch"
     Then the response should contain "Patch applied succesfully"      