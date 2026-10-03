package com.example.service;

import com.example.dto.AdminUserResponse;
import com.example.dto.CreateUserDTO;
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
import com.example.util.Names;
import com.example.util.Passwords;
import com.example.util.PhoneNumbers;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String ROLE_ADMIN = "ADMIN";

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

    /**
     * An admin creates a staff account. Patients register themselves, so only
     * the three staff roles are accepted. The password the admin sets is only
     * temporary: the new user must choose their own on first sign-in.
     *
     * The name and phone are cleaned exactly as on register and login, or the
     * duplicate check could miss an existing account and the new user could
     * fail to sign in with the name they were given.
     */
    @Transactional
    public AdminUserResponse createUser(CreateUserDTO request) {
        String fullName = Names.trimmed(request.getFullName());
        String phone = PhoneNumbers.normalise(request.getPhoneNumber());
        String password = request.getPassword();
        String role = request.getRole() == null ? null : request.getRole().trim().toUpperCase();

        if (fullName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter the user's full name.");
        }
        if (!PhoneNumbers.isValidLebanese(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Please enter a valid Lebanese phone number.");
        }
        Passwords.requireAcceptablePassword(password);
        if (!ROLE_DOCTOR.equals(role) && !ROLE_RECEPTIONIST.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_INVALID",
                    "Choose a role: doctor, receptionist or admin.");
        }

        if (userRepository.existsByPhoneNumberAndFullName(phone, fullName)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                    "An account already exists for this name and phone number.");
        }

        User user = new User(fullName, phone, passwordEncoder.encode(password), role);
        user.setMustChangePassword(true);
        userRepository.save(user);

        // Each role has its own row, which the rest of the app looks the user
        // up by. A doctor without one would have no schedule or queue.
        switch (role) {
            case ROLE_DOCTOR -> doctorRepository.save(
                    new Doctor(user, Names.trimmed(request.getSpecialization())));
            case ROLE_RECEPTIONIST -> receptionistRepository.save(new Receptionist(user));
            default -> adminRepository.save(new Admin(user));
        }

        return AdminUserResponse.of(user);
    }

    public UserResponse updateUser(Integer id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND", "User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);

        return new UserResponse(user.getUserId(), user.getFullName(),
                user.getPhoneNumber(), user.getRole());
    }
}
