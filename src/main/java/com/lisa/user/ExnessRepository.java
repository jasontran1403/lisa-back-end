package com.lisa.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExnessRepository extends JpaRepository<Exness, Long> {
  Optional<Exness> findByExness(String exness);
  
  Optional<User> getByExness(String exness);
  
  List<Exness> findByUser(User user);
}
