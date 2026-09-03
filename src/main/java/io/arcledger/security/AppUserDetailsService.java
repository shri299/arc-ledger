package io.arcledger.security;

import io.arcledger.domain.AppUser;
import io.arcledger.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository repository;

    public AppUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = repository.findByEmail(normalize(email))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        return User.withUsername(user.getEmail())
            .password(user.getPasswordHash())
            .roles("USER")
            .disabled(!user.isEnabled())
            .build();
    }

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
