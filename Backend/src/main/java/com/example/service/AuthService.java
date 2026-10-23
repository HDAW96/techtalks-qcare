package com.example.service;

import com.example.dto.AuthResponse;
import com.example.dto.LoginDTO;
import com.example.dto.RegisterDTO;
import com.example.dto.UserResponse;

import com.example.entity.Patient;
import com.example.entity.User;

import com.example.repository.PatientRepository;
import com.example.repository.UserRepository;

import com.example.security.JwtService;

import com.example.util.Names;
import com.example.util.Passwords;
import com.example.util.PhoneNumbers;

import com.example.web.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /**
     * Everyone who registers online is a patient. Staff accounts are made at the
     * clinic.
     */
    private static final String ROLE_PATIENT = "PATIENT";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterDTO details) {
        String fullName = Names.trimmed(details.getFullName());
        String phone = PhoneNumbers.normalise(details.getPhoneNumber());
        String password = details.getPassword();

        if (fullName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter your full name.");
        }
        Names.requireWithinLimit(fullName);
        if (!PhoneNumbers.isValidLebanese(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Please enter a valid Lebanese phone number.");
        }
        Passwords.requireAcceptablePassword(password);

        if (userRepository.existsByPhoneNumberAndFullName(phone, fullName)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                    "An account already exists for this name and phone number. Try signing in.");
        }

        User user = new User(fullName, phone, passwordEncoder.encode(password), ROLE_PATIENT);
        userRepository.save(user);

        // This person may already be known to the clinic as a walk-in. If so,
        // attach the new account to that record instead of creating a second
        // one, so their history is not split in two.
        Patient patient = patientRepository.findByPhoneNumberAndFullName(phone, fullName)
                .orElseGet(() -> new Patient(fullName, phone));
        patient.setUser(user);
        patientRepository.save(patient);

        return respond(user, patient);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginDTO credentials) {
    String fullName = Names.trimmed(credentials.getFullName());
    String phone = PhoneNumbers.normalise(credentials.getPhoneNumber());

    ApiException wrong = new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
            "Those sign-in details are not right. Please check and try again.");

    if (fullName == null || phone == null || credentials.getPassword() == null) {
        throw wrong;
    }
    
    User user = userRepository.findByPhoneNumberAndFullName(phone, fullName)
            .orElseThrow(() -> wrong);

    if (!passwordEncoder.matches(credentials.getPassword(), user.getPasswordHash())) {
        throw wrong;
    }

    // Checked only after the password, so a wrong guess cannot tell anyone
    // that this name and phone belong to a removed account.
    if (!Boolean.TRUE.equals(user.getActive())) {
        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "ACCOUNT_DEACTIVATED",
            "This account has been deactivated."
        );
    }
    return respond(user, patientRepository.findByUser(user).orElse(null));
}

    /**
     * Replaces the signed-in user's password. The current one is asked for
     * again, so a phone left signed in at the desk cannot be used to lock its
     * owner out. Clears must_change_password, which is how the seeded staff
     * accounts stop being flagged.
     *
     * A wrong current password is a 400, not a 401: the frontend treats 401
     * as "your session ended" and would sign the user out.
     */
    @Transactional
    public UserResponse changePassword(Integer userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));

        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_WRONG",
                    "Your current password is not right.");
        }
        Passwords.requireAcceptablePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD",
                    "Please choose a password different from your current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        return UserResponse.of(user, patientRepository.findByUser(user).orElse(null));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Please sign in again."));
        return UserResponse.of(user, patientRepository.findByUser(user).orElse(null));
    }

    private AuthResponse respond(User user, Patient patient) {
        String token = jwtService.issue(user.getUserId(), user.getRole());
        return new AuthResponse(token, UserResponse.of(user, patient));
    }
}
