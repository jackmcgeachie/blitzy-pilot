/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/**
 * Data Transfer Object (DTO) representing authentication request payload for REST API login endpoints.
 * 
 * <p>This DTO provides structured request payload handling for user authentication, implementing
 * comprehensive input validation that matches the COBOL field validation requirements from COSGN00C.
 * The class ensures proper request structure and field validation for Spring Security JWT authentication.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> COSGN00C.cbl - Signon Screen for CardDemo Application</p>
 * 
 * <p><strong>Field Specifications:</strong></p>
 * <ul>
 *   <li>Username: Matches COBOL PIC X(08) - maximum 8 characters, required</li>
 *   <li>Password: Matches COBOL PIC X(08) - maximum 8 characters, required</li>
 * </ul>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>Both username and password fields are required (cannot be blank)</li>
 *   <li>Maximum length of 8 characters for both fields (COBOL compatibility)</li>
 *   <li>Whitespace trimming applied automatically</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 1.0
 */
public class AuthenticationRequest {

    /**
     * Validation error message constants matching COSGN00C error messages
     */
    public static final String USERNAME_REQUIRED_MESSAGE = "Please enter User ID ...";
    public static final String PASSWORD_REQUIRED_MESSAGE = "Please enter Password ...";
    public static final String USERNAME_LENGTH_MESSAGE = "User ID must not exceed 8 characters";
    public static final String PASSWORD_LENGTH_MESSAGE = "Password must not exceed 8 characters";

    /**
     * User identification field matching COBOL WS-USER-ID PIC X(08).
     * 
     * <p>This field corresponds to the USERIDI field from COSGN0AI BMS map
     * and the WS-USER-ID working storage variable in COSGN00C.cbl.</p>
     * 
     * <p><strong>COBOL Reference:</strong></p>
     * <ul>
     *   <li>Line 45: 05 WS-USER-ID PIC X(08)</li>
     *   <li>Line 118: WHEN USERIDI OF COSGN0AI = SPACES OR LOW-VALUES</li>
     *   <li>Line 132: MOVE FUNCTION UPPER-CASE(USERIDI OF COSGN0AI) TO WS-USER-ID</li>
     * </ul>
     */
    @JsonProperty("username")
    @NotBlank(message = USERNAME_REQUIRED_MESSAGE)
    @Size(max = 8, message = USERNAME_LENGTH_MESSAGE)
    private String username;

    /**
     * Password field matching COBOL WS-USER-PWD PIC X(08).
     * 
     * <p>This field corresponds to the PASSWDI field from COSGN0AI BMS map
     * and the WS-USER-PWD working storage variable in COSGN00C.cbl.</p>
     * 
     * <p><strong>COBOL Reference:</strong></p>
     * <ul>
     *   <li>Line 46: 05 WS-USER-PWD PIC X(08)</li>
     *   <li>Line 123: WHEN PASSWDI OF COSGN0AI = SPACES OR LOW-VALUES</li>
     *   <li>Line 135: MOVE FUNCTION UPPER-CASE(PASSWDI OF COSGN0AI) TO WS-USER-PWD</li>
     * </ul>
     */
    @JsonProperty("password")
    @NotBlank(message = PASSWORD_REQUIRED_MESSAGE)
    @Size(max = 8, message = PASSWORD_LENGTH_MESSAGE)
    private String password;

    /**
     * Default no-argument constructor required for JSON deserialization.
     * 
     * <p>This constructor is used by Jackson for deserializing JSON requests
     * into AuthenticationRequest objects.</p>
     */
    public AuthenticationRequest() {
        // Default constructor for JSON deserialization
    }

    /**
     * Constructor for creating authentication request with username and password.
     * 
     * <p>This constructor automatically trims whitespace from input values
     * to ensure consistent validation behavior.</p>
     * 
     * @param username the user identification (max 8 characters, required)
     * @param password the user password (max 8 characters, required)
     */
    @JsonCreator
    public AuthenticationRequest(
            @JsonProperty("username") String username,
            @JsonProperty("password") String password) {
        this.username = username != null ? username.trim() : null;
        this.password = password != null ? password.trim() : null;
    }

    /**
     * Gets the username field value.
     * 
     * <p>Returns the user identification field that will be validated
     * against the PostgreSQL users table username column.</p>
     * 
     * @return the username value, or null if not set
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username field value.
     * 
     * <p>Automatically trims whitespace to ensure consistent validation.
     * This setter is used during JSON deserialization and form binding.</p>
     * 
     * @param username the user identification (max 8 characters, required)
     */
    public void setUsername(String username) {
        this.username = username != null ? username.trim() : null;
    }

    /**
     * Gets the password field value.
     * 
     * <p>Returns the password field that will be validated using BCrypt
     * against the PostgreSQL users table password_hash column.</p>
     * 
     * @return the password value, or null if not set
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password field value.
     * 
     * <p>Automatically trims whitespace to ensure consistent validation.
     * This setter is used during JSON deserialization and form binding.</p>
     * 
     * @param password the user password (max 8 characters, required)
     */
    public void setPassword(String password) {
        this.password = password != null ? password.trim() : null;
    }

    /**
     * Validates if the authentication request has non-blank credentials.
     * 
     * <p>This method provides programmatic validation equivalent to the
     * COBOL validation logic in COSGN00C lines 118-130.</p>
     * 
     * @return true if both username and password are present and non-blank
     */
    public boolean hasValidCredentials() {
        return username != null && !username.trim().isEmpty() &&
               password != null && !password.trim().isEmpty();
    }

    /**
     * Returns the username in uppercase format as per COBOL processing.
     * 
     * <p>Replicates the COBOL FUNCTION UPPER-CASE logic from COSGN00C line 132.
     * This method is used by the authentication service to ensure consistent
     * case handling with the legacy system.</p>
     * 
     * @return uppercase username, or null if username is null
     */
    public String getUppercaseUsername() {
        return username != null ? username.toUpperCase().trim() : null;
    }

    /**
     * Returns the password in uppercase format as per COBOL processing.
     * 
     * <p>Replicates the COBOL FUNCTION UPPER-CASE logic from COSGN00C line 135.
     * This method is used by the authentication service to ensure consistent
     * case handling with the legacy system.</p>
     * 
     * @return uppercase password, or null if password is null
     */
    public String getUppercasePassword() {
        return password != null ? password.toUpperCase().trim() : null;
    }

    /**
     * Checks if this authentication request is equal to another object.
     * 
     * <p>Two AuthenticationRequest objects are considered equal if they have
     * the same username and password values (case-sensitive comparison).</p>
     * 
     * @param obj the object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AuthenticationRequest that = (AuthenticationRequest) obj;
        return Objects.equals(username, that.username) &&
               Objects.equals(password, that.password);
    }

    /**
     * Returns a hash code value for this authentication request.
     * 
     * <p>The hash code is based on both username and password fields
     * to ensure proper hash-based collection behavior.</p>
     * 
     * @return hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    /**
     * Returns a string representation of this authentication request.
     * 
     * <p><strong>Security Note:</strong> The password field is masked in the
     * string representation to prevent accidental logging of credentials.</p>
     * 
     * @return string representation with masked password
     */
    @Override
    public String toString() {
        return "AuthenticationRequest{" +
                "username='" + username + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}