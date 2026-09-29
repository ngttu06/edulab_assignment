package vn.edu.khtn8.identity.repository;

import vn.edu.khtn8.identity.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.authVersion = u.authVersion + 1 where u.id = :id")
    void revokeAccessTokens(@org.springframework.data.repository.query.Param("id") Long id);
}
