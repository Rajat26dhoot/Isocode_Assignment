package com.companya.customs.repository;
import com.companya.customs.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByNameIgnoreCase(String name);
}
