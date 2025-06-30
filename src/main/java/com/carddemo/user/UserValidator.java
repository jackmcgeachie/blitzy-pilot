package com.carddemo.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UserValidator - Custom validation component implementing COBOL program validation logic
 * through Spring Validation framework, providing business rule validation, field constraint
 * checking, and extensible validation capabilities for user management operations.
 * 
 * This class converts COBOL 88-level conditions and validation logic from COUSR01C.cbl
 * and COUSR02C.cbl programs to Java validation patterns, ensuring identical business
 * rule enforcement in the modernized system.
 * 
 * Key Validation Requirements:
 * - User ID: 8 characters maximum, alphanumeric, required (PIC X(08))
 * - First Name: 20 characters maximum, alphabetic with spaces, required (PIC X(20))
 * - Last Name: 20 characters maximum, alphabetic with spaces, required (PIC X(20))
 * - Password: 8 characters maximum, complexity requirements, required (PIC X(08))
 * - User Type: 1 character, only 'A' (Admin) or 'U' (User) allowed (PIC X(01))
 * 
 * Business Rules Implemented:
 * - Username uniqueness validation (replacing COBOL duplicate key checking)
 * - Role code validation ensuring only administrative ('A') and user ('U') types
 * - Field length validation matching COBOL PIC clause specifications
 * - Required field validation replicating COBOL empty field checks
 */
@Component
public class UserValidator implements Validator {

    // Pattern definitions matching COBOL field validation logic
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,8}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z\\s]{1,20}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?]{1,8}$");
    
    // Valid user types matching COBOL program specifications
    private static final Set<String> VALID_USER_TYPES = new HashSet<>();
    static {
        VALID_USER_TYPES.add("A"); // Admin user type
        VALID_USER_TYPES.add("U"); // Regular user type
    }
    
    // Error message constants matching COBOL program messages
    public static final String MSG_USER_ID_REQUIRED = "User ID can NOT be empty...";
    public static final String MSG_FIRST_NAME_REQUIRED = "First Name can NOT be empty...";
    public static final String MSG_LAST_NAME_REQUIRED = "Last Name can NOT be empty...";
    public static final String MSG_PASSWORD_REQUIRED = "Password can NOT be empty...";
    public static final String MSG_USER_TYPE_REQUIRED = "User Type can NOT be empty...";
    public static final String MSG_USER_ID_INVALID_FORMAT = "User ID must be 1-8 alphanumeric characters";
    public static final String MSG_FIRST_NAME_INVALID_FORMAT = "First Name must be 1-20 alphabetic characters";
    public static final String MSG_LAST_NAME_INVALID_FORMAT = "Last Name must be 1-20 alphabetic characters";
    public static final String MSG_PASSWORD_INVALID_FORMAT = "Password must be 1-8 characters";
    public static final String MSG_USER_TYPE_INVALID = "User Type must be 'A' (Admin) or 'U' (User)";
    public static final String MSG_USER_ID_DUPLICATE = "User ID already exist...";
    public static final String MSG_USER_ID_NOT_FOUND = "User ID NOT found...";

    @Autowired
    private UserRepository userRepository;

    /**
     * Supports validation for UserDto objects
     * 
     * @param clazz The class to validate
     * @return true if this validator can validate the given class
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return UserDto.class.equals(clazz);
    }

    /**
     * Validates UserDto object according to COBOL program validation logic
     * Implements validation equivalent to PROCESS-ENTER-KEY and UPDATE-USER-INFO
     * paragraphs from COUSR01C.cbl and COUSR02C.cbl
     * 
     * @param target The object to validate
     * @param errors The validation errors object
     */
    @Override
    public void validate(Object target, Errors errors) {
        UserDto userDto = (UserDto) target;
        
        // Validate required fields (matching COBOL empty field checks)
        validateRequiredFields(userDto, errors);
        
        // Validate field formats and constraints
        if (!errors.hasErrors()) {
            validateFieldFormats(userDto, errors);
        }
        
        // Validate business rules
        if (!errors.hasErrors()) {
            validateBusinessRules(userDto, errors);
        }
    }

    /**
     * Validates that all required fields are present and not empty
     * Replicates COBOL validation logic from COUSR01C lines 118-147 and COUSR02C lines 180-213
     * 
     * @param userDto The user data transfer object to validate
     * @param errors The validation errors object
     */
    private void validateRequiredFields(UserDto userDto, Errors errors) {
        // User ID validation (COBOL: WHEN USERIDI OF COUSR1AI = SPACES OR LOW-VALUES)
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "userId", "user.id.required", MSG_USER_ID_REQUIRED);
        
        // First Name validation (COBOL: WHEN FNAMEI OF COUSR1AI = SPACES OR LOW-VALUES)
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "firstName", "user.firstName.required", MSG_FIRST_NAME_REQUIRED);
        
        // Last Name validation (COBOL: WHEN LNAMEI OF COUSR1AI = SPACES OR LOW-VALUES)
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "lastName", "user.lastName.required", MSG_LAST_NAME_REQUIRED);
        
        // Password validation (COBOL: WHEN PASSWDI OF COUSR1AI = SPACES OR LOW-VALUES)
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "user.password.required", MSG_PASSWORD_REQUIRED);
        
        // User Type validation (COBOL: WHEN USRTYPEI OF COUSR1AI = SPACES OR LOW-VALUES)
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "userType", "user.userType.required", MSG_USER_TYPE_REQUIRED);
    }

    /**
     * Validates field formats and length constraints matching COBOL PIC clauses
     * 
     * @param userDto The user data transfer object to validate
     * @param errors The validation errors object
     */
    private void validateFieldFormats(UserDto userDto, Errors errors) {
        // User ID format validation (PIC X(08) - 8 character alphanumeric)
        if (userDto.getUserId() != null && !USER_ID_PATTERN.matcher(userDto.getUserId()).matches()) {
            errors.rejectValue("userId", "user.id.format", MSG_USER_ID_INVALID_FORMAT);
        }
        
        // First Name format validation (PIC X(20) - 20 character alphabetic with spaces)
        if (userDto.getFirstName() != null && !NAME_PATTERN.matcher(userDto.getFirstName()).matches()) {
            errors.rejectValue("firstName", "user.firstName.format", MSG_FIRST_NAME_INVALID_FORMAT);
        }
        
        // Last Name format validation (PIC X(20) - 20 character alphabetic with spaces)
        if (userDto.getLastName() != null && !NAME_PATTERN.matcher(userDto.getLastName()).matches()) {
            errors.rejectValue("lastName", "user.lastName.format", MSG_LAST_NAME_INVALID_FORMAT);
        }
        
        // Password format validation (PIC X(08) - 8 character with complexity requirements)
        if (userDto.getPassword() != null && !PASSWORD_PATTERN.matcher(userDto.getPassword()).matches()) {
            errors.rejectValue("password", "user.password.format", MSG_PASSWORD_INVALID_FORMAT);
        }
        
        // User Type validation (PIC X(01) - only 'A' and 'U' values allowed)
        if (userDto.getUserType() != null && !VALID_USER_TYPES.contains(userDto.getUserType().toUpperCase())) {
            errors.rejectValue("userType", "user.userType.invalid", MSG_USER_TYPE_INVALID);
        }
    }

    /**
     * Validates business rules including uniqueness and administrative privilege constraints
     * 
     * @param userDto The user data transfer object to validate
     * @param errors The validation errors object
     */
    private void validateBusinessRules(UserDto userDto, Errors errors) {
        // Username uniqueness validation (replacing COBOL duplicate key checking)
        // Replicates DFHRESP(DUPKEY) handling from COUSR01C lines 260-266
        if (userDto.getUserId() != null && userRepository != null) {
            if (userRepository.existsByUserId(userDto.getUserId())) {
                errors.rejectValue("userId", "user.id.duplicate", MSG_USER_ID_DUPLICATE);
            }
        }
    }

    /**
     * Validates user for update operations - checks if user exists
     * Replicates READ-USER-SEC-FILE logic from COUSR02C lines 320-353
     * 
     * @param userDto The user data transfer object to validate
     * @param errors The validation errors object
     */
    public void validateForUpdate(UserDto userDto, Errors errors) {
        // Basic validation first
        validate(userDto, errors);
        
        // Check if user exists for update operations
        // Replicates DFHRESP(NOTFND) handling from COUSR02C lines 340-345
        if (!errors.hasFieldErrors("userId") && userDto.getUserId() != null && userRepository != null) {
            if (!userRepository.existsByUserId(userDto.getUserId())) {
                errors.rejectValue("userId", "user.id.notfound", MSG_USER_ID_NOT_FOUND);
            }
        }
    }

    /**
     * Validates administrative privilege assignments
     * Ensures proper role-based access control for user type assignments
     * 
     * @param userDto The user data transfer object to validate
     * @param currentUserType The current user's type performing the operation
     * @param errors The validation errors object
     */
    public void validateAdministrativePrivileges(UserDto userDto, String currentUserType, Errors errors) {
        // Only admin users can create other admin users
        if ("A".equals(userDto.getUserType()) && !"A".equals(currentUserType)) {
            errors.rejectValue("userType", "user.userType.admin.privilege", 
                "Only administrators can create administrative users");
        }
    }

    // Custom validation annotation for User ID format
    @Documented
    @Constraint(validatedBy = UserIdValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidUserId {
        String message() default "User ID must be 1-8 alphanumeric characters";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Custom validation annotation for User Type
    @Documented
    @Constraint(validatedBy = UserTypeValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidUserType {
        String message() default "User Type must be 'A' (Admin) or 'U' (User)";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Custom validation annotation for unique User ID
    @Documented
    @Constraint(validatedBy = UniqueUserIdValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface UniqueUserId {
        String message() default "User ID already exist...";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // Validator implementation for User ID format
    public static class UserIdValidator implements ConstraintValidator<ValidUserId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return true; // Let @NotBlank handle null/empty validation
            }
            return USER_ID_PATTERN.matcher(value).matches();
        }
    }

    // Validator implementation for User Type
    public static class UserTypeValidator implements ConstraintValidator<ValidUserType, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return true; // Let @NotBlank handle null/empty validation
            }
            return VALID_USER_TYPES.contains(value.toUpperCase());
        }
    }

    // Validator implementation for unique User ID
    public static class UniqueUserIdValidator implements ConstraintValidator<UniqueUserId, String> {
        @Autowired
        private UserRepository userRepository;

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return true; // Let @NotBlank handle null/empty validation
            }
            return userRepository == null || !userRepository.existsByUserId(value);
        }
    }

    /**
     * Validates password complexity requirements
     * Enhanced business rule for password security beyond basic COBOL validation
     * 
     * @param password The password to validate
     * @return true if password meets complexity requirements
     */
    public boolean isValidPasswordComplexity(String password) {
        if (password == null || password.length() < 4 || password.length() > 8) {
            return false;
        }
        
        // At least one letter and one number for enhanced security
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        
        return hasLetter && hasDigit;
    }

    /**
     * Validates user modification permissions
     * Ensures users can only modify their own profiles unless they are administrators
     * 
     * @param targetUserId The user ID being modified
     * @param currentUserId The current user's ID performing the operation
     * @param currentUserType The current user's type
     * @return true if modification is allowed
     */
    public boolean canModifyUser(String targetUserId, String currentUserId, String currentUserType) {
        // Administrators can modify any user
        if ("A".equals(currentUserType)) {
            return true;
        }
        
        // Regular users can only modify their own profile
        return targetUserId != null && targetUserId.equals(currentUserId);
    }

    /**
     * Validates complete user data integrity
     * Comprehensive validation method for use in service layer operations
     * 
     * @param userDto The user data transfer object to validate
     * @param isUpdate Whether this is an update operation
     * @param currentUserType The current user's type performing the operation
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validateUser(UserDto userDto, boolean isUpdate, String currentUserType) {
        ValidationResult result = new ValidationResult();
        
        // Basic field validation
        if (userDto.getUserId() == null || userDto.getUserId().trim().isEmpty()) {
            result.addError("userId", MSG_USER_ID_REQUIRED);
        } else if (!USER_ID_PATTERN.matcher(userDto.getUserId()).matches()) {
            result.addError("userId", MSG_USER_ID_INVALID_FORMAT);
        }
        
        if (userDto.getFirstName() == null || userDto.getFirstName().trim().isEmpty()) {
            result.addError("firstName", MSG_FIRST_NAME_REQUIRED);
        } else if (!NAME_PATTERN.matcher(userDto.getFirstName()).matches()) {
            result.addError("firstName", MSG_FIRST_NAME_INVALID_FORMAT);
        }
        
        if (userDto.getLastName() == null || userDto.getLastName().trim().isEmpty()) {
            result.addError("lastName", MSG_LAST_NAME_REQUIRED);
        } else if (!NAME_PATTERN.matcher(userDto.getLastName()).matches()) {
            result.addError("lastName", MSG_LAST_NAME_INVALID_FORMAT);
        }
        
        if (userDto.getPassword() == null || userDto.getPassword().trim().isEmpty()) {
            result.addError("password", MSG_PASSWORD_REQUIRED);
        } else if (!PASSWORD_PATTERN.matcher(userDto.getPassword()).matches()) {
            result.addError("password", MSG_PASSWORD_INVALID_FORMAT);
        } else if (!isValidPasswordComplexity(userDto.getPassword())) {
            result.addError("password", "Password must contain at least one letter and one number");
        }
        
        if (userDto.getUserType() == null || userDto.getUserType().trim().isEmpty()) {
            result.addError("userType", MSG_USER_TYPE_REQUIRED);
        } else if (!VALID_USER_TYPES.contains(userDto.getUserType().toUpperCase())) {
            result.addError("userType", MSG_USER_TYPE_INVALID);
        }
        
        // Business rule validation
        if (result.isValid() && userRepository != null) {
            if (isUpdate) {
                if (!userRepository.existsByUserId(userDto.getUserId())) {
                    result.addError("userId", MSG_USER_ID_NOT_FOUND);
                }
            } else {
                if (userRepository.existsByUserId(userDto.getUserId())) {
                    result.addError("userId", MSG_USER_ID_DUPLICATE);
                }
            }
            
            // Administrative privilege validation
            if ("A".equals(userDto.getUserType()) && !"A".equals(currentUserType)) {
                result.addError("userType", "Only administrators can create administrative users");
            }
        }
        
        return result;
    }

    /**
     * ValidationResult class for comprehensive validation feedback
     */
    public static class ValidationResult {
        private final Set<String> errorFields = new HashSet<>();
        private final Set<String> errorMessages = new HashSet<>();
        
        public void addError(String field, String message) {
            errorFields.add(field);
            errorMessages.add(message);
        }
        
        public boolean isValid() {
            return errorFields.isEmpty();
        }
        
        public Set<String> getErrorFields() {
            return new HashSet<>(errorFields);
        }
        
        public Set<String> getErrorMessages() {
            return new HashSet<>(errorMessages);
        }
        
        public boolean hasFieldError(String field) {
            return errorFields.contains(field);
        }
    }
}