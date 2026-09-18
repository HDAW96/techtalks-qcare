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
import com.example.util.PhoneNumbers;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Everyone who registers online is a patient. Staff accounts are made at the clinic. */
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
        String fullName = trimmed(details.getFullName());
        String phone = PhoneNumbers.normalise(details.getPhoneNumber());
        String password = details.getPassword();

        if (fullName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter your full name.");
        }
        if (!PhoneNumbers.isValidLebanese(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Please enter a valid Lebanese phone number.");
        }
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT",
                    "Your password must be at least 8 characters.");
        }

        // The same three rules the register form checks before submitting
        // (RegisterPage.jsx), so the form and the server never disagree.
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
        String fullName = trimmed(credentials.getFullName());
        String phone = PhoneNumbers.normalise(credentials.getPhoneNumber());

        // One message for every failure: saying which part was wrong tells an
        // attacker which phone numbers and names are registered.
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
        return respond(user, patientRepository.findByUser(user).orElse(null));
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

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[ ]+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
