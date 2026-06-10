package com.keeply.backend.config;

import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataInitializer — Garante que o usuário de desenvolvimento existe no banco.
 *
 * Executa uma única vez no startup do backend. Se o usuário 'kalleb@keeply.com'
 * já existir (criado pela migration V13), esta operação é no-op.
 *
 * Ativo apenas nos profiles "default" e "dev" — nunca em produção.
 *
 * Credenciais de teste:
 *   Email: kalleb@keeply.com
 *   Senha: keeply123
 */
@Component
@Profile({"default", "dev"})
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String TEST_USER_NAME  = "Kalleb";
    private static final String TEST_USER_EMAIL = "kalleb@keeply.com";
    private static final String TEST_USER_PASS  = "keeply123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail(TEST_USER_EMAIL)) {
            log.info("DataInitializer: usuário de teste '{}' já existe. Nenhuma ação necessária.", TEST_USER_EMAIL);
            return;
        }

        UserAccount user = new UserAccount();
        user.name         = TEST_USER_NAME;
        user.email        = TEST_USER_EMAIL;
        user.passwordHash = passwordEncoder.encode(TEST_USER_PASS);

        userRepository.save(user);

        log.info("DataInitializer: usuário de teste criado com sucesso → email={}", TEST_USER_EMAIL);
        log.info("DataInitializer: Use email='{}' e senha='{}' para autenticar o mobile.", TEST_USER_EMAIL, TEST_USER_PASS);
    }
}
