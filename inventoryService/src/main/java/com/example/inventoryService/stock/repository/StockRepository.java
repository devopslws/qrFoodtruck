package com.example.inventoryService.stock.repository;


import com.example.inventoryService.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StockRepository extends JpaRepository<Stock, Long> {}