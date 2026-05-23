/*
 * Repositório para a entidade UserAccount.
 * Permite buscar contas de usuários por email e verificar a existência de usuários no sistema.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
