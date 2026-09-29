package vn.edu.khtn8.identity.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.khtn8.identity.repository.UserRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public AuthenticatedUser loadUserByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public AuthenticatedUser loadById(Long userId) {
        return users.findById(userId)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
