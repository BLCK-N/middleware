package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        log.debug("Loading user details for authentication | username={}", normalizedUsername);
        return userRepository.findByEmailAddress(normalizedUsername)
                .orElseThrow(() -> {
                    log.warn("Authentication lookup failed: user not found | username={}", normalizedUsername);
                    return new UsernameNotFoundException("User not found with email: " + username);
                });
    }
}
