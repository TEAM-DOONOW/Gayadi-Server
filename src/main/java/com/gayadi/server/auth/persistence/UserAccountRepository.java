package com.gayadi.server.auth.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user from UserAccount user
            where user.email = :email and user.deletedAt is null
            """)
    Optional<UserAccount> findForLogin(@Param("email") String email);

    @Query("""
            select user from UserAccount user
            where user.id = :id and user.status = 'ACTIVE' and user.deletedAt is null
            """)
    Optional<UserAccount> findActive(@Param("id") long id);
}
