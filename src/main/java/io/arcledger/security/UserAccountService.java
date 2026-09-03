package io.arcledger.security;

import io.arcledger.domain.AppUser;
import io.arcledger.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {
    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(String email, String password, String displayName) {
        String normalizedEmail = AppUserDetailsService.normalize(email);
        if (repository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return repository.saveAndFlush(new AppUser(normalizedEmail, passwordEncoder.encode(password), displayName.strip()));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
