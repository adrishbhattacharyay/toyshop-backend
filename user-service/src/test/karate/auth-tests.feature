Feature: User Service Authentication API Tests

  Background:
    * url baseUrl
    * def authToken = null

  Scenario: Register a new user successfully
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "karatetestuser",
        "email": "karatetest@example.com",
        "password": "Password123!",
        "firstName": "Karate",
        "lastName": "Test"
      }
      """
    When method POST
    Then status 201
    And match response == { id: #number, username: 'karatetestuser', email: 'karatetest@example.com', firstName: 'Karate', lastName: 'Test', role: 'CUSTOMER', enabled: true, createdAt: #notnull }

  Scenario: Login with valid credentials
    Given path '/api/v1/auth/login'
    And request { usernameOrEmail: 'karatetestuser', password: 'Password123!' }
    When method POST
    Then status 200
    And match response.accessToken == '#string'
    And match response.refreshToken == '#string'
    And match response.tokenType == 'Bearer'
    And match response.user.username == 'karatetestuser'
    * def authToken = response.accessToken

  Scenario: Access protected endpoint with valid token
    Given path '/api/v1/users'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    And match response == '#array'

  Scenario: Reject registration with existing username
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "karatetestuser",
        "email": "different@example.com",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 400
    And match response.message == 'Username already exists: karatetestuser'

  Scenario: Reject login with invalid credentials
    Given path '/api/v1/auth/login'
    And request { usernameOrEmail: 'karatetestuser', password: 'WrongPassword123!' }
    When method POST
    Then status 401
    And match response.message == 'Invalid username or password'

  Scenario: Reject access without token
    Given path '/api/v1/users'
    When method GET
    Then status 401

  Scenario: Reject access with invalid token
    Given path '/api/v1/users'
    And header Authorization = 'Bearer invalid.token.here'
    When method GET
    Then status 401

  Scenario: Get user by ID
    Given path '/api/v1/users/1'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 200
    And match response.id == 1
    And match response.username == 'karatetestuser'

  Scenario: Update user
    Given path '/api/v1/users/1'
    And header Authorization = 'Bearer ' + authToken
    And request { firstName: 'Updated', lastName: 'Name' }
    When method PUT
    Then status 200
    And match response.firstName == 'Updated'
    And match response.lastName == 'Name'

  Scenario: Handle non-existent user ID
    Given path '/api/v1/users/99999'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 404
    And match response.message == 'User not found with ID: 99999'

  Scenario: Handle invalid user ID format
    Given path '/api/v1/users/invalid'
    And header Authorization = 'Bearer ' + authToken
    When method GET
    Then status 400

  Scenario: Handle empty request body
    Given path '/api/v1/auth/register'
    And request {}
    When method POST
    Then status 400

  Scenario: Handle malformed JSON
    Given path '/api/v1/auth/register'
    And request '{ invalid json }'
    When method POST
    Then status 400

  Scenario: Test password reset request
    Given path '/api/v1/auth/password/reset/request'
    And param email = 'karatetest@example.com'
    When method POST
    Then status 200
    And match response == 'Password reset email sent'

  Scenario: Refresh token
    Given path '/api/v1/auth/refresh'
    And request { refreshToken: '#(authToken)' }
    When method POST
    Then status 200
    And match response.accessToken == '#string'
    And match response.refreshToken == '#string'

  Scenario: Validate registration with weak password
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "weakpassuser",
        "email": "weakpass@example.com",
        "password": "weak"
      }
      """
    When method POST
    Then status 400

  Scenario: Validate registration with invalid email
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "invalidemailuser",
        "email": "not-an-email",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 400

  Scenario: Validate registration with empty username
    Given path '/api/v1/auth/register'
    And request
      """
      {
        "username": "",
        "email": "emptyuser@example.com",
        "password": "Password123!"
      }
      """
    When method POST
    Then status 400

  Scenario: Test concurrent user operations
    * def users = []
    * def fun = function(i) { return { username: 'concurrent' + i, email: 'concurrent' + i + '@example.com', password: 'Password123!' } }
    * def testUsers = karate.repeat(5, fun)

    * def registerUser = function(user) { karate.call('classpath:karate-config.js').baseUrl; karate.call('register-user.feature', user) }

  Scenario: Health check endpoint
    Given path '/actuator/health'
    When method GET
    Then status 200
    And match response.status == 'UP'