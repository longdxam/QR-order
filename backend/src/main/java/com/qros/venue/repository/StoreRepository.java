package com.qros.venue.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.venue.domain.Store;

public interface StoreRepository extends JpaRepository<Store, UUID> {
}
