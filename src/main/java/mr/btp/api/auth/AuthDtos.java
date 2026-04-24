package mr.btp.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mr.btp.api.user.UserRole;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,
            @NotBlank(message = "{validation.password.required}") @Size(min = 8, message = "{validation.password.min}") String password,
            @NotBlank(message = "{validation.full-name.required}") String fullName,
            UserRole role
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,
            @NotBlank(message = "{validation.password.required}") String password
    ) {
    }

    public record AuthResponse(String accessToken, String tokenType, UserResponse user) {
    }

    public record UserResponse(Long id, String email, String fullName, String role) {
    }
}
