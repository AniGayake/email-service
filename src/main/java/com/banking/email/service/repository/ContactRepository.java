package com.banking.email.service.repository;

import com.banking.email.service.bo.ContactInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<ContactInformation,Long> {
    Optional<ContactInformation> findByCustomerId(Long customerId);
}
