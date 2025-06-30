/*
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.carddemo.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * UserMapper provides comprehensive mapping functionality between User entities and UserDto objects.
 * This mapper handles secure password operations, role transformations, audit field management,
 * and data type conversions for clean architectural separation between persistence and API layers.
 * 
 * Converted from COBOL programs:
 * - COUSR01C: User creation operations
 * - COUSR02C: User update operations
 * 
 * Maps COBOL copybook CSUSR01Y.cpy structure:
 * - SEC-USR-ID (PIC X(08)) -> userId
 * - SEC-USR-FNAME (PIC X(20)) -> firstName  
 * - SEC-USR-LNAME (PIC X(20)) -> lastName
 * - SEC-USR-PWD (PIC X(08)) -> password (BCrypt hashed)
 * - SEC-USR-TYPE (PIC X(01)) -> userType with Spring Security authority mapping
 */
@Component
public class UserMapper {

    private final BCryptPasswordEncoder passwordEncoder;
    
    // COBOL-compatible date format for audit fields
    private static final DateTimeFormatter COBOL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COBOL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // COBOL user type mappings to Spring Security authorities
    private static final String USER_TYPE_REGULAR = "R";
    private static final String USER_TYPE_ADMIN = "A";
    private static final String AUTHORITY_USER = "ROLE_USER";
    private static final String AUTHORITY_ADMIN = "ROLE_ADMIN";

    public UserMapper() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Maps User entity to UserDto for API layer consumption.
     * Excludes sensitive fields and applies role-based filtering.
     * 
     * @param user the User entity to map
     * @return UserDto with mapped fields, null if input is null
     */
    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }

        UserDto dto = new UserDto();
        
        // Direct field mappings from COBOL structure
        dto.setUserId(user.getUserId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUserType(user.getUserType());
        
        // Derived fields for API convenience
        dto.setFullName(buildFullName(user.getFirstName(), user.getLastName()));
        dto.setDisplayName(buildDisplayName(user.getFirstName(), user.getLastName()));
        
        // Spring Security authority mapping
        dto.setAuthorities(mapUserTypeToAuthorities(user.getUserType()));
        dto.setIsAdministrator(USER_TYPE_ADMIN.equals(user.getUserType()));
        
        // Audit field mappings with COBOL date format conversion
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setLastSignonDate(user.getLastSignonDate());
        dto.setLastSignonTime(user.getLastSignonTime());
        
        // Version field for JPA optimistic locking
        dto.setVersion(user.getVersion());
        
        // Note: Password field is intentionally excluded for security
        
        return dto;
    }

    /**
     * Maps UserDto to User entity for persistence layer operations.
     * Handles password hashing and audit field management.
     * 
     * @param dto the UserDto to map
     * @return User entity with mapped fields, null if input is null
     */
    public User toEntity(UserDto dto) {
        if (dto == null) {
            return null;
        }

        User user = new User();
        
        // Direct field mappings to COBOL structure equivalents
        user.setUserId(dto.getUserId());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUserType(dto.getUserType());
        
        // Audit field mappings
        user.setCreatedAt(dto.getCreatedAt());
        user.setUpdatedAt(dto.getUpdatedAt());
        user.setLastSignonDate(dto.getLastSignonDate());
        user.setLastSignonTime(dto.getLastSignonTime());
        user.setVersion(dto.getVersion());
        
        // Password handling - only if provided in DTO
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(hashPassword(dto.getPassword()));
        }
        
        return user;
    }

    /**
     * Maps User entity to UserDto with administrative access.
     * Includes additional fields for administrative operations.
     * 
     * @param user the User entity to map
     * @param includeAuditInfo whether to include detailed audit information
     * @return UserDto with administrative fields included
     */
    public UserDto toDtoForAdmin(User user, boolean includeAuditInfo) {
        if (user == null) {
            return null;
        }

        UserDto dto = toDto(user);
        
        if (includeAuditInfo) {
            // Additional audit information for administrators
            dto.setPasswordLastChanged(user.getPasswordLastChanged());
            dto.setFailedLoginAttempts(user.getFailedLoginAttempts());
            dto.setAccountLocked(user.getAccountLocked());
            dto.setAccountExpired(user.getAccountExpired());
            dto.setCredentialsExpired(user.getCredentialsExpired());
        }
        
        return dto;
    }

    /**
     * Creates a new User entity for user creation operations.
     * Automatically sets audit timestamps and hashes password.
     * 
     * @param dto the UserDto containing user creation data
     * @param clearTextPassword the clear text password to hash
     * @return User entity ready for persistence
     */
    public User createNewUserEntity(UserDto dto, String clearTextPassword) {
        if (dto == null) {
            return null;
        }

        User user = toEntity(dto);
        
        // Set creation audit fields
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setPasswordLastChanged(now);
        
        // Hash password for secure storage
        if (StringUtils.hasText(clearTextPassword)) {
            user.setPassword(hashPassword(clearTextPassword));
        }
        
        // Initialize security fields
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setAccountExpired(false);
        user.setCredentialsExpired(false);
        
        // Set initial version for optimistic locking
        user.setVersion(0L);
        
        return user;
    }

    /**
     * Updates an existing User entity with data from UserDto.
     * Preserves creation audit fields and manages password updates.
     * 
     * @param existingUser the existing User entity to update
     * @param dto the UserDto containing updated data
     * @param newPassword optional new password to set (null to keep existing)
     * @return updated User entity
     */
    public User updateUserEntity(User existingUser, UserDto dto, String newPassword) {
        if (existingUser == null || dto == null) {
            return existingUser;
        }

        // Update basic fields if changed
        if (StringUtils.hasText(dto.getFirstName())) {
            existingUser.setFirstName(dto.getFirstName());
        }
        if (StringUtils.hasText(dto.getLastName())) {
            existingUser.setLastName(dto.getLastName());
        }
        if (StringUtils.hasText(dto.getUserType())) {
            existingUser.setUserType(dto.getUserType());
        }
        
        // Update password if provided
        if (StringUtils.hasText(newPassword)) {
            existingUser.setPassword(hashPassword(newPassword));
            existingUser.setPasswordLastChanged(LocalDateTime.now());
        }
        
        // Update audit timestamp
        existingUser.setUpdatedAt(LocalDateTime.now());
        
        return existingUser;
    }

    /**
     * Maps a list of User entities to UserDto list.
     * 
     * @param users the list of User entities to map
     * @return list of UserDto objects
     */
    public List<UserDto> toDtoList(List<User> users) {
        if (users == null) {
            return new ArrayList<>();
        }

        List<UserDto> dtoList = new ArrayList<>();
        for (User user : users) {
            UserDto dto = toDto(user);
            if (dto != null) {
                dtoList.add(dto);
            }
        }
        return dtoList;
    }

    /**
     * Maps a list of User entities to UserDto list with administrative access.
     * 
     * @param users the list of User entities to map
     * @param includeAuditInfo whether to include audit information
     * @return list of UserDto objects with administrative fields
     */
    public List<UserDto> toDtoListForAdmin(List<User> users, boolean includeAuditInfo) {
        if (users == null) {
            return new ArrayList<>();
        }

        List<UserDto> dtoList = new ArrayList<>();
        for (User user : users) {
            UserDto dto = toDtoForAdmin(user, includeAuditInfo);
            if (dto != null) {
                dtoList.add(dto);
            }
        }
        return dtoList;
    }

    /**
     * Validates password strength according to system requirements.
     * 
     * @param password the password to validate
     * @return true if password meets requirements, false otherwise
     */
    public boolean isValidPassword(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        
        // COBOL system compatible validation (8 characters max as per SEC-USR-PWD PIC X(08))
        if (password.length() < 4 || password.length() > 8) {
            return false;
        }
        
        // Basic validation - alphanumeric characters
        return password.matches("^[a-zA-Z0-9]+$");
    }

    /**
     * Validates user type value against COBOL-compatible values.
     * 
     * @param userType the user type to validate
     * @return true if valid user type, false otherwise
     */
    public boolean isValidUserType(String userType) {
        return USER_TYPE_REGULAR.equals(userType) || USER_TYPE_ADMIN.equals(userType);
    }

    /**
     * Checks if a password matches the stored hash.
     * 
     * @param clearTextPassword the clear text password to check
     * @param hashedPassword the stored hashed password
     * @return true if passwords match, false otherwise
     */
    public boolean verifyPassword(String clearTextPassword, String hashedPassword) {
        if (!StringUtils.hasText(clearTextPassword) || !StringUtils.hasText(hashedPassword)) {
            return false;
        }
        return passwordEncoder.matches(clearTextPassword, hashedPassword);
    }

    /**
     * Converts COBOL date string (YYYY-MM-DD) to LocalDateTime.
     * 
     * @param cobolDate the COBOL formatted date string
     * @return LocalDateTime object, null if invalid date
     */
    public LocalDateTime parseCobolDate(String cobolDate) {
        if (!StringUtils.hasText(cobolDate)) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(cobolDate + "T00:00:00");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts LocalDateTime to COBOL-compatible date string (YYYY-MM-DD).
     * 
     * @param dateTime the LocalDateTime to convert
     * @return COBOL formatted date string, empty string if null
     */
    public String formatCobolDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(COBOL_DATE_FORMAT);
    }

    /**
     * Converts LocalDateTime to COBOL-compatible time string (HH:MM:SS).
     * 
     * @param dateTime the LocalDateTime to convert
     * @return COBOL formatted time string, empty string if null
     */
    public String formatCobolTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(COBOL_TIME_FORMAT);
    }

    // Private helper methods

    /**
     * Hashes a password using BCrypt encryption.
     * 
     * @param clearTextPassword the password to hash
     * @return BCrypt hashed password
     */
    private String hashPassword(String clearTextPassword) {
        if (!StringUtils.hasText(clearTextPassword)) {
            return null;
        }
        return passwordEncoder.encode(clearTextPassword);
    }

    /**
     * Maps COBOL user type to Spring Security authorities.
     * 
     * @param userType the COBOL user type (R=Regular, A=Admin)
     * @return list of Spring Security authority strings
     */
    private List<String> mapUserTypeToAuthorities(String userType) {
        List<String> authorities = new ArrayList<>();
        
        // All users get basic user authority
        authorities.add(AUTHORITY_USER);
        
        // Admin users get additional admin authority
        if (USER_TYPE_ADMIN.equals(userType)) {
            authorities.add(AUTHORITY_ADMIN);
        }
        
        return authorities;
    }

    /**
     * Builds full name from first and last name components.
     * 
     * @param firstName user's first name
     * @param lastName user's last name
     * @return formatted full name
     */
    private String buildFullName(String firstName, String lastName) {
        StringBuilder fullName = new StringBuilder();
        
        if (StringUtils.hasText(firstName)) {
            fullName.append(firstName.trim());
        }
        
        if (StringUtils.hasText(lastName)) {
            if (fullName.length() > 0) {
                fullName.append(" ");
            }
            fullName.append(lastName.trim());
        }
        
        return fullName.toString();
    }

    /**
     * Builds display name for UI purposes (Last, First format).
     * 
     * @param firstName user's first name
     * @param lastName user's last name
     * @return formatted display name
     */
    private String buildDisplayName(String firstName, String lastName) {
        StringBuilder displayName = new StringBuilder();
        
        if (StringUtils.hasText(lastName)) {
            displayName.append(lastName.trim());
        }
        
        if (StringUtils.hasText(firstName)) {
            if (displayName.length() > 0) {
                displayName.append(", ");
            }
            displayName.append(firstName.trim());
        }
        
        return displayName.toString();
    }
}