package vn.career.auth.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.api.dto.AuthResponse;
import vn.career.auth.api.dto.LoginRequest;
import vn.career.auth.api.dto.RegisterRequest;
import vn.career.auth.api.dto.UserResponse;
import vn.career.auth.domain.Role;
import vn.career.auth.domain.User;
import vn.career.auth.domain.UserStatus;
import vn.career.auth.infrastructure.LoginAttemptLimiter;
import vn.career.auth.infrastructure.UserMapper;
import vn.career.auth.infrastructure.UserRepository;
import vn.career.auth.infrastructure.security.JwtService;
import vn.career.common.api.ErrorDetail;
import vn.career.common.exception.AppException;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.UnauthorizedException;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter loginLimiter;
    private final ConsentProperties consentProperties;
    private final UserMapper userMapper;
    private final Clock clock;
    // Compared against when the email is unknown, so response time does not reveal which emails exist.
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, LoginAttemptLimiter loginLimiter,
                       ConsentProperties consentProperties, UserMapper userMapper, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.loginLimiter = loginLimiter;
        this.consentProperties = consentProperties;
        this.userMapper = userMapper;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.role() != Role.STUDENT && request.role() != Role.PARENT) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Only STUDENT and PARENT accounts can be self-registered");
        }
        if (request.role() == Role.STUDENT && request.dateOfBirth() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("dateOfBirth", "is required for students")));
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }

        boolean needsConsent = User.requiresParentalConsent(
                request.role(), request.dateOfBirth(), consentProperties.minAge(), LocalDate.now(clock));
        User user = User.create(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
                request.dateOfBirth(), request.role(),
                needsConsent ? UserStatus.PENDING_PARENT_CONSENT : UserStatus.ACTIVE);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email raced past the exists check.
            throw emailTaken();
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        loginLimiter.assertNotLocked(email);

        User user = users.findByEmail(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyPasswordHash);
        if (user == null || !passwordMatches) {
            loginLimiter.recordFailure(email);
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
        if (user.isLocked()) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED, "This account is locked");
        }
        loginLimiter.reset(email);
        return buildAuthResponse(user, refreshTokens.issue(user));
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = rotation.user();
        if (user.isLocked()) {
            refreshTokens.revokeAll(user.getId());
            throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED, "This account is locked");
        }
        return buildAuthResponse(user, rotation.refreshToken());
    }

    @Transactional
    public void logout(UUID userId, String refreshToken) {
        refreshTokens.revoke(refreshToken, userId);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        // A deleted (anonymised) or locked account may still hold an unexpired access token: treat it as logged out.
        return users.findById(userId)
                .filter(user -> !user.isLocked())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        return new AuthResponse(jwtService.generateAccessToken(user), refreshToken, TOKEN_TYPE,
                jwtService.accessTokenTtlSeconds(), userMapper.toResponse(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException emailTaken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "This email is already registered");
    }
}
