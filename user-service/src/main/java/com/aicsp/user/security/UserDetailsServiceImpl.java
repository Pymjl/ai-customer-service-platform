package com.aicsp.user.security;

import com.aicsp.user.config.AdminProperties;
import java.util.Collections;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final AdminProperties adminProperties;
    private final PasswordEncoder passwordEncoder;

    public UserDetailsServiceImpl(AdminProperties adminProperties, PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!adminProperties.getUsername().equals(username)) {
            throw new UsernameNotFoundException(username);
        }
        return User.withUsername(username)
                .password(passwordEncoder.encode(adminProperties.getPassword()))
                .authorities(Collections.emptyList())
                .build();
    }
}
