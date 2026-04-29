package sn.mixx.expresso.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.User;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Mono<User> findOneByLogin(String login);

    Mono<User> findOneByEmailIgnoreCase(String email);

    @Query("SELECT * FROM jhi_user WHERE login = :login AND activated = true")
    Mono<User> findOneByLoginAndActivatedTrue(String login);

    @Query("""
        SELECT u.*, a.name as authority_name
        FROM jhi_user u
        LEFT JOIN jhi_user_authority ua ON u.id = ua.user_id
        LEFT JOIN jhi_authority a ON ua.authority_name = a.name
        WHERE u.login = :login
        """)
    Mono<User> findOneWithAuthoritiesByLogin(String login);
}