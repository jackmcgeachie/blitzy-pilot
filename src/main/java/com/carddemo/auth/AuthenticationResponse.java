/**
 * AuthenticationResponse.java
 * 
 * Data Transfer Object (DTO) representing authentication response payload.
 * Contains JWT token, user details, and session information for successful
 * authentication operations, including user context and navigation information
 * for frontend integration.
 * 
 * Derived from COBOL program COSGN00C.cbl and COMMAREA structure COCOM01Y.cpy
 * 
 * @version CardDemo_v1.0-15-g27d6c6f-68
 * @since 1.0
 */

package com.carddemo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * Authentication response DTO containing JWT token and user details.
 * 
 * Maps to CARDDEMO-COMMAREA structure from COCOM01Y.cpy:
 * - CDEMO-USER-ID -> userId
 * - CDEMO-USER-TYPE -> userType
 * - Navigation context for React frontend routing
 * 
 * Supports Spring Security JWT authentication with role-based access control.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // Authentication status and token information
    @JsonProperty("success")
    @NotNull
    private Boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("token")
    private String token;

    @JsonProperty("tokenType")
    private String tokenType = "Bearer";

    @JsonProperty("expiresIn")
    private Long expiresIn;

    @JsonProperty("expiresAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    // User information matching CARDDEMO-COMMAREA structure
    @JsonProperty("userId")
    @Size(max = 8, message = "User ID must not exceed 8 characters")
    @Pattern(regexp = "^[A-Z0-9]*$", message = "User ID must contain only uppercase letters and numbers")
    private String userId;

    @JsonProperty("username")
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    @JsonProperty("userType")
    @Pattern(regexp = "^[AU]$", message = "User type must be 'A' (Admin) or 'U' (User)")
    private String userType;

    @JsonProperty("role")
    private String role;

    @JsonProperty("firstName")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @JsonProperty("lastName")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    // Session and navigation information
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("sessionCorrelationId")
    private String sessionCorrelationId;

    @JsonProperty("fromTransactionId")
    @Size(max = 4, message = "From transaction ID must not exceed 4 characters")
    private String fromTransactionId;

    @JsonProperty("fromProgram")
    @Size(max = 8, message = "From program must not exceed 8 characters")
    private String fromProgram;

    @JsonProperty("nextRoute")
    private String nextRoute;

    @JsonProperty("menuType")
    private String menuType;

    // System information
    @JsonProperty("applicationId")
    private String applicationId;

    @JsonProperty("systemId")
    private String systemId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Error handling fields
    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("errorDetails")
    private String errorDetails;

    /**
     * Default constructor for JSON deserialization.
     */
    public AuthenticationResponse() {
        this.timestamp = LocalDateTime.now();
        this.tokenType = "Bearer";
    }

    /**
     * Constructor for successful authentication response.
     * 
     * @param token JWT token
     * @param userId User ID from COMMAREA
     * @param username Username
     * @param userType User type ('A' for Admin, 'U' for User)
     * @param expiresIn Token expiration time in seconds
     */
    public AuthenticationResponse(String token, String userId, String username, 
                                String userType, Long expiresIn) {
        this();
        this.success = true;
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.userType = userType;
        this.expiresIn = expiresIn;
        this.expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
        this.role = mapUserTypeToRole(userType);
        this.nextRoute = determineNextRoute(userType);
        this.menuType = determineMenuType(userType);
        this.fromTransactionId = "CC00"; // COSGN00C transaction ID
        this.fromProgram = "COSGN00C";
    }

    /**
     * Constructor for failed authentication response.
     * 
     * @param message Error message
     * @param errorCode Error code
     */
    public AuthenticationResponse(String message, String errorCode) {
        this();
        this.success = false;
        this.message = message;
        this.errorCode = errorCode;
    }

    /**
     * Maps COBOL user type to Spring Security role.
     * Replicates RACF user type mapping from COCOM01Y.cpy:
     * - 'A' (CDEMO-USRTYP-ADMIN) -> ROLE_ADMIN
     * - 'U' (CDEMO-USRTYP-USER) -> ROLE_USER
     * 
     * @param userType COBOL user type ('A' or 'U')
     * @return Spring Security role
     */
    private String mapUserTypeToRole(String userType) {
        if ("A".equals(userType)) {
            return "ROLE_ADMIN";
        } else if ("U".equals(userType)) {
            return "ROLE_USER";
        }
        return "ROLE_USER"; // Default to user role
    }

    /**
     * Determines next route for React frontend navigation.
     * Replicates COBOL XCTL logic from COSGN00C.cbl:
     * - Admin users -> /admin/menu (COADM01C equivalent)
     * - Regular users -> /main/menu (COMEN01C equivalent)
     * 
     * @param userType User type from COMMAREA
     * @return Next route path
     */
    private String determineNextRoute(String userType) {
        if ("A".equals(userType)) {
            return "/admin/menu";
        } else {
            return "/main/menu";
        }
    }

    /**
     * Determines menu type for frontend display.
     * 
     * @param userType User type from COMMAREA
     * @return Menu type identifier
     */
    private String determineMenuType(String userType) {
        if ("A".equals(userType)) {
            return "ADMIN_MENU";
        } else {
            return "MAIN_MENU";
        }
    }

    /**
     * Creates successful authentication response with full user context.
     * 
     * @param token JWT token
     * @param userId User ID
     * @param username Username
     * @param userType User type ('A' or 'U')
     * @param firstName First name
     * @param lastName Last name
     * @param expiresIn Token expiration in seconds
     * @param sessionId Session ID
     * @return Authentication response
     */
    public static AuthenticationResponse success(String token, String userId, String username,
                                               String userType, String firstName, String lastName,
                                               Long expiresIn, String sessionId) {
        AuthenticationResponse response = new AuthenticationResponse(token, userId, username, 
                                                                   userType, expiresIn);
        response.setFirstName(firstName);
        response.setLastName(lastName);
        response.setSessionId(sessionId);
        response.setMessage("Authentication successful");
        return response;
    }

    /**
     * Creates failed authentication response.
     * 
     * @param message Error message
     * @param errorCode Error code
     * @return Authentication response
     */
    public static AuthenticationResponse failure(String message, String errorCode) {
        return new AuthenticationResponse(message, errorCode);
    }

    /**
     * Creates user not found response.
     * 
     * @return Authentication response
     */
    public static AuthenticationResponse userNotFound() {
        return new AuthenticationResponse("User not found. Try again ...", "USER_NOT_FOUND");
    }

    /**
     * Creates wrong password response.
     * 
     * @return Authentication response
     */
    public static AuthenticationResponse wrongPassword() {
        return new AuthenticationResponse("Wrong Password. Try again ...", "INVALID_PASSWORD");
    }

    /**
     * Creates system error response.
     * 
     * @return Authentication response
     */
    public static AuthenticationResponse systemError() {
        return new AuthenticationResponse("Unable to verify the User ...", "SYSTEM_ERROR");
    }

    /**
     * Checks if authentication was successful.
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * Checks if user is administrator.
     * 
     * @return true if user type is 'A', false otherwise
     */
    public boolean isAdmin() {
        return "A".equals(userType);
    }

    /**
     * Checks if user is regular user.
     * 
     * @return true if user type is 'U', false otherwise
     */
    public boolean isRegularUser() {
        return "U".equals(userType);
    }

    // Getters and Setters

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
        if (expiresIn != null) {
            this.expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
        }
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
        this.role = mapUserTypeToRole(userType);
        this.nextRoute = determineNextRoute(userType);
        this.menuType = determineMenuType(userType);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionCorrelationId() {
        return sessionCorrelationId;
    }

    public void setSessionCorrelationId(String sessionCorrelationId) {
        this.sessionCorrelationId = sessionCorrelationId;
    }

    public String getFromTransactionId() {
        return fromTransactionId;
    }

    public void setFromTransactionId(String fromTransactionId) {
        this.fromTransactionId = fromTransactionId;
    }

    public String getFromProgram() {
        return fromProgram;
    }

    public void setFromProgram(String fromProgram) {
        this.fromProgram = fromProgram;
    }

    public String getNextRoute() {
        return nextRoute;
    }

    public void setNextRoute(String nextRoute) {
        this.nextRoute = nextRoute;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    @Override
    public String toString() {
        return "AuthenticationResponse{" +
                "success=" + success +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", userType='" + userType + '\'' +
                ", role='" + role + '\'' +
                ", nextRoute='" + nextRoute + '\'' +
                ", menuType='" + menuType + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthenticationResponse that = (AuthenticationResponse) o;

        if (success != null ? !success.equals(that.success) : that.success != null) return false;
        if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
        if (username != null ? !username.equals(that.username) : that.username != null) return false;
        if (userType != null ? !userType.equals(that.userType) : that.userType != null) return false;
        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }

    @Override
    public int hashCode() {
        int result = success != null ? success.hashCode() : 0;
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (userType != null ? userType.hashCode() : 0);
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        return result;
    }
}