package com.qros.catalog.service;

import java.util.List;

import com.qros.catalog.domain.Category;
import com.qros.catalog.domain.MenuItemEntity;
import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.OptionChoice;
import com.qros.catalog.domain.OptionGroupEntity;

/**
 * Cấu trúc thực đơn đã dựng xong ở tầng domain — {@code catalog.controller} dịch sang DTO hợp đồng
 * ({@code com.qros.generated.model.Menu}), cùng cách {@code venue.service.TableSessionService}
 * trả entity/record cho controller tự dịch, không tự import kiểu sinh ra ở tầng service.
 */
public record MenuAggregate(List<CategoryView> categories) {

    public record CategoryView(Category category, List<MenuItemView> items) {
    }

    public record MenuItemView(
            MenuItemEntity item,
            boolean available,
            List<VariantView> variants,
            List<OptionGroupView> optionGroups) {
    }

    public record VariantView(MenuVariant variant, long effectivePriceAmount, boolean available) {
    }

    public record OptionGroupView(OptionGroupEntity group, List<OptionView> options) {
    }

    public record OptionView(OptionChoice choice, boolean available) {
    }
}
