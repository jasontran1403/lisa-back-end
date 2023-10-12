package com.lisa.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByEmail(String email);
  Optional<User> findByCode(String code);
  @Query(value = "select * from _user where refferal = ?1 order by id", nativeQuery = true)
  List<User> findAllByRefferal(String email);
  
  @Query(value = "select * from _user where refferal = ?1", nativeQuery = true)
  List<User> findByRefferal(String email);
}
