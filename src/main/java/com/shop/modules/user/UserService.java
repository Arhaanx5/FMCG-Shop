package com.shop.modules.user;

import com.shop.modules.user.dto.CreateUserRequest;
import com.shop.modules.user.dto.UpdateUserRequest;
import com.shop.modules.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<UserResponse> getDeliveryBoys() {
        return userRepository
                .findByRoleAndActive(
                        UserRole.DELIVERY_BOY, true)
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found: " + id)));
    }


    public UserResponse createUser(CreateUserRequest req) {

        // Trim whitespace
        String name = req.getName().trim();
        String phone = req.getPhone().trim();

        // Check blank after trim
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Name cannot be blank or whitespace");
        }
        if (phone.isBlank()) {
            throw new RuntimeException(
                    "Phone cannot be blank or whitespace");
        }

        // Check duplicate phone
        if (userRepository.existsByPhone(phone)) {
            throw new RuntimeException(
                    "User with phone "
                            + phone
                            + " already exists");
        }

        User user = User.builder()
                .name(name)
                .phone(phone)
                .role(req.getRole())
                .passwordHash(
                        passwordEncoder.encode(req.getPassword()))
                .active(true)
                .mustChangePassword(true)
                .monthlySalary(req.getMonthlySalary())
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(
            UUID id, UpdateUserRequest req) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found: " + id));

        // Trim whitespace
        String name = req.getName().trim();
        String phone = req.getPhone().trim();

        // Check blank after trim
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Name cannot be blank or whitespace");
        }

        // Check duplicate phone — exclude current user
        if (!user.getPhone().equals(phone)
                && userRepository.existsByPhone(phone)) {
            throw new RuntimeException(
                    "Phone " + phone + " already in use");
        }

        user.setName(name);
        user.setPhone(phone);
        user.setRole(req.getRole());
        user.setMonthlySalary(req.getMonthlySalary());

        // Update password only if provided and non-blank.
        // Also enforce max length to prevent BCrypt DoS.
        if (req.getPassword() != null
                && !req.getPassword().isBlank()) {
            if (req.getPassword().length() > 72) {
                throw new RuntimeException("Password must not exceed 72 characters");
            }
            user.setPasswordHash(
                    passwordEncoder.encode(req.getPassword()));
        }
        // If password is null or blank → keep existing hash unchanged

        return userMapper.toResponse(userRepository.save(user));
    }

    public void toggleActive(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found: " + id));
        user.setActive(!user.getActive());
        userRepository.save(user);
    }

    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException(
                    "User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    public void changePassword(
            String phone,
            String currentPassword,
            String newPassword) {

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        if (!passwordEncoder.matches(
                currentPassword, user.getPasswordHash())) {
            throw new RuntimeException(
                    "Current password is incorrect");
        }

        if (newPassword.isBlank()) {
            throw new RuntimeException(
                    "New password cannot be blank");
        }

        if (newPassword.length() < 8 || newPassword.length() > 72) {
            throw new RuntimeException(
                    "Password must be between 8 and 72 characters");
        }

        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$";
        if (!newPassword.matches(regex)) {
            throw new RuntimeException(
                    "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character");
        }

        user.setPasswordHash(
                passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public User updateLiveLocation(String phone, Double lat, Double lng) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastLatitude(lat);
        user.setLastLongitude(lng);
        user.setLastLocationTime(java.time.LocalDateTime.now());
        return userRepository.save(user);
    }
}