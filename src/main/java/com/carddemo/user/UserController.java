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

import com.carddemo.user.UserDto.UserCreateRequestDto;
import com.carddemo.user.UserDto.UserUpdateRequestDto;
import com.carddemo.user.UserDto.UserResponseDto;
import com.carddemo.user.UserDto.UserPageDto;
import com.carddemo.user.UserDto.UserSearchCriteriaDto;
import com.carddemo.user.UserDto.UserSelectionDto;
import com.carddemo.user.UserDto.UserApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST controller providing HTTP endpoints for user management operations in the CardDemo system.
 * 
 * This controller replaces the legacy CICS transaction processing programs COUSR00C-03C with 
 * modern REST API endpoints, implementing comprehensive user management functionality including
 * listing, creation, updates, and deletion operations with Spring Security authorization.
 * 
 * <p><b>COBOL Program Migration Mapping:</b></p>
 * <ul>
 *   <li>COUSR00C (CU00) → GET /api/v1/users - User list with pagination and selection</li>
 *   <li>COUSR01C (CU01) → POST /api/v1/users - User creation with validation</li>
 *   <li>COUSR02C (CU02) → PUT /api/v1/users/{userId} - User update with change detection</li>
 *   <li>COUSR03C (CU03) → DELETE /api/v1/users/{userId} - User deletion with confirmation</li>
 * </ul>
 * 
 * <p><b>Security Implementation:</b></p>
 * Spring Security method-level authorization enforces role-based access control matching 
 * RACF user type restrictions:
 * <ul>
 *   <li>ROLE_ADMIN (Type 'A') - Full user management access including administrative operations</li>
 *   <li>ROLE_USER (Type 'R') - Read-only access to user information with limited operations</li>
 * </ul>
 * 
 * <p><b>Session Management:</b></p>
 * Pseudo-conversational state management through Redis-backed Spring Session maintains 
 * user context across REST interactions, replicating CICS COMMAREA functionality for 
 * pagination state, navigation context, and temporary data preservation.
 * 
 * <p><b>API Communication:</b></p>
 * All endpoints support JSON payloads over HTTP/REST with standardized error responses 
 * and comprehensive validation matching original COBOL field validation rules.
 * 
 * @author Blitzy Agent
 * @version 1.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User Management", description = "User administration operations converted from COBOL COUSR00C-03C programs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    // Constants matching COBOL program behavior
    private static final int DEFAULT_PAGE_SIZE = 10; // Matches USER-REC OCCURS 10 TIMES from COUSR00C
    private static final String TRANSACTION_CU00 = "CU00"; // COUSR00C transaction ID
    private static final String TRANSACTION_CU01 = "CU01"; // COUSR01C transaction ID  
    private static final String TRANSACTION_CU02 = "CU02"; // COUSR02C transaction ID
    private static final String TRANSACTION_CU03 = "CU03"; // COUSR03C transaction ID

    // Error messages matching COBOL validation messages
    private static final String MSG_USER_NOT_FOUND = "User ID NOT found...";
    private static final String MSG_USER_EXISTS = "User ID already exist...";
    private static final String MSG_INVALID_SELECTION = "Invalid selection. Valid values are U and D";
    private static final String MSG_INVALID_KEY = "Invalid key pressed. Use valid navigation keys.";
    private static final String MSG_USER_CREATED = "User %s has been added ...";
    private static final String MSG_USER_UPDATED = "User %s has been updated ...";
    private static final String MSG_USER_DELETED = "User %s has been deleted ...";
    private static final String MSG_NO_CHANGES = "Please modify to update ...";

    private final UserService userService;

    /**
     * Constructor for dependency injection of UserService.
     * 
     * @param userService The user service for business logic operations
     */
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
        logger.info("UserController initialized with UserService dependency");
    }

    /**
     * Retrieve paginated list of users with optional search criteria.
     * 
     * This endpoint replicates the functionality of COUSR00C (CU00 transaction) which provides
     * user listing with 10 users per page, navigation controls (PF7/PF8), and user selection
     * capabilities for update ('U') and delete ('D') operations.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR00C.cbl lines 57-64 (WS-USER-DATA structure)</p>
     * <p><b>Pagination Logic:</b> Matches PROCESS-PAGE-FORWARD and PROCESS-PAGE-BACKWARD sections</p>
     * <p><b>Session State:</b> Maintains CDEMO-CU00-INFO structure in Redis session</p>
     * 
     * @param page Page number (0-based, default: 0)
     * @param size Page size (fixed at 10 to match BMS screen layout)
     * @param sortBy Sort field (default: userId)
     * @param sortDirection Sort direction (ASC/DESC, default: ASC)
     * @param userId Optional user ID filter for partial matching
     * @param firstName Optional first name filter
     * @param lastName Optional last name filter  
     * @param userType Optional user type filter ('A' for Admin, 'R' for Regular)
     * @param status Optional status filter ('A' for Active, 'I' for Inactive)
     * @param request HTTP request for session management
     * @return UserPageDto containing paginated user list with navigation metadata
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
        summary = "List users with pagination", 
        description = "Retrieve paginated list of users with search and filtering capabilities, " +
                     "replicating COUSR00C (CU00) transaction functionality with 10 users per page"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<UserPageDto>> listUsers(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size (fixed at 10 to match BMS screen)")
            @RequestParam(defaultValue = "10") int size,
            
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "userId") String sortBy,
            
            @Parameter(description = "Sort direction (ASC/DESC)")
            @RequestParam(defaultValue = "ASC") 
            @Pattern(regexp = "^(ASC|DESC)$", message = "Sort direction must be 'ASC' or 'DESC'")
            String sortDirection,
            
            @Parameter(description = "User ID filter (partial match)")
            @RequestParam(required = false)
            @Size(max = 8, message = "User ID filter must not exceed 8 characters")
            String userId,
            
            @Parameter(description = "First name filter")
            @RequestParam(required = false)
            @Size(max = 20, message = "First name filter must not exceed 20 characters")
            String firstName,
            
            @Parameter(description = "Last name filter")
            @RequestParam(required = false)
            @Size(max = 20, message = "Last name filter must not exceed 20 characters")
            String lastName,
            
            @Parameter(description = "User type filter ('A' for Admin, 'R' for Regular)")
            @RequestParam(required = false)
            @Pattern(regexp = "^[AaRr]$", message = "User type must be 'A' for Admin or 'R' for Regular")
            String userType,
            
            @Parameter(description = "Status filter ('A' for Active, 'I' for Inactive)")
            @RequestParam(required = false)
            @Pattern(regexp = "^[AaIiLl]$", message = "Status must be 'A' for Active, 'I' for Inactive, or 'L' for Locked")
            String status,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing user list request - User: {}, Page: {}, RequestId: {}", 
                   currentUser, page, requestId);
        
        try {
            // Force page size to 10 to match COBOL USER-REC OCCURS 10 TIMES structure
            size = DEFAULT_PAGE_SIZE;
            
            // Build search criteria matching COBOL filter logic
            UserSearchCriteriaDto searchCriteria = new UserSearchCriteriaDto();
            searchCriteria.setUserId(userId);
            searchCriteria.setFirstName(firstName);
            searchCriteria.setLastName(lastName);
            searchCriteria.setUserType(userType);
            searchCriteria.setStatus(status);
            searchCriteria.setSortBy(sortBy);
            searchCriteria.setSortDirection(sortDirection);
            
            // Create pagination matching COBOL page management
            Sort.Direction direction = Sort.Direction.fromString(sortDirection);
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            // Retrieve user page from service layer
            UserPageDto userPage = userService.findUsers(searchCriteria, pageable);
            
            // Store pagination state in session (replicating CDEMO-CU00-INFO structure)
            HttpSession session = request.getSession();
            session.setAttribute("currentPage", page);
            session.setAttribute("pageSize", size);
            session.setAttribute("searchCriteria", searchCriteria);
            session.setAttribute("transactionId", TRANSACTION_CU00);
            
            logger.info("User list retrieved successfully - Count: {}, Page: {}, RequestId: {}", 
                       userPage.getUsers() != null ? userPage.getUsers().size() : 0, page, requestId);
            
            UserApiResponse<UserPageDto> response = new UserApiResponse<>(userPage, requestId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving user list - User: {}, RequestId: {}, Error: {}", 
                        currentUser, requestId, e.getMessage(), e);
            
            UserApiResponse<UserPageDto> errorResponse = new UserApiResponse<>(
                "Unable to lookup User...", requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Retrieve specific user details by user ID.
     * 
     * This endpoint provides individual user lookup functionality used by update and delete
     * operations, matching the COBOL READ-USER-SEC-FILE sections in COUSR02C and COUSR03C.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR02C.cbl lines 320-353 (READ-USER-SEC-FILE)</p>
     * <p><b>Security:</b> Admin users can view any user, regular users restricted by business rules</p>
     * 
     * @param userId The user ID to retrieve (max 8 characters, alphanumeric)
     * @param request HTTP request for session management
     * @return UserResponseDto containing user details or 404 if not found
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and #userId == authentication.name)")
    @Operation(
        summary = "Get user details",
        description = "Retrieve individual user information by user ID, " +
                     "matching COBOL READ-USER-SEC-FILE functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found and returned"),
        @ApiResponse(responseCode = "400", description = "Invalid user ID format"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<UserResponseDto>> getUser(
            @Parameter(description = "User ID (max 8 alphanumeric characters)")
            @PathVariable
            @Size(max = 8, message = "User ID must not exceed 8 characters")
            @Pattern(regexp = "^[A-Za-z0-9]+$", message = "User ID must contain only alphanumeric characters")
            String userId,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing get user request - User: {}, TargetUserId: {}, RequestId: {}", 
                   currentUser, userId, requestId);
        
        try {
            // Retrieve user from service layer
            UserResponseDto user = userService.findUserById(userId);
            
            if (user == null) {
                logger.warn("User not found - UserId: {}, RequestId: {}", userId, requestId);
                UserApiResponse<UserResponseDto> notFoundResponse = new UserApiResponse<>(
                    MSG_USER_NOT_FOUND, requestId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            // Store user context in session for potential updates
            HttpSession session = request.getSession();
            session.setAttribute("selectedUserId", userId);
            session.setAttribute("lastAccessTime", LocalDateTime.now());
            
            logger.info("User retrieved successfully - UserId: {}, RequestId: {}", userId, requestId);
            
            UserApiResponse<UserResponseDto> response = new UserApiResponse<>(user, requestId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving user - UserId: {}, RequestId: {}, Error: {}", 
                        userId, requestId, e.getMessage(), e);
            
            UserApiResponse<UserResponseDto> errorResponse = new UserApiResponse<>(
                "Unable to lookup User...", requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Create a new user account.
     * 
     * This endpoint replicates the functionality of COUSR01C (CU01 transaction) which handles
     * new user creation with comprehensive field validation, duplicate checking, and success
     * confirmation messaging.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR01C.cbl lines 238-274 (WRITE-USER-SEC-FILE)</p>
     * <p><b>Validation Logic:</b> Matches PROCESS-ENTER-KEY validation (lines 115-160)</p>
     * <p><b>Security:</b> Restricted to administrative users only</p>
     * 
     * @param createRequest User creation request with validated fields
     * @param request HTTP request for session management
     * @return UserResponseDto containing created user details
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Create new user",
        description = "Create a new user account with validation and duplicate checking, " +
                     "replicating COUSR01C (CU01) transaction functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or validation errors"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
        @ApiResponse(responseCode = "409", description = "User already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<UserResponseDto>> createUser(
            @Parameter(description = "User creation request with validated fields")
            @Valid @RequestBody UserCreateRequestDto createRequest,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing create user request - User: {}, NewUserId: {}, RequestId: {}", 
                   currentUser, createRequest.getUserId(), requestId);
        
        try {
            // Check for duplicate user ID (matching COBOL DUPKEY/DUPREC handling)
            if (userService.existsByUserId(createRequest.getUserId())) {
                logger.warn("Duplicate user creation attempted - UserId: {}, RequestId: {}", 
                           createRequest.getUserId(), requestId);
                
                UserApiResponse<UserResponseDto> duplicateResponse = new UserApiResponse<>(
                    MSG_USER_EXISTS, requestId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(duplicateResponse);
            }
            
            // Create user through service layer
            UserResponseDto createdUser = userService.createUser(createRequest, currentUser);
            
            // Store creation context in session
            HttpSession session = request.getSession();
            session.setAttribute("lastCreatedUserId", createdUser.getUserId());
            session.setAttribute("transactionId", TRANSACTION_CU01);
            session.setAttribute("createdBy", currentUser);
            session.setAttribute("createdAt", LocalDateTime.now());
            
            String successMessage = String.format(MSG_USER_CREATED, createdUser.getUserId());
            logger.info("User created successfully - UserId: {}, CreatedBy: {}, RequestId: {}", 
                       createdUser.getUserId(), currentUser, requestId);
            
            UserApiResponse<UserResponseDto> response = new UserApiResponse<>(createdUser, requestId);
            response.setErrorMessage(successMessage); // Success message in COBOL style
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user creation request - RequestId: {}, Error: {}", 
                       requestId, e.getMessage());
            
            UserApiResponse<UserResponseDto> badRequestResponse = new UserApiResponse<>(
                e.getMessage(), requestId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequestResponse);
            
        } catch (Exception e) {
            logger.error("Error creating user - RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            UserApiResponse<UserResponseDto> errorResponse = new UserApiResponse<>(
                "Unable to Add User...", requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update existing user information.
     * 
     * This endpoint replicates the functionality of COUSR02C (CU02 transaction) which handles
     * user updates with change detection, field validation, and optimistic locking for 
     * concurrent access control.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR02C.cbl lines 358-390 (UPDATE-USER-SEC-FILE)</p>
     * <p><b>Change Detection:</b> Matches lines 219-244 (field-by-field change detection)</p>
     * <p><b>Concurrency:</b> JPA optimistic locking replaces CICS UPDATE file control</p>
     * 
     * @param userId The user ID to update (must match path parameter)
     * @param updateRequest User update request with optional fields
     * @param request HTTP request for session management
     * @return UserResponseDto containing updated user details
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Update user information",
        description = "Update existing user with change detection and validation, " +
                     "replicating COUSR02C (CU02) transaction functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or no changes detected"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "409", description = "Concurrent modification detected"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<UserResponseDto>> updateUser(
            @Parameter(description = "User ID to update")
            @PathVariable
            @Size(max = 8, message = "User ID must not exceed 8 characters")
            @Pattern(regexp = "^[A-Za-z0-9]+$", message = "User ID must contain only alphanumeric characters")
            String userId,
            
            @Parameter(description = "User update request with optional fields")
            @Valid @RequestBody UserUpdateRequestDto updateRequest,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing update user request - User: {}, TargetUserId: {}, RequestId: {}", 
                   currentUser, userId, requestId);
        
        try {
            // Validate that path userId matches request userId (if provided)
            if (updateRequest.getUserId() != null && !updateRequest.getUserId().equals(userId)) {
                logger.warn("User ID mismatch in update request - Path: {}, Body: {}, RequestId: {}", 
                           userId, updateRequest.getUserId(), requestId);
                
                UserApiResponse<UserResponseDto> mismatchResponse = new UserApiResponse<>(
                    "User ID mismatch between path and request body", requestId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mismatchResponse);
            }
            
            // Ensure userId is set in request for service processing
            updateRequest.setUserId(userId);
            
            // Check if user exists before attempting update
            if (!userService.existsByUserId(userId)) {
                logger.warn("Update attempted on non-existent user - UserId: {}, RequestId: {}", 
                           userId, requestId);
                
                UserApiResponse<UserResponseDto> notFoundResponse = new UserApiResponse<>(
                    MSG_USER_NOT_FOUND, requestId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            // Update user through service layer with change detection
            UserResponseDto updatedUser = userService.updateUser(updateRequest, currentUser);
            
            // Store update context in session
            HttpSession session = request.getSession();
            session.setAttribute("lastUpdatedUserId", userId);
            session.setAttribute("transactionId", TRANSACTION_CU02);
            session.setAttribute("updatedBy", currentUser);
            session.setAttribute("updatedAt", LocalDateTime.now());
            
            String successMessage = String.format(MSG_USER_UPDATED, userId);
            logger.info("User updated successfully - UserId: {}, UpdatedBy: {}, RequestId: {}", 
                       userId, currentUser, requestId);
            
            UserApiResponse<UserResponseDto> response = new UserApiResponse<>(updatedUser, requestId);
            response.setErrorMessage(successMessage); // Success message in COBOL style
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user update request - UserId: {}, RequestId: {}, Error: {}", 
                       userId, requestId, e.getMessage());
            
            // Check for "no changes" scenario matching COBOL logic
            String errorMessage = e.getMessage().contains("No changes") ? MSG_NO_CHANGES : e.getMessage();
            UserApiResponse<UserResponseDto> badRequestResponse = new UserApiResponse<>(
                errorMessage, requestId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequestResponse);
            
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            logger.warn("Concurrent modification detected - UserId: {}, RequestId: {}", 
                       userId, requestId);
            
            UserApiResponse<UserResponseDto> conflictResponse = new UserApiResponse<>(
                "User has been modified by another user. Please refresh and try again.", requestId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflictResponse);
            
        } catch (Exception e) {
            logger.error("Error updating user - UserId: {}, RequestId: {}, Error: {}", 
                        userId, requestId, e.getMessage(), e);
            
            UserApiResponse<UserResponseDto> errorResponse = new UserApiResponse<>(
                "Unable to Update User...", requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Delete user account.
     * 
     * This endpoint replicates the functionality of COUSR03C (CU03 transaction) which handles
     * user deletion with confirmation requirements and comprehensive audit logging.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR03C.cbl lines 305-336 (DELETE-USER-SEC-FILE)</p>
     * <p><b>Confirmation Logic:</b> Matches PF5 key confirmation pattern from COBOL</p>
     * <p><b>Security:</b> Restricted to administrative users with full audit trail</p>
     * 
     * @param userId The user ID to delete (max 8 characters, alphanumeric)
     * @param confirm Confirmation flag required for deletion (must be true)
     * @param request HTTP request for session management
     * @return Success message confirming deletion
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete user account",
        description = "Delete user account with confirmation requirement, " +
                     "replicating COUSR03C (CU03) transaction functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or missing confirmation"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<String>> deleteUser(
            @Parameter(description = "User ID to delete")
            @PathVariable
            @Size(max = 8, message = "User ID must not exceed 8 characters")
            @Pattern(regexp = "^[A-Za-z0-9]+$", message = "User ID must contain only alphanumeric characters")
            String userId,
            
            @Parameter(description = "Confirmation flag required for deletion", required = true)
            @RequestParam(required = true) boolean confirm,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing delete user request - User: {}, TargetUserId: {}, Confirmed: {}, RequestId: {}", 
                   currentUser, userId, confirm, requestId);
        
        try {
            // Require explicit confirmation (matching COBOL PF5 key confirmation)
            if (!confirm) {
                logger.warn("Delete attempted without confirmation - UserId: {}, RequestId: {}", 
                           userId, requestId);
                
                UserApiResponse<String> confirmationResponse = new UserApiResponse<>(
                    "Deletion requires explicit confirmation. Set confirm=true to proceed.", requestId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(confirmationResponse);
            }
            
            // Check if user exists before attempting deletion
            if (!userService.existsByUserId(userId)) {
                logger.warn("Delete attempted on non-existent user - UserId: {}, RequestId: {}", 
                           userId, requestId);
                
                UserApiResponse<String> notFoundResponse = new UserApiResponse<>(
                    MSG_USER_NOT_FOUND, requestId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            // Delete user through service layer
            userService.deleteUser(userId, currentUser);
            
            // Store deletion context in session
            HttpSession session = request.getSession();
            session.setAttribute("lastDeletedUserId", userId);
            session.setAttribute("transactionId", TRANSACTION_CU03);
            session.setAttribute("deletedBy", currentUser);
            session.setAttribute("deletedAt", LocalDateTime.now());
            
            String successMessage = String.format(MSG_USER_DELETED, userId);
            logger.info("User deleted successfully - UserId: {}, DeletedBy: {}, RequestId: {}", 
                       userId, currentUser, requestId);
            
            UserApiResponse<String> response = new UserApiResponse<>(successMessage, requestId);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user deletion request - UserId: {}, RequestId: {}, Error: {}", 
                       userId, requestId, e.getMessage());
            
            UserApiResponse<String> badRequestResponse = new UserApiResponse<>(
                e.getMessage(), requestId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequestResponse);
            
        } catch (Exception e) {
            logger.error("Error deleting user - UserId: {}, RequestId: {}, Error: {}", 
                        userId, requestId, e.getMessage(), e);
            
            UserApiResponse<String> errorResponse = new UserApiResponse<>(
                "Unable to Update User...", requestId); // Matches COBOL error message
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Handle user selection operations for BMS-style interaction patterns.
     * 
     * This endpoint replicates the COBOL selection logic from COUSR00C (lines 149-216) which
     * processes user selections for 'U' (Update) and 'D' (Delete) operations, implementing
     * the traditional XCTL program transfer pattern through REST redirects.
     * 
     * <p><b>COBOL Program Reference:</b> COUSR00C.cbl lines 189-215 (selection EVALUATE statement)</p>
     * <p><b>Navigation Logic:</b> Replaces CICS XCTL with REST endpoint routing</p>
     * <p><b>Session Management:</b> Maintains CDEMO-CU00-USR-SELECTED state in Redis session</p>
     * 
     * @param selectionRequest User selection request with operation and target user
     * @param request HTTP request for session management
     * @return Routing information for next operation or error if invalid selection
     */
    @PostMapping("/select")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
        summary = "Handle user selection operations",
        description = "Process user selection for update/delete operations, " +
                     "replicating COBOL selection logic from COUSR00C"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Selection processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid selection operation"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Selected user not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<String>> selectUser(
            @Parameter(description = "User selection request with operation and target user")
            @Valid @RequestBody UserSelectionDto selectionRequest,
            
            HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing user selection - User: {}, SelectedUser: {}, Operation: {}, RequestId: {}", 
                   currentUser, selectionRequest.getSelectedUserId(), 
                   selectionRequest.getOperation(), requestId);
        
        try {
            String selectedUserId = selectionRequest.getSelectedUserId();
            String operation = selectionRequest.getOperation().toUpperCase();
            
            // Validate selected user exists
            if (!userService.existsByUserId(selectedUserId)) {
                logger.warn("Selection of non-existent user - UserId: {}, RequestId: {}", 
                           selectedUserId, requestId);
                
                UserApiResponse<String> notFoundResponse = new UserApiResponse<>(
                    MSG_USER_NOT_FOUND, requestId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            // Store selection context in session (matching CDEMO-CU00-USR-SELECTED)
            HttpSession session = request.getSession();
            session.setAttribute("selectedUserId", selectedUserId);
            session.setAttribute("selectionOperation", operation);
            session.setAttribute("selectionTime", LocalDateTime.now());
            session.setAttribute("selectionPerformedBy", currentUser);
            
            // Process selection based on operation (matching COBOL EVALUATE logic)
            String responseMessage;
            String targetEndpoint;
            
            switch (operation) {
                case "U":
                    // Update operation - equivalent to XCTL PROGRAM('COUSR02C')
                    responseMessage = String.format("User %s selected for update. " +
                        "Use PUT /api/v1/users/%s to proceed with update.", 
                        selectedUserId, selectedUserId);
                    targetEndpoint = "/api/v1/users/" + selectedUserId;
                    
                    // Check authorization for update operation
                    if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                        logger.warn("Non-admin user attempted update selection - User: {}, RequestId: {}", 
                                   currentUser, requestId);
                        UserApiResponse<String> forbiddenResponse = new UserApiResponse<>(
                            "Update operations require administrative privileges", requestId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResponse);
                    }
                    break;
                    
                case "D":
                    // Delete operation - equivalent to XCTL PROGRAM('COUSR03C')
                    responseMessage = String.format("User %s selected for deletion. " +
                        "Use DELETE /api/v1/users/%s?confirm=true to proceed with deletion.", 
                        selectedUserId, selectedUserId);
                    targetEndpoint = "/api/v1/users/" + selectedUserId;
                    
                    // Check authorization for delete operation  
                    if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                        logger.warn("Non-admin user attempted delete selection - User: {}, RequestId: {}", 
                                   currentUser, requestId);
                        UserApiResponse<String> forbiddenResponse = new UserApiResponse<>(
                            "Delete operations require administrative privileges", requestId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenResponse);
                    }
                    break;
                    
                default:
                    // Invalid operation - matches COBOL WHEN OTHER clause
                    logger.warn("Invalid selection operation - Operation: {}, User: {}, RequestId: {}", 
                               operation, currentUser, requestId);
                    
                    UserApiResponse<String> invalidResponse = new UserApiResponse<>(
                        MSG_INVALID_SELECTION, requestId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }
            
            // Store target endpoint for client navigation
            session.setAttribute("targetEndpoint", targetEndpoint);
            session.setAttribute("operationDescription", selectionRequest.getOperationDescription());
            
            logger.info("User selection processed successfully - User: {}, Operation: {}, Target: {}, RequestId: {}", 
                       currentUser, operation, selectedUserId, requestId);
            
            UserApiResponse<String> response = new UserApiResponse<>(responseMessage, requestId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing user selection - RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            UserApiResponse<String> errorResponse = new UserApiResponse<>(
                MSG_INVALID_KEY, requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Clear current session state and navigation context.
     * 
     * This endpoint replicates the CLEAR-CURRENT-SCREEN functionality from COBOL programs
     * (COUSR01C, COUSR02C, COUSR03C) which resets form fields and session state, providing
     * a clean slate for new operations.
     * 
     * <p><b>COBOL Program Reference:</b> Multiple programs - CLEAR-CURRENT-SCREEN sections</p>
     * <p><b>Session Reset:</b> Clears all user-related session attributes</p>
     * 
     * @param request HTTP request for session management
     * @return Confirmation message indicating session has been cleared
     */
    @PostMapping("/clear-session")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
        summary = "Clear session state",
        description = "Clear current session navigation and form state, " +
                     "replicating COBOL CLEAR-CURRENT-SCREEN functionality"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session cleared successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserApiResponse<String>> clearSession(HttpServletRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        
        logger.info("Processing clear session request - User: {}, RequestId: {}", currentUser, requestId);
        
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                // Clear user-related session attributes (matching INITIALIZE-ALL-FIELDS logic)
                session.removeAttribute("selectedUserId");
                session.removeAttribute("selectionOperation");
                session.removeAttribute("selectionTime");
                session.removeAttribute("currentPage");
                session.removeAttribute("searchCriteria");
                session.removeAttribute("lastCreatedUserId");
                session.removeAttribute("lastUpdatedUserId");
                session.removeAttribute("lastDeletedUserId");
                session.removeAttribute("targetEndpoint");
                session.removeAttribute("operationDescription");
                
                logger.info("Session cleared successfully - User: {}, RequestId: {}", currentUser, requestId);
            }
            
            UserApiResponse<String> response = new UserApiResponse<>(
                "Session state cleared successfully", requestId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error clearing session - User: {}, RequestId: {}, Error: {}", 
                        currentUser, requestId, e.getMessage(), e);
            
            UserApiResponse<String> errorResponse = new UserApiResponse<>(
                "Error clearing session state", requestId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}