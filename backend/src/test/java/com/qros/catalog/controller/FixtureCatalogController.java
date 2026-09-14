package com.qros.catalog.controller;

import com.qros.catalog.service.FixtureCatalogService;

public class FixtureCatalogController {
    public void call(FixtureCatalogService service) {
        service.execute();
    }
}
