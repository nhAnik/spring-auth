package com.nhanik.springauth.service;

import com.nhanik.springauth.exception.RegistrationFailureException;
import com.nhanik.springauth.model.SecurityToken;
import com.nhanik.springauth.model.User;
import com.nhanik.springauth.payload.AuthenticationRequest;
import com.nhanik.springauth.payload.RegistrationRequest;
import com.nhanik.springauth.repository.UserRepository;
import com.nhanik.springauth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final EmailConfirmationService emailConfirmationService;
    private final SecurityTokenService securityTokenService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User with email " + email + " not found"));
    }

    public void createNewUser(RegistrationRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        emailService.checkInvalidEmail(email);

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isEnabled()) {
                logger.info("User with email " + email + " already exists");
                throw new RegistrationFailureException(email);
            }
            // User exists but did not confirm email previously, so just send mail in this case
            logger.info("Sending confirmation mail again to " + email);
            emailConfirmationService.sendEmailConfirmationMail(user);
            return;
        }
        logger.info("Saving new user with email " + email + " in database");
        String encodedPass = passwordEncoder.encode(password);
        User user = new User(email, encodedPass);
        userRepository.save(user);
        emailConfirmationService.sendEmailConfirmationMail(user);
    }

    public String authenticateUser(AuthenticationRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
        logger.info("Authenticated user with email " + email);
        UserDetails userDetails = loadUserByUsername(email);
        return jwtUtil.generateToken(userDetails);
    }

    public void confirmRegister(String token) {
        SecurityToken securityToken = emailConfirmationService.validateAndGetToken(token);
        String email = securityToken.getUser().getEmail();
        logger.info("Confirm registration of user with email " + email);
        userRepository.enableUser(email);
    }
}
