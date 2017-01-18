Feature: "diff-tree" command
    In order to know changes made in a repository
    As a Geogig User
    I want to see the existing differences between trees 
  
Scenario: Show diff between working tree and index
    Given I have a repository
      And I stage 6 features      
      And I modify a feature
     When I run the command "diff-tree WORK_HEAD STAGE_HEAD"
     Then the response should contain "Points/Points.1"       
      And the response should contain 1 lines
      
Scenario: Show diff between working tree and index, omitting index refspec
    Given I have a repository
      And I stage 6 features      
      And I modify a feature
     When I run the command "diff-tree WORK_HEAD"
     Then the response should contain "Points/Points.1"       
      And the response should contain 1 lines
      And the response should contain variable "{@ObjectId|localrepo|WORK_HEAD:Points/Points.1}"
      
Scenario: Show diff between working tree and index, omitting both refspecs
    Given I have a repository
      And I stage 6 features      
      And I modify a featuregit 
     When I run the command "diff-tree"
     Then the response should contain "Points/Points.1"       
      And the response should contain 1 lines   
      
Scenario: Show diff tree stats between working tree and index
    Given I have a repository
      And I stage 6 features
      And I modify a feature      
     When I run the command "diff-tree --tree-stats"
     Then the response should contain "Points 0 0 1"                
            
Scenario: Show diff using too many commit refspecs
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff-tree refspec1 refspec2 refspec3"
	 Then the response should contain "Tree refspecs list is too long"  
	  And it should exit with non-zero exit code 
	 
Scenario: Show diff using a wrong  refspec
    Given I have a repository
      And I stage 6 features   
      And I modify a feature         
     When I run the command "diff-tree wrong:refspec"
	 Then the response should contain "wrong:refspec did not resolve to a tree"
	  And it should exit with non-zero exit code  	 

Scenario: Show diff between working tree and index, describing the modified element
    Given I have a repository
      And I stage 6 features         
      And I modify a feature             
     When I run the command "diff-tree WORK_HEAD STAGE_HEAD --describe"
     Then the response should contain 10 lines
      And the response should contain "Points/Points.1"

Scenario: Show diff between working tree and index, with a change in the feature type
    Given I have a repository
      And I stage 6 features  
      And I a featuretype is modified
     When I run the command "diff-tree WORK_HEAD:Points STAGE_HEAD:Points"
     Then the response should contain 3 lines   
      And the response should contain "Points.1"
      And the response should contain "Points.2"
      And the response should contain "Points.3"
      And the response should contain variable "{@ObjectId|localrepo|WORK_HEAD:Points/Points.1}"
      And the response should contain variable "{@ObjectId|localrepo|STAGE_HEAD:Points/Points.1}"

  Scenario: Show diff between working tree and index, using a path filter
    Given I have a repository
      And I stage 6 features         
      And I modify a feature             
     When I run the command "diff-tree WORK_HEAD:Points STAGE_HEAD:Points --path Points.1"
     Then the response should contain "Points.1"
      And the response should not contain "Points/Points.1"   
      And the response should contain 1 lines
      And the response should contain variable "{@ObjectId|localrepo|WORK_HEAD:Points/Points.1}"
      And the response should contain variable "{@ObjectId|localrepo|STAGE_HEAD:Points/Points.1}"
     
Scenario: Try to show a diff with --describe and --tree-stats
    Given I have a repository
      And I stage 6 features
      And I modify a feature
     When I run the command "diff-tree WORK_HEAD STAGE_HEAD --describe --tree-stats"
     Then the response should contain "Cannot use --describe and --tree-stats simultaneously"
     
Scenario: Show diff between working tree and index, describing a removed element
    Given I have a repository
      And I stage 6 features         
      And I remove a feature             
     When I run the command "diff-tree WORK_HEAD STAGE_HEAD --describe"
     Then the response should contain "Points/Points.1"
      And the response should contain 7 lines
