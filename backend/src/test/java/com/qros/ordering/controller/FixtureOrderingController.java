package com.qros.ordering.controller;

import com.qros.catalog.service.FixtureCatalogService;

public class FixtureOrderingController {
    public void call(FixtureCatalogService service) {
        service.execute();
    }
}
