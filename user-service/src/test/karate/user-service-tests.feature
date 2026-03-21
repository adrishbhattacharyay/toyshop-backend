@ignore
Feature: User Service API Tests

  Background:
    * url baseUrl
    * header Content-Type = 'application/json'

  Scenario: Register a new user
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "karateuser",
        "email": "karate@example.com",
        "password": "Password123!",
        "firstName": "Karate",
        "lastName": "User"
      }
      """
    When method POST
    Then status 201
    And match response.username == 'karateuser'
    And match response.email == 'karate@example.com'
    And match response.id == '#notnull'

  Scenario: Login with valid credentials
    Given path '/api/v1/auth/login'
    And request
      """
      {
        "usernameOrEmail": "testuser",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 200
    And match response.accessToken == '#notnull'
    And match response.tokenType == 'Bearer'
    And match response.expiresIn > 0

  Scenario: Login with invalid credentials
    Given path '/api/v1/auth/login'
    And request
      """
      {
        "usernameOrEmail": "testuser",
        "password": "WrongPassword123!"
      }
      """
    When method POST
    Then status 401

  Scenario: Get current user profile
    Given path '/api/v1/users/me'
    And header Authorization = 'Bearer ' + token
    When method GET
    Then status 200
    And match response.username == '#notnull'

  Scenario: Get all users (admin only)
    Given path '/api/v1/users'
    And header Authorization = 'Bearer ' + adminToken
    When method GET
    Then status 200
    And match response == '#array'

  Scenario: Update user profile
    Given path '/api/v1/users/me'
    And header Authorization = 'Bearer ' + token
    And request
      """
      {
        "firstName": "Updated",
        "lastName": "Name"
      }
      """
    When method PUT
    Then status 200
    And match response.firstName == 'Updated'
    And match response.lastName == 'Name'

  Scenario: Register with invalid email
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "invaliduser",
        "email": "not-an-email",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 400

  Scenario: Register with weak password
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "weakuser",
        "email": "weak@example.com",
        "password": "123"
      }
      """
    When method POST
    Then status 400

  Scenario: Register with existing email
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "newuser",
        "email": "existing@example.com",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 409

  Scenario: Access protected resource without token
    Given path '/api/v1/users/me'
    When method GET
    Then status 401