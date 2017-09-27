Web API: Issues
###############

- The main concern with the web API currently is that it doesn't have any kind of authentication on it, which means that anyone with the url can potentially compromise the integrity of the repo using various endpoints.

- There is also some inconsistencies in parameter names across different endpoints.  Parameters that refer to the same thing are named differently in some endpoints.  Some endpoints also use camel case while others are all lowercase.

- Most endpoints use the ``GET`` request method for every operation when other methods would be more appropriate.

- Most endpoints return a ``200`` status code when other status codes would be more appropriate.
