@Commands @Branch
Feature: Branch
  The branch command allows a user to create and list branches and is supported through the "/repos/{repository}/branch" endpoint
  The command must be executed using the HTTP GET method

  @Status405
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty repository named repo1
     When I call "PUT /repos/repo1/branch"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  @Status500
  Scenario: Calling branch without specifying list or a branch name issues a 500 status code
    Given There is an empty repository named repo1
     When I call "GET /repos/repo1/branch"
     Then the response status should be '500'
      And the xpath "/response/error/text()" contains "Nothing to do."
      
  Scenario: Calling branch with the list parameter lists all local branches
    Given There is an empty repository named repo1
      And There are multiple branches on the "repo1" repo
     When I call "GET /repos/repo1/branch?list=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Local/Branch" 3 times
      And the xml response should contain "/response/Remote/Branch" 0 times
      And there is an xpath "/response/Local/Branch/name/text()" that equals "master"
      And there is an xpath "/response/Local/Branch/name/text()" that equals "branch1"
      And there is an xpath "/response/Local/Branch/name/text()" that equals "branch2"
            
  Scenario: Calling branch with the list and remotes parameters lists all local and remote branches
    Given There is an empty repository named repo1
      And There are multiple branches on the "repo1" repo
     When I call "GET /repos/repo1/branch?list=true&remotes=true"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xml response should contain "/response/Local/Branch" 3 times
      And the xml response should contain "/response/Remote/Branch" 3 times
      And there is an xpath "/response/Local/Branch/name/text()" that equals "master"
      And there is an xpath "/response/Local/Branch/name/text()" that equals "branch1"
      And there is an xpath "/response/Local/Branch/name/text()" that equals "branch2"
      And there is an xpath "/response/Remote/Branch/remoteName/text()" that equals "origin"
      And there is an xpath "/response/Remote/Branch/name/text()" that equals "master_remote"
      And there is an xpath "/response/Remote/Branch/name/text()" that equals "branch1_remote"
      And there is an xpath "/response/Remote/Branch/name/text()" that equals "branch2_remote"
      And the response body should contain "branch2_remote"
      
  Scenario: Calling branch with a branch name creates a new branch
    Given There is a default multirepo server
     When I call "GET /repos/repo1/branch?branchName=new_branch"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/BranchCreated/name/text()" equals "new_branch"
      And the xpath "/response/BranchCreated/source/text()" equals "{@ObjectId|repo1|master}"
     When I call "GET /repos/repo1/repo/manifest"
     Then the response body should contain "new_branch"
     
  Scenario: Calling branch with a branch name and source creates a new branch from the source
    Given There is a default multirepo server
     When I call "GET /repos/repo1/branch?branchName=new_branch&source=branch1"
     Then the response status should be '200'
      And the xpath "/response/success/text()" equals "true"
      And the xpath "/response/BranchCreated/name/text()" equals "new_branch"
      And the xpath "/response/BranchCreated/source/text()" equals "{@ObjectId|repo1|branch1}"
      And the xml response should contain "/response/BranchCreated/source" 1 times
     When I call "GET /repos/repo1/repo/manifest"
     Then the response body should contain "new_branch"
     
  @Status400
  Scenario: Calling branch with a branch name that already exists issues a 400 status code
    Given There is a default multirepo server
     When I call "GET /repos/repo1/branch?branchName=branch1"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "A branch named 'branch1' already exists."
      
  @Status400
  Scenario: Calling branch with a source that does not exist issues a 400 status code
    Given There is a default multirepo server
     When I call "GET /repos/repo1/branch?branchName=new_branch&source=nonexistent"
     Then the response status should be '400'
      And the xpath "/response/error/text()" equals "nonexistent does not resolve to a repository object"