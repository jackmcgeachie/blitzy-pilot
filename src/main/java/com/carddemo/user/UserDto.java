/**
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.carddemo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data transfer object classes providing JSON-compatible structures for REST API communication,
 * replacing COBOL COMMAREA and copybook structures with Bean Validation support and 
 * security-conscious field filtering.
 * 
 * This class represents user data structures converted from the COBOL SEC-USER-DATA copybook
 * structure defined in CSUSR01Y.cpy, supporting all CRUD operations from COUSR00C-03C programs.
 * 
 * Original COBOL structure mapping:
 * - SEC-USR-ID       PIC X(08) → userId (String, max 8 chars)
 * - SEC-USR-FNAME    PIC X(20) → firstName (String, max 20 chars)  
 * - SEC-USR-LNAME    PIC X(20) → lastName (String, max 20 chars)
 * - SEC-USR-PWD      PIC X(08) → password (String, max 8 chars, excluded from responses)
 * - SEC-USR-TYPE     PIC X(01) → userType (String, 1 char: A=Admin, R=Regular)
 * - SEC-USR-FILLER   PIC X(23) → (not used in Java implementation)
 */
public class UserDto {

    /**
     * User creation request DTO for adding new users.
     * Maps to COUSR01C COBOL program functionality with comprehensive validation
     * matching COBOL field validation rules and 88-level conditions.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserCreateRequestDto {
        
        /**
         * User ID field - corresponds to SEC-USR-ID PIC X(08) from COBOL copybook.
         * Validation rules match COBOL validation in COUSR01C program:
         * - Cannot be empty or spaces (line 130-135 in COUSR01C)
         * - Must be alphanumeric characters only
         * - Maximum length 8 characters
         */
        @NotBlank(message = "User ID can NOT be empty...")
        @Size(max = 8, message = "User ID must not exceed 8 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "User ID must contain only alphanumeric characters")
        @JsonProperty("userId")
        private String userId;
        
        /**
         * First name field - corresponds to SEC-USR-FNAME PIC X(20) from COBOL copybook.
         * Validation rules match COBOL validation in COUSR01C program:
         * - Cannot be empty or spaces (line 118-123 in COUSR01C)
         * - Maximum length 20 characters
         */
        @NotBlank(message = "First Name can NOT be empty...")
        @Size(max = 20, message = "First name must not exceed 20 characters")
        @JsonProperty("firstName")
        private String firstName;
        
        /**
         * Last name field - corresponds to SEC-USR-LNAME PIC X(20) from COBOL copybook.
         * Validation rules match COBOL validation in COUSR01C program:
         * - Cannot be empty or spaces (line 124-129 in COUSR01C)
         * - Maximum length 20 characters
         */
        @NotBlank(message = "Last Name can NOT be empty...")
        @Size(max = 20, message = "Last name must not exceed 20 characters")
        @JsonProperty("lastName")
        private String lastName;
        
        /**
         * Password field - corresponds to SEC-USR-PWD PIC X(08) from COBOL copybook.
         * Validation rules match COBOL validation in COUSR01C program:
         * - Cannot be empty or spaces (line 136-141 in COUSR01C)
         * - Maximum length 8 characters
         */
        @NotBlank(message = "Password can NOT be empty...")
        @Size(max = 8, message = "Password must not exceed 8 characters")
        @JsonProperty("password")
        private String password;
        
        /**
         * User type field - corresponds to SEC-USR-TYPE PIC X(01) from COBOL copybook.
         * Validation rules match COBOL validation in COUSR01C program:
         * - Cannot be empty or spaces (line 142-147 in COUSR01C)
         * - Must be either 'A' (Admin) or 'R' (Regular) user type
         */
        @NotBlank(message = "User Type can NOT be empty...")
        @Pattern(regexp = "^[AaRr]$", message = "User Type must be 'A' for Admin or 'R' for Regular")
        @JsonProperty("userType")
        private String userType;

        // Default constructor
        public UserCreateRequestDto() {}

        // Constructor with all fields
        public UserCreateRequestDto(String userId, String firstName, String lastName, 
                                   String password, String userType) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.password = password;
            this.userType = userType;
        }

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        @Override
        public String toString() {
            return "UserCreateRequestDto{" +
                   "userId='" + userId + '\'' +
                   ", firstName='" + firstName + '\'' +
                   ", lastName='" + lastName + '\'' +
                   ", password='[PROTECTED]'" +
                   ", userType='" + userType + '\'' +
                   '}';
        }
    }

    /**
     * User update request DTO for modifying existing users.
     * Maps to COUSR02C COBOL program functionality with validation
     * supporting partial updates and change detection.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserUpdateRequestDto {
        
        /**
         * User ID field - required for identification but not modifiable.
         * Used to lookup existing user record before applying updates.
         */
        @NotBlank(message = "User ID can NOT be empty...")
        @Size(max = 8, message = "User ID must not exceed 8 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "User ID must contain only alphanumeric characters")
        @JsonProperty("userId")
        private String userId;
        
        /**
         * First name field - optional for updates to support partial modifications.
         * Validation rules match COBOL validation in COUSR02C program:
         * - If provided, cannot be empty or spaces (line 186-191 in COUSR02C)
         * - Maximum length 20 characters
         */
        @Size(max = 20, message = "First name must not exceed 20 characters")
        @JsonProperty("firstName")
        private String firstName;
        
        /**
         * Last name field - optional for updates to support partial modifications.
         * Validation rules match COBOL validation in COUSR02C program:
         * - If provided, cannot be empty or spaces (line 192-197 in COUSR02C)
         * - Maximum length 20 characters
         */
        @Size(max = 20, message = "Last name must not exceed 20 characters")
        @JsonProperty("lastName")
        private String lastName;
        
        /**
         * Password field - optional for updates to support password changes.
         * Validation rules match COBOL validation in COUSR02C program:
         * - If provided, cannot be empty or spaces (line 198-203 in COUSR02C)
         * - Maximum length 8 characters
         */
        @Size(max = 8, message = "Password must not exceed 8 characters")
        @JsonProperty("password")
        private String password;
        
        /**
         * User type field - optional for updates to support role changes.
         * Validation rules match COBOL validation in COUSR02C program:
         * - If provided, cannot be empty or spaces (line 204-209 in COUSR02C)
         * - Must be either 'A' (Admin) or 'R' (Regular) user type
         */
        @Pattern(regexp = "^[AaRr]$", message = "User Type must be 'A' for Admin or 'R' for Regular")
        @JsonProperty("userType")
        private String userType;

        // Default constructor
        public UserUpdateRequestDto() {}

        // Constructor with all fields
        public UserUpdateRequestDto(String userId, String firstName, String lastName, 
                                   String password, String userType) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.password = password;
            this.userType = userType;
        }

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        @Override
        public String toString() {
            return "UserUpdateRequestDto{" +
                   "userId='" + userId + '\'' +
                   ", firstName='" + firstName + '\'' +
                   ", lastName='" + lastName + '\'' +
                   ", password='[PROTECTED]'" +
                   ", userType='" + userType + '\'' +
                   '}';
        }
    }

    /**
     * User response DTO for returning user information in API responses.
     * Security-conscious design excludes sensitive fields like passwords from response objects.
     * Maps to COBOL screen display fields in COUSR00C program (populate-user-data section).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserResponseDto {
        
        /**
         * User ID field - safe to include in responses for identification purposes.
         * Corresponds to SEC-USR-ID displayed in BMS screens.
         */
        @JsonProperty("userId")
        private String userId;
        
        /**
         * First name field - safe to include in responses for display purposes.
         * Corresponds to SEC-USR-FNAME displayed in BMS screens.
         */
        @JsonProperty("firstName")
        private String firstName;
        
        /**
         * Last name field - safe to include in responses for display purposes.
         * Corresponds to SEC-USR-LNAME displayed in BMS screens.
         */
        @JsonProperty("lastName")
        private String lastName;
        
        /**
         * User type field - safe to include in responses for authorization display.
         * Corresponds to SEC-USR-TYPE displayed in BMS screens.
         * Values: 'A' = Admin, 'R' = Regular
         */
        @JsonProperty("userType")
        private String userType;
        
        /**
         * User type description field - derived from userType for display purposes.
         * Provides human-readable description of user role.
         */
        @JsonProperty("userTypeDescription")
        private String userTypeDescription;
        
        /**
         * Full name field - concatenated first and last name for display convenience.
         * Matches BMS screen display format in user list screens.
         */
        @JsonProperty("fullName")
        private String fullName;
        
        /**
         * Password field - EXCLUDED from JSON serialization for security compliance.
         * This field should never appear in API responses to prevent password exposure.
         */
        @JsonIgnore
        private String password;
        
        /**
         * Timestamp of last sign-on - for audit and display purposes.
         * Matches COBOL date handling patterns from original system.
         */
        @JsonProperty("lastSignonDate")
        private String lastSignonDate;
        
        /**
         * Time of last sign-on - for audit and display purposes.
         * Matches COBOL time handling patterns from original system.
         */
        @JsonProperty("lastSignonTime")
        private String lastSignonTime;
        
        /**
         * Account status field - indicates if user account is active.
         * Values: 'A' = Active, 'I' = Inactive, 'L' = Locked
         */
        @JsonProperty("accountStatus")
        private String accountStatus;
        
        /**
         * Failed login attempts count - for security monitoring.
         */
        @JsonProperty("failedAttempts")
        private Integer failedAttempts;

        // Default constructor
        public UserResponseDto() {}

        // Constructor with essential fields
        public UserResponseDto(String userId, String firstName, String lastName, String userType) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.userType = userType;
            this.fullName = buildFullName(firstName, lastName);
            this.userTypeDescription = buildUserTypeDescription(userType);
        }

        // Constructor with all fields except password (security)
        public UserResponseDto(String userId, String firstName, String lastName, String userType,
                              String lastSignonDate, String lastSignonTime, String accountStatus,
                              Integer failedAttempts) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.userType = userType;
            this.lastSignonDate = lastSignonDate;
            this.lastSignonTime = lastSignonTime;
            this.accountStatus = accountStatus;
            this.failedAttempts = failedAttempts;
            this.fullName = buildFullName(firstName, lastName);
            this.userTypeDescription = buildUserTypeDescription(userType);
        }

        /**
         * Builds full name from first and last name components.
         * Matches COBOL string concatenation patterns from original system.
         */
        private String buildFullName(String firstName, String lastName) {
            if (firstName != null && lastName != null) {
                return firstName.trim() + " " + lastName.trim();
            } else if (firstName != null) {
                return firstName.trim();
            } else if (lastName != null) {
                return lastName.trim();
            }
            return "";
        }

        /**
         * Builds user type description from userType code.
         * Matches COBOL 88-level condition logic from original system.
         */
        private String buildUserTypeDescription(String userType) {
            if (userType == null) return "Unknown";
            
            switch (userType.toUpperCase()) {
                case "A":
                    return "Administrator";
                case "R":
                    return "Regular User";
                default:
                    return "Unknown";
            }
        }

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { 
            this.firstName = firstName;
            this.fullName = buildFullName(firstName, this.lastName);
        }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { 
            this.lastName = lastName;
            this.fullName = buildFullName(this.firstName, lastName);
        }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { 
            this.userType = userType;
            this.userTypeDescription = buildUserTypeDescription(userType);
        }

        public String getUserTypeDescription() { return userTypeDescription; }
        public void setUserTypeDescription(String userTypeDescription) { 
            this.userTypeDescription = userTypeDescription; 
        }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getLastSignonDate() { return lastSignonDate; }
        public void setLastSignonDate(String lastSignonDate) { this.lastSignonDate = lastSignonDate; }

        public String getLastSignonTime() { return lastSignonTime; }
        public void setLastSignonTime(String lastSignonTime) { this.lastSignonTime = lastSignonTime; }

        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

        public Integer getFailedAttempts() { return failedAttempts; }
        public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

        @Override
        public String toString() {
            return "UserResponseDto{" +
                   "userId='" + userId + '\'' +
                   ", firstName='" + firstName + '\'' +
                   ", lastName='" + lastName + '\'' +
                   ", userType='" + userType + '\'' +
                   ", userTypeDescription='" + userTypeDescription + '\'' +
                   ", fullName='" + fullName + '\'' +
                   ", lastSignonDate='" + lastSignonDate + '\'' +
                   ", lastSignonTime='" + lastSignonTime + '\'' +
                   ", accountStatus='" + accountStatus + '\'' +
                   ", failedAttempts=" + failedAttempts +
                   '}';
        }
    }

    /**
     * User summary DTO for list displays with minimal information.
     * Optimized for performance in paginated user list screens.
     * Maps to COBOL WS-USER-DATA structure with 10 USER-REC entries (lines 56-64 in COUSR00C).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSummaryDto {
        
        /**
         * User ID field - primary identifier for list selection.
         * Corresponds to USER-ID PIC X(08) in WS-USER-DATA structure.
         */
        @JsonProperty("userId")
        private String userId;
        
        /**
         * User name field - concatenated first and last name for list display.
         * Corresponds to USER-NAME PIC X(25) in WS-USER-DATA structure.
         * Matches COBOL string formatting in populate-user-data section.
         */
        @JsonProperty("userName")
        private String userName;
        
        /**
         * User type field - role description for list display.
         * Corresponds to USER-TYPE PIC X(08) in WS-USER-DATA structure.
         * Converted from single character code to readable description.
         */
        @JsonProperty("userType")
        private String userType;
        
        /**
         * Selection flag field - supports BMS selection functionality.
         * Corresponds to USER-SEL PIC X(01) in WS-USER-DATA structure.
         * Used for 'U' (Update) and 'D' (Delete) operations from COUSR00C.
         */
        @JsonProperty("selectionFlag")
        private String selectionFlag;
        
        /**
         * Account status indicator for quick status assessment.
         */
        @JsonProperty("status")
        private String status;

        // Default constructor
        public UserSummaryDto() {}

        // Constructor with essential fields for list display
        public UserSummaryDto(String userId, String firstName, String lastName, String userTypeCode) {
            this.userId = userId;
            this.userName = buildUserName(firstName, lastName);
            this.userType = buildUserTypeDisplay(userTypeCode);
            this.status = "A"; // Default to Active
        }

        // Constructor with all fields
        public UserSummaryDto(String userId, String firstName, String lastName, 
                             String userTypeCode, String selectionFlag, String status) {
            this.userId = userId;
            this.userName = buildUserName(firstName, lastName);
            this.userType = buildUserTypeDisplay(userTypeCode);
            this.selectionFlag = selectionFlag;
            this.status = status;
        }

        /**
         * Builds user name for list display, truncating to 25 characters to match COBOL field length.
         * Replicates COBOL string concatenation logic from populate-user-data section.
         */
        private String buildUserName(String firstName, String lastName) {
            if (firstName == null && lastName == null) return "";
            
            String fullName = "";
            if (firstName != null) fullName += firstName.trim();
            if (lastName != null) {
                if (!fullName.isEmpty()) fullName += " ";
                fullName += lastName.trim();
            }
            
            // Truncate to 25 characters to match COBOL USER-NAME PIC X(25)
            return fullName.length() > 25 ? fullName.substring(0, 25) : fullName;
        }

        /**
         * Builds user type display description from code to match COBOL 88-level conditions.
         */
        private String buildUserTypeDisplay(String userTypeCode) {
            if (userTypeCode == null) return "UNKNOWN";
            
            switch (userTypeCode.toUpperCase()) {
                case "A":
                    return "ADMIN";
                case "R":
                    return "REGULAR";
                default:
                    return "UNKNOWN";
            }
        }

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        public String getSelectionFlag() { return selectionFlag; }
        public void setSelectionFlag(String selectionFlag) { this.selectionFlag = selectionFlag; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return "UserSummaryDto{" +
                   "userId='" + userId + '\'' +
                   ", userName='" + userName + '\'' +
                   ", userType='" + userType + '\'' +
                   ", selectionFlag='" + selectionFlag + '\'' +
                   ", status='" + status + '\'' +
                   '}';
        }
    }

    /**
     * Pagination DTO supporting 10-user page listings per BMS screen requirements.
     * Maps to COBOL pagination logic in COUSR00C program with page forward/backward navigation.
     * Replicates CDEMO-CU00-INFO structure for page management (lines 67-76 in COUSR00C).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserPageDto {
        
        /**
         * List of users for current page - maximum 10 users per BMS screen requirements.
         * Corresponds to USER-REC OCCURS 10 TIMES in WS-USER-DATA (line 57 in COUSR00C).
         */
        @JsonProperty("users")
        private List<UserSummaryDto> users;
        
        /**
         * Current page number - corresponds to CDEMO-CU00-PAGE-NUM PIC 9(08).
         * Used for pagination navigation in process-page-forward/backward sections.
         */
        @JsonProperty("currentPage")
        private Integer currentPage;
        
        /**
         * Page size - fixed at 10 to match BMS screen layout (USER-REC OCCURS 10 TIMES).
         */
        @JsonProperty("pageSize")
        private Integer pageSize;
        
        /**
         * Total number of users across all pages.
         */
        @JsonProperty("totalUsers")
        private Long totalUsers;
        
        /**
         * Total number of pages available.
         */
        @JsonProperty("totalPages")
        private Integer totalPages;
        
        /**
         * First user ID on current page - corresponds to CDEMO-CU00-USRID-FIRST PIC X(08).
         * Used for VSAM STARTBR positioning in process-page-backward section.
         */
        @JsonProperty("firstUserId")
        private String firstUserId;
        
        /**
         * Last user ID on current page - corresponds to CDEMO-CU00-USRID-LAST PIC X(08).
         * Used for VSAM STARTBR positioning in process-page-forward section.
         */
        @JsonProperty("lastUserId")
        private String lastUserId;
        
        /**
         * Has next page flag - corresponds to CDEMO-CU00-NEXT-PAGE-FLG PIC X(01).
         * Matches NEXT-PAGE-YES/NEXT-PAGE-NO 88-level conditions from COBOL.
         */
        @JsonProperty("hasNextPage")
        private Boolean hasNextPage;
        
        /**
         * Has previous page flag - derived from current page position.
         */
        @JsonProperty("hasPreviousPage")
        private Boolean hasPreviousPage;
        
        /**
         * Search criteria applied to current page results.
         */
        @JsonProperty("searchCriteria")
        private UserSearchCriteriaDto searchCriteria;

        // Default constructor
        public UserPageDto() {
            this.pageSize = 10; // Fixed to match BMS screen layout
        }

        // Constructor with essential pagination fields
        public UserPageDto(List<UserSummaryDto> users, Integer currentPage, Long totalUsers) {
            this.users = users;
            this.currentPage = currentPage;
            this.pageSize = 10; // Fixed to match BMS screen layout
            this.totalUsers = totalUsers;
            this.totalPages = calculateTotalPages(totalUsers, pageSize);
            this.hasNextPage = currentPage < totalPages;
            this.hasPreviousPage = currentPage > 1;
            
            // Set first and last user IDs for VSAM positioning
            if (users != null && !users.isEmpty()) {
                this.firstUserId = users.get(0).getUserId();
                this.lastUserId = users.get(users.size() - 1).getUserId();
            }
        }

        /**
         * Calculates total pages based on total users and page size.
         * Matches COBOL arithmetic for pagination calculations.
         */
        private Integer calculateTotalPages(Long totalUsers, Integer pageSize) {
            if (totalUsers == null || totalUsers == 0 || pageSize == null || pageSize == 0) {
                return 0;
            }
            return (int) Math.ceil((double) totalUsers / pageSize);
        }

        // Getters and setters
        public List<UserSummaryDto> getUsers() { return users; }
        public void setUsers(List<UserSummaryDto> users) { 
            this.users = users;
            // Update first and last user IDs when users list changes
            if (users != null && !users.isEmpty()) {
                this.firstUserId = users.get(0).getUserId();
                this.lastUserId = users.get(users.size() - 1).getUserId();
            }
        }

        public Integer getCurrentPage() { return currentPage; }
        public void setCurrentPage(Integer currentPage) { 
            this.currentPage = currentPage;
            this.hasPreviousPage = currentPage != null && currentPage > 1;
        }

        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { 
            this.pageSize = pageSize;
            this.totalPages = calculateTotalPages(this.totalUsers, pageSize);
        }

        public Long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(Long totalUsers) { 
            this.totalUsers = totalUsers;
            this.totalPages = calculateTotalPages(totalUsers, this.pageSize);
            if (currentPage != null) {
                this.hasNextPage = currentPage < totalPages;
            }
        }

        public Integer getTotalPages() { return totalPages; }
        public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }

        public String getFirstUserId() { return firstUserId; }
        public void setFirstUserId(String firstUserId) { this.firstUserId = firstUserId; }

        public String getLastUserId() { return lastUserId; }
        public void setLastUserId(String lastUserId) { this.lastUserId = lastUserId; }

        public Boolean getHasNextPage() { return hasNextPage; }
        public void setHasNextPage(Boolean hasNextPage) { this.hasNextPage = hasNextPage; }

        public Boolean getHasPreviousPage() { return hasPreviousPage; }
        public void setHasPreviousPage(Boolean hasPreviousPage) { this.hasPreviousPage = hasPreviousPage; }

        public UserSearchCriteriaDto getSearchCriteria() { return searchCriteria; }
        public void setSearchCriteria(UserSearchCriteriaDto searchCriteria) { this.searchCriteria = searchCriteria; }

        @Override
        public String toString() {
            return "UserPageDto{" +
                   "usersCount=" + (users != null ? users.size() : 0) +
                   ", currentPage=" + currentPage +
                   ", pageSize=" + pageSize +
                   ", totalUsers=" + totalUsers +
                   ", totalPages=" + totalPages +
                   ", firstUserId='" + firstUserId + '\'' +
                   ", lastUserId='" + lastUserId + '\'' +
                   ", hasNextPage=" + hasNextPage +
                   ", hasPreviousPage=" + hasPreviousPage +
                   '}';
        }
    }

    /**
     * User search criteria DTO for filtering user lists.
     * Supports flexible search functionality matching COBOL search patterns
     * and BMS screen input field capabilities.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSearchCriteriaDto {
        
        /**
         * User ID search filter - partial match support.
         * Corresponds to USRIDINI field from BMS input screens.
         */
        @Size(max = 8, message = "User ID search criteria must not exceed 8 characters")
        @JsonProperty("userId")
        private String userId;
        
        /**
         * First name search filter - partial match support.
         */
        @Size(max = 20, message = "First name search criteria must not exceed 20 characters")
        @JsonProperty("firstName")
        private String firstName;
        
        /**
         * Last name search filter - partial match support.
         */
        @Size(max = 20, message = "Last name search criteria must not exceed 20 characters")
        @JsonProperty("lastName")
        private String lastName;
        
        /**
         * User type filter - exact match for role-based filtering.
         * Values: 'A' = Admin, 'R' = Regular, null = All types
         */
        @Pattern(regexp = "^[AaRr]$", message = "User Type filter must be 'A' for Admin or 'R' for Regular")
        @JsonProperty("userType")
        private String userType;
        
        /**
         * Account status filter - for filtering active/inactive users.
         * Values: 'A' = Active, 'I' = Inactive, 'L' = Locked, null = All statuses
         */
        @Pattern(regexp = "^[AaIiLl]$", message = "Status filter must be 'A' for Active, 'I' for Inactive, or 'L' for Locked")
        @JsonProperty("status")
        private String status;
        
        /**
         * Sort field for ordering results.
         * Supported values: userId, firstName, lastName, userType, lastSignonDate
         */
        @JsonProperty("sortBy")
        private String sortBy;
        
        /**
         * Sort direction for ordering results.
         * Values: ASC (ascending), DESC (descending)
         */
        @Pattern(regexp = "^(ASC|DESC)$", message = "Sort direction must be 'ASC' or 'DESC'")
        @JsonProperty("sortDirection")
        private String sortDirection;

        // Default constructor
        public UserSearchCriteriaDto() {
            this.sortBy = "userId"; // Default sort by user ID
            this.sortDirection = "ASC"; // Default ascending order
        }

        // Constructor with essential search fields
        public UserSearchCriteriaDto(String userId, String userType, String status) {
            this();
            this.userId = userId;
            this.userType = userType;
            this.status = status;
        }

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }

        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

        /**
         * Checks if any search criteria are specified.
         * Used to determine if filtering should be applied.
         */
        public boolean hasSearchCriteria() {
            return (userId != null && !userId.trim().isEmpty()) ||
                   (firstName != null && !firstName.trim().isEmpty()) ||
                   (lastName != null && !lastName.trim().isEmpty()) ||
                   (userType != null && !userType.trim().isEmpty()) ||
                   (status != null && !status.trim().isEmpty());
        }

        @Override
        public String toString() {
            return "UserSearchCriteriaDto{" +
                   "userId='" + userId + '\'' +
                   ", firstName='" + firstName + '\'' +
                   ", lastName='" + lastName + '\'' +
                   ", userType='" + userType + '\'' +
                   ", status='" + status + '\'' +
                   ", sortBy='" + sortBy + '\'' +
                   ", sortDirection='" + sortDirection + '\'' +
                   '}';
        }
    }

    /**
     * User selection DTO for handling BMS-style selection operations.
     * Maps to COBOL selection logic in COUSR00C process-enter-key section
     * supporting 'U' (Update) and 'D' (Delete) operations.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSelectionDto {
        
        /**
         * Selected user ID - corresponds to CDEMO-CU00-USR-SELECTED PIC X(08).
         * Populated when user selects a specific user from the list.
         */
        @NotBlank(message = "Selected user ID cannot be empty")
        @Size(max = 8, message = "User ID must not exceed 8 characters")
        @JsonProperty("selectedUserId")
        private String selectedUserId;
        
        /**
         * Selection operation - corresponds to CDEMO-CU00-USR-SEL-FLG PIC X(01).
         * Valid values: 'U' or 'u' (Update), 'D' or 'd' (Delete)
         * Matches EVALUATE statement in COUSR00C lines 189-215.
         */
        @NotBlank(message = "Selection operation cannot be empty")
        @Pattern(regexp = "^[UuDd]$", message = "Selection operation must be 'U' for Update or 'D' for Delete")
        @JsonProperty("operation")
        private String operation;
        
        /**
         * Selection timestamp for audit purposes.
         */
        @JsonProperty("selectionTimestamp")
        private LocalDateTime selectionTimestamp;
        
        /**
         * User ID of the person making the selection (for audit trail).
         */
        @JsonProperty("performedBy")
        private String performedBy;

        // Default constructor
        public UserSelectionDto() {
            this.selectionTimestamp = LocalDateTime.now();
        }

        // Constructor with essential fields
        public UserSelectionDto(String selectedUserId, String operation) {
            this();
            this.selectedUserId = selectedUserId;
            this.operation = operation;
        }

        // Constructor with all fields
        public UserSelectionDto(String selectedUserId, String operation, String performedBy) {
            this();
            this.selectedUserId = selectedUserId;
            this.operation = operation;
            this.performedBy = performedBy;
        }

        /**
         * Gets the operation description for display purposes.
         * Matches COBOL logic for operation routing in COUSR00C.
         */
        public String getOperationDescription() {
            if (operation == null) return "Unknown";
            
            switch (operation.toUpperCase()) {
                case "U":
                    return "Update User";
                case "D":
                    return "Delete User";
                default:
                    return "Unknown Operation";
            }
        }

        /**
         * Gets the target program for the operation.
         * Matches COBOL XCTL program routing in COUSR00C lines 192-209.
         */
        public String getTargetProgram() {
            if (operation == null) return null;
            
            switch (operation.toUpperCase()) {
                case "U":
                    return "COUSR02C"; // Update program
                case "D":
                    return "COUSR03C"; // Delete program
                default:
                    return null;
            }
        }

        // Getters and setters
        public String getSelectedUserId() { return selectedUserId; }
        public void setSelectedUserId(String selectedUserId) { this.selectedUserId = selectedUserId; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public LocalDateTime getSelectionTimestamp() { return selectionTimestamp; }
        public void setSelectionTimestamp(LocalDateTime selectionTimestamp) { this.selectionTimestamp = selectionTimestamp; }

        public String getPerformedBy() { return performedBy; }
        public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

        @Override
        public String toString() {
            return "UserSelectionDto{" +
                   "selectedUserId='" + selectedUserId + '\'' +
                   ", operation='" + operation + '\'' +
                   ", operationDescription='" + getOperationDescription() + '\'' +
                   ", targetProgram='" + getTargetProgram() + '\'' +
                   ", selectionTimestamp=" + selectionTimestamp +
                   ", performedBy='" + performedBy + '\'' +
                   '}';
        }
    }

    /**
     * User administration response DTO for role-based field filtering.
     * Provides different levels of user information based on the requesting user's role.
     * Implements administrative vs regular user operation security requirements.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserAdminResponseDto extends UserResponseDto {
        
        /**
         * Administrative information available only to admin users.
         * Includes sensitive operational data not shown to regular users.
         */
        @JsonProperty("adminInfo")
        private UserAdminInfo adminInfo;
        
        /**
         * List of roles assigned to the user.
         */
        @JsonProperty("roles")
        private List<String> roles;
        
        /**
         * User creation timestamp.
         */
        @JsonProperty("createdDate")
        private String createdDate;
        
        /**
         * User last modification timestamp.
         */
        @JsonProperty("lastModifiedDate")
        private String lastModifiedDate;
        
        /**
         * User created by (admin user ID).
         */
        @JsonProperty("createdBy")
        private String createdBy;
        
        /**
         * User last modified by (admin user ID).
         */
        @JsonProperty("lastModifiedBy")
        private String lastModifiedBy;

        // Default constructor
        public UserAdminResponseDto() {
            super();
        }

        // Constructor with user base fields
        public UserAdminResponseDto(String userId, String firstName, String lastName, String userType) {
            super(userId, firstName, lastName, userType);
        }

        // Constructor with full admin fields
        public UserAdminResponseDto(String userId, String firstName, String lastName, String userType,
                                   String lastSignonDate, String lastSignonTime, String accountStatus,
                                   Integer failedAttempts, String createdDate, String createdBy) {
            super(userId, firstName, lastName, userType, lastSignonDate, lastSignonTime, accountStatus, failedAttempts);
            this.createdDate = createdDate;
            this.createdBy = createdBy;
        }

        // Getters and setters
        public UserAdminInfo getAdminInfo() { return adminInfo; }
        public void setAdminInfo(UserAdminInfo adminInfo) { this.adminInfo = adminInfo; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }

        public String getCreatedDate() { return createdDate; }
        public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

        public String getLastModifiedDate() { return lastModifiedDate; }
        public void setLastModifiedDate(String lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

        @Override
        public String toString() {
            return "UserAdminResponseDto{" +
                   "userId='" + getUserId() + '\'' +
                   ", firstName='" + getFirstName() + '\'' +
                   ", lastName='" + getLastName() + '\'' +
                   ", userType='" + getUserType() + '\'' +
                   ", accountStatus='" + getAccountStatus() + '\'' +
                   ", createdDate='" + createdDate + '\'' +
                   ", createdBy='" + createdBy + '\'' +
                   ", lastModifiedDate='" + lastModifiedDate + '\'' +
                   ", lastModifiedBy='" + lastModifiedBy + '\'' +
                   ", adminInfo=" + adminInfo +
                   '}';
        }
    }

    /**
     * Administrative information sub-DTO for sensitive user management data.
     * Only included in responses to administrative users.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserAdminInfo {
        
        /**
         * Number of successful logins for security monitoring.
         */
        @JsonProperty("successfulLogins")
        private Integer successfulLogins;
        
        /**
         * Last password change date for security compliance.
         */
        @JsonProperty("lastPasswordChange")
        private String lastPasswordChange;
        
        /**
         * Account lock timestamp if user is locked.
         */
        @JsonProperty("accountLockedDate")
        private String accountLockedDate;
        
        /**
         * Reason for account lock.
         */
        @JsonProperty("lockReason")
        private String lockReason;
        
        /**
         * Password expiration date for compliance tracking.
         */
        @JsonProperty("passwordExpirationDate")
        private String passwordExpirationDate;
        
        /**
         * Security risk score for monitoring purposes.
         */
        @JsonProperty("riskScore")
        private String riskScore;
        
        /**
         * IP address of last successful login.
         */
        @JsonProperty("lastLoginIpAddress")
        private String lastLoginIpAddress;

        // Default constructor
        public UserAdminInfo() {}

        // Constructor with essential fields
        public UserAdminInfo(Integer successfulLogins, String lastPasswordChange, String riskScore) {
            this.successfulLogins = successfulLogins;
            this.lastPasswordChange = lastPasswordChange;
            this.riskScore = riskScore;
        }

        // Getters and setters
        public Integer getSuccessfulLogins() { return successfulLogins; }
        public void setSuccessfulLogins(Integer successfulLogins) { this.successfulLogins = successfulLogins; }

        public String getLastPasswordChange() { return lastPasswordChange; }
        public void setLastPasswordChange(String lastPasswordChange) { this.lastPasswordChange = lastPasswordChange; }

        public String getAccountLockedDate() { return accountLockedDate; }
        public void setAccountLockedDate(String accountLockedDate) { this.accountLockedDate = accountLockedDate; }

        public String getLockReason() { return lockReason; }
        public void setLockReason(String lockReason) { this.lockReason = lockReason; }

        public String getPasswordExpirationDate() { return passwordExpirationDate; }
        public void setPasswordExpirationDate(String passwordExpirationDate) { this.passwordExpirationDate = passwordExpirationDate; }

        public String getRiskScore() { return riskScore; }
        public void setRiskScore(String riskScore) { this.riskScore = riskScore; }

        public String getLastLoginIpAddress() { return lastLoginIpAddress; }
        public void setLastLoginIpAddress(String lastLoginIpAddress) { this.lastLoginIpAddress = lastLoginIpAddress; }

        @Override
        public String toString() {
            return "UserAdminInfo{" +
                   "successfulLogins=" + successfulLogins +
                   ", lastPasswordChange='" + lastPasswordChange + '\'' +
                   ", accountLockedDate='" + accountLockedDate + '\'' +
                   ", lockReason='" + lockReason + '\'' +
                   ", passwordExpirationDate='" + passwordExpirationDate + '\'' +
                   ", riskScore='" + riskScore + '\'' +
                   ", lastLoginIpAddress='" + lastLoginIpAddress + '\'' +
                   '}';
        }
    }

    /**
     * Generic API response wrapper for user operations.
     * Provides consistent response structure with metadata and error information.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserApiResponse<T> {
        
        /**
         * API version identifier.
         */
        @JsonProperty("apiVersion")
        private String apiVersion = "v1";
        
        /**
         * Unique request identifier for tracing.
         */
        @JsonProperty("requestId")
        private String requestId;
        
        /**
         * Response timestamp.
         */
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        /**
         * Response data payload.
         */
        @JsonProperty("data")
        private T data;
        
        /**
         * Success indicator.
         */
        @JsonProperty("success")
        private Boolean success;
        
        /**
         * Error message if operation failed.
         */
        @JsonProperty("errorMessage")
        private String errorMessage;
        
        /**
         * Detailed error information.
         */
        @JsonProperty("errors")
        private List<String> errors;

        // Default constructor
        public UserApiResponse() {
            this.timestamp = LocalDateTime.now();
            this.success = true;
        }

        // Success response constructor
        public UserApiResponse(T data, String requestId) {
            this();
            this.data = data;
            this.requestId = requestId;
        }

        // Error response constructor
        public UserApiResponse(String errorMessage, String requestId) {
            this();
            this.success = false;
            this.errorMessage = errorMessage;
            this.requestId = requestId;
        }

        // Getters and setters
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }

        @Override
        public String toString() {
            return "UserApiResponse{" +
                   "apiVersion='" + apiVersion + '\'' +
                   ", requestId='" + requestId + '\'' +
                   ", timestamp=" + timestamp +
                   ", success=" + success +
                   ", errorMessage='" + errorMessage + '\'' +
                   ", hasData=" + (data != null) +
                   '}';
        }
    }
}
