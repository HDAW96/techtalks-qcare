package com.example.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.AdminUserResponse;
import com.example.dto.UserRequest;
import com.example.dto.UserResponse;
import com.example.entity.Admin;
import com.example.entity.Doctor;
import com.example.entity.Receptionist;
import com.example.entity.User;
import com.example.repository.AdminRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.ReceptionistRepository;
import com.example.repository.UserRepository;
import com.example.util.PhoneNumbers;
import com.example.web.ApiException;

import java.util.List;
import java.util.Set;

@Service 
public class UserService {
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> VALID_ROLES = Set.of(ROLE_DOCTOR, ROLE_RECEPTIONIST, ROLE_ADMIN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final ReceptionistRepository receptionistRepository;
    private final AdminRepository adminRepository;

    public UserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DoctorRepository doctorRepository,
            ReceptionistRepository receptionistRepository,
            AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
        this.receptionistRepository = receptionistRepository;
        this.adminRepository = adminRepository;
    }

    public List<AdminUserResponse> searchUsers(String role, String search) {
        List<User> users = userRepository.searchUsers(role, search);
        return users.stream()
                .map(AdminUserResponse::of)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(Integer id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found"
                ));

        String oldRole = user.getRole();

        // Validate and process full name
        if (request.getFullName() != null) {
            String fullName = trimmed(request.getFullName());
            if (fullName == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                        "Please enter a valid full name.");
            }
            user.setFullName(fullName);
        }

        // Validate and process phone number
        if (request.getPhoneNumber() != null) {
            String phone = PhoneNumbers.normalise(request.getPhoneNumber());
            if (phone == null || !PhoneNumbers.isValidLebanese(phone)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                        "Please enter a valid Lebanese phone number.");
            }
            user.setPhoneNumber(phone);
        }

        // Validate and process role
        if (request.getRole() != null) {
            String role = request.getRole().trim().toUpperCase();
            if (!VALID_ROLES.contains(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_INVALID",
                        "Role must be DOCTOR, RECEPTIONIST, or ADMIN.");
            }
            user.setRole(role);
        }

        // Validate and process password
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            requireAcceptablePassword(request.getPassword());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setMustChangePassword(true);
        }

        // Check for duplicate (phone, fullName) pair
        if (request.getFullName() != null || request.getPhoneNumber() != null) {
            User duplicate = userRepository.findByPhoneNumberAndFullName(
                    user.getPhoneNumber(),
                    user.getFullName()
            ).orElse(null);

            if (duplicate != null && !duplicate.getUserId().equals(user.getUserId())) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS",
                        "An account already exists for this name and phone number.");
            }
        }

        userRepository.save(user);

        // Create role-specific row if role changed to DOCTOR/RECEPTIONIST/ADMIN
        String newRole = user.getRole();
        if (!oldRole.equals(newRole)) {
            if (ROLE_DOCTOR.equals(newRole)) {
                boolean hasDoctorRow = doctorRepository.findByUser(user).isPresent();
                if (!hasDoctorRow) {
                    doctorRepository.save(new Doctor(user, null));
                }
            } else if (ROLE_RECEPTIONIST.equals(newRole)) {
                boolean hasReceptionistRow = receptionistRepository.findByUser(user).isPresent();
                if (!hasReceptionistRow) {
                    receptionistRepository.save(new Receptionist(user));
                }
            } else if (ROLE_ADMIN.equals(newRole)) {
                boolean hasAdminRow = adminRepository.findByUser(user).isPresent();
                if (!hasAdminRow) {
                    adminRepository.save(new Admin(user));
                }
            }
        }

        return new UserResponse(
                user.getUserId(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole()
        );
    }

    /**
     * The rules every password must meet when updating. Same as registration and
     * password change validation, so they never disagree.
     */
    private static void requireAcceptablePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT",
                    "Your password must be at least 8 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_UPPERCASE",
                    "Your password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_LOWERCASE",
                    "Your password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NO_SPECIAL",
                    "Your password must contain at least one special character.");
        }
    }

    /**
     * Trim whitespace and normalize names. Returns null if the result is empty.
     * Same logic as AuthService so both paths agree on what a name is.
     */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[ ]+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
