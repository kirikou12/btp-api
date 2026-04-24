package mr.btp.api.security;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import mr.btp.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "error.resource.not-found",
                        "{0} not found",
                        MessageKey.of("resource.user", "User")
                ));
    }
}
