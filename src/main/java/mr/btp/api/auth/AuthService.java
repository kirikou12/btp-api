package mr.btp.api.auth;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import mr.btp.api.security.AppUserDetails;
import mr.btp.api.security.JwtService;
import mr.btp.api.user.User;
import mr.btp.api.user.UserRepository;
import mr.btp.api.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "error.auth.email-already-exists", "Email already exists");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role() == null ? UserRole.MANAGER : request.role());
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "error.resource.not-found",
                        "{0} not found",
                        MessageKey.of("resource.user", "User")
                ));
        return buildResponse(user);
    }

    public AuthDtos.UserResponse me(AppUserDetails principal) {
        return new AuthDtos.UserResponse(principal.id(), principal.email(), principal.fullName(), principal.role());
    }

    private AuthDtos.AuthResponse buildResponse(User user) {
        AppUserDetails details = AppUserDetails.from(user);
        return new AuthDtos.AuthResponse(
                jwtService.generateToken(details),
                "Bearer",
                new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name())
        );
    }
}
