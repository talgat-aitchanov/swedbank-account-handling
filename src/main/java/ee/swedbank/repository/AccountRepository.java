package ee.swedbank.repository;

import ee.swedbank.domain.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "balances")
    Optional<Account> findWithBalancesById(Long id);

    @EntityGraph(attributePaths = "balances")
    List<Account> findAllWithBalancesByOwnerUsername(String ownerUsername);

    @EntityGraph(attributePaths = "balances")
    @Query("SELECT a FROM Account a ORDER BY a.ownerUsername ASC, a.id ASC")
    List<Account> findAllWithBalances();

    @Query("SELECT a.id FROM Account a WHERE a.ownerUsername = :ownerUsername ORDER BY a.id ASC")
    List<Long> findIdsByOwnerUsername(String ownerUsername);

    @Query("SELECT a.id FROM Account a ORDER BY a.id ASC")
    List<Long> findAllIds();
}
