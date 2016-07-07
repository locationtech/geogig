@GeopackageSupport
Feature: Export GeoPackage
  The GeoPackage export Web-API allows for downloading a repository snapshot or a subset of it as a GeoPackage file.
  It is possible to filter out the downloaded content indicating the names of the layers to include from a given
  root tree, and to specify a bounding box to filter out the included features.
  Also, a flag can be passed as argument to indicate the generated geopackage shall include the Geogig GeoPackage
  extension, allowing to transparently log every change to a tracked layer in an audit table that can later be
  replyed on top of the repository.  
  
  API Spec: GET /repos/<repo>/export[.xml|.json]?format=gpkg[&root=<refspec>][&path=<layerName>[,<layerName>]+][&bbox=<boundingBox>][&interchange=<true|false>]
  
  Scenario: Verify wrong HTTP method issues 405 "Method not allowed"
    Given There is an empty multirepo server
     When I call "POST /repos/repo1/export?format=gpkg"
     Then the response status should be '405'
      And the response allowed methods should be "GET"
      
  Scenario: Verify missing "format=gpkg" argument issues 400 "Bad request"
    Given There is a default multirepo server
     When I call "GET /repos/repo1/export"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" contains "output format not provided"

  Scenario: Verify unsupported output format argument issues 400 "Bad request"
    Given There is a default multirepo server
     When I call "GET /repos/repo1/export?format=badFormat"
     Then the response status should be '400'
      And the response ContentType should be "application/xml"
      And the xpath "/response/success/text()" equals "false"
      And the xpath "/response/error/text()" contains "Unsupported output format"

  Scenario: Verify export on a non existent repository issues 404 "Not found"
    Given There is an empty multirepo server
     When I call "GET /repos/badRepo/export?format=gpkg"
     Then the response status should be '404'
      And the response ContentType should be "text/plain"
      And the response body should contain "Repository not found"

  Scenario: Export defaults: all layers from current head
    Given There is a default multirepo server
     When I call "GET /repos/repo1/export?format=gpkg"
     Then the response is an XML async task @taskId
      And the task @taskId description contains "Export to Geopackage database"
      And when the task @taskId finishes
     Then the task @taskId status is FINISHED
      And the task @taskId result contains "atom:link/@href" with value "/tasks/{@taskId}/download"
     When I call "GET /tasks/{@taskId}/download"
     Then the result is a valid GeoPackage file
