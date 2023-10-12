package com.lisa.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long>{
	List<Transaction> findAllTransactionByStatus(int status);
	Optional<Transaction> findTransactionById(long id);
	List<Transaction> findTransactionByWithdrawer(String email);
	
	@Query(value="select * from transaction where exness_id = ?1 order by time desc", nativeQuery = true)
	List<Transaction> findTransactionByExnessId(String exness);
	
	@Query(value="select sum(amount) from transaction where withdrawer = ?1 and status = 1", nativeQuery = true)
	List<Transaction> calWithdraw(String email);
}
