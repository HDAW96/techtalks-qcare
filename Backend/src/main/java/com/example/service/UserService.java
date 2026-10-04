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
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> STAFF_ROLES = Set.of(ROLE_DOCTOR, ROLE_RECEPTIONIST, ROLE_ADMIN);

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
        if (role == null || !STAFF_ROLES.contains(role)) {
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

    /**
     * An admin edits a user. Only the fields sent are changed, and each one is
     * checked by the same rules as createUser. A password set here is again only
     * temporary, so the user must choose their own on next sign-in.
     */
    @Transactional
    public UserResponse updateUser(Integer id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND", "User not found"));

        // Everything is checked before the user is touched: the duplicate lookup
        // below would otherwise flush a half-edited user and hit the database's
        // own unique (phone, name) rule as a 500 instead of a clear 409.
        String fullName = user.getFullName();
        if (request.getFullName() != null) {
            fullName = Names.trimmed(request.getFullName());
            if (fullName == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                        "Please enter a valid full name.");
            }
        }

        String phone = user.getPhoneNumber();
        if (request.getPhoneNumber() != null) {
            phone = PhoneNumbers.normalise(request.getPhoneNumber());
            if (!PhoneNumbers.isValidLebanese(phone)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                        "Please enter a valid Lebanese phone number.");
            }
        }

        String oldRole = user.getRole();
        String newRole = oldRole;
        if (request.getRole() != null) {
            newRole = request.getRole().trim().toUpperCase();
            if (!STAFF_ROLES.contains(newRole)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_INVALID",
                        "Role must be DOCTOR, RECEPTIONIST, or ADMIN.");
            }
        }

        // The same rule as deactivateUser: moving the last admin to another
        // role would leave nobody able to open the admin pages.
        if (ROLE_ADMIN.equals(oldRole) && !ROLE_ADMIN.equals(newRole)
                && Boolean.TRUE.equals(user.getActive())
                && userRepository.countByRoleAndActiveTrue(ROLE_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_ADMIN",
                    "The last remaining admin cannot be given another role.");
        }

        String password = request.getPassword();
        boolean newPassword = password != null && !password.isBlank();
        if (newPassword) {
            Passwords.requireAcceptablePassword(password);
        }

        boolean sameAccount = fullName.equals(user.getFullName()) && phone.equals(user.getPhoneNumber());
        if (!sameAccount && userRepository.existsByPhoneNumberAndFullName(phone, fullName)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                    "An account already exists for this name and phone number.");
        }

        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setRole(newRole);
        if (newPassword) {
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setMustChangePassword(true);
        }
        userRepository.save(user);

        // A user moved to a new role needs that role's row, or the rest of the
        // app cannot find them (a doctor with no row has no schedule or queue).
        if (!newRole.equals(oldRole)) {
            switch (newRole) {
                case ROLE_DOCTOR -> {
                    if (doctorRepository.findByUser(user).isEmpty()) {
                        doctorRepository.save(new Doctor(user, null));
                    }
                }
                case ROLE_RECEPTIONIST -> {
                    if (receptionistRepository.findByUser(user).isEmpty()) {
                        receptionistRepository.save(new Receptionist(user));
                    }
                }
                default -> {
                    if (adminRepository.findByUser(user).isEmpty()) {
                        adminRepository.save(new Admin(user));
                    }
                }
            }
        }

        return new UserResponse(user.getUserId(), user.getFullName(),
                user.getPhoneNumber(), user.getRole());
    }
    @Transactional
   public void deactivateUser(Integer id, Authentication authentication) {

    // 1. Find the target user
    User user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found"
            ));

    // 2. Get the admin who is making the request
    Integer actingAdminId = Integer.valueOf(authentication.getName());

    // 3. Admin cannot deactivate themselves
    if (actingAdminId.equals(user.getUserId())) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "CANNOT_DEACTIVATE_SELF",
                "You cannot deactivate your own account."
        );
    }

    // 4. Reject already deactivated users
    if (!Boolean.TRUE.equals(user.getActive())) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "USER_ALREADY_DEACTIVATED",
                "User is already deactivated."
        );
    }

    // 5. The last active admin cannot be deactivated
    if (ROLE_ADMIN.equals(user.getRole())) {

        long activeAdminCount =
                userRepository.countByRoleAndActiveTrue(ROLE_ADMIN);

        if (activeAdminCount <= 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "LAST_ADMIN",
                    "The last remaining admin cannot be deactivated."
            );
        }
    }

    // 6. Soft delete
    user.setActive(false);
    userRepository.save(user);
}
}
