package com.qros.venue.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.qros.venue.api.StoreFacade;
import com.qros.venue.api.StoreView;
import com.qros.venue.domain.Store;
import com.qros.venue.repository.StoreRepository;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class StoreFacadeImpl implements StoreFacade {

    private final StoreRepository storeRepository;

    public StoreFacadeImpl(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    public Optional<StoreView> find(UUID storeId) {
        return storeRepository.findById(storeId).map(StoreFacadeImpl::toView);
    }

    private static StoreView toView(Store store) {
        return new StoreView(store.getId(), store.getName(), store.getTimezone());
    }
}
