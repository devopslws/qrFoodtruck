package com.example.inventoryService.manufacture.repository;

import com.example.inventoryService.manufacture.entity.Manufacture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufactureRepository extends JpaRepository<Manufacture, Long> {
}