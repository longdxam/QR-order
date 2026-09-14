package com.qros.catalog.controller;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import com.qros.catalog.domain.MenuItemEntity;
import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.OptionChoice;
import com.qros.catalog.domain.OptionGroupEntity;
import com.qros.catalog.service.MenuAggregate;
import com.qros.catalog.service.MenuAggregate.CategoryView;
import com.qros.catalog.service.MenuAggregate.MenuItemView;
import com.qros.catalog.service.MenuAggregate.OptionGroupView;
import com.qros.catalog.service.MenuAggregate.OptionView;
import com.qros.catalog.service.MenuAggregate.VariantView;
import com.qros.catalog.service.MenuService;
import com.qros.generated.api.GuestMenuApi;
import com.qros.generated.model.Menu;
import com.qros.generated.model.MenuCategoriesInner;
import com.qros.generated.model.MenuItem;
import com.qros.generated.model.MenuItem.AllergensEnum;
import com.qros.generated.model.MenuItem.AttributesEnum;
import com.qros.generated.model.MenuItemVariantsInner;
import com.qros.generated.model.Money;
import com.qros.generated.model.Money.CurrencyEnum;
import com.qros.generated.model.OptionGroup;
import com.qros.generated.model.OptionGroup.SelectionEnum;
import com.qros.generated.model.OptionGroupOptionsInner;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code FR-CUS-03}, {@code FR-CUS-05}: thực đơn đang phát hành và chi tiết một món.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do {@code MenuService} nó phụ thuộc.
 */
@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
public class GuestMenuController implements GuestMenuApi {

    private final MenuService menuService;
    private final ObjectMapper objectMapper;

    public GuestMenuController(MenuService menuService, ObjectMapper objectMapper) {
        this.menuService = menuService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Menu> getMenu(String ifNoneMatch) {
        UUID storeId = storeIdCuaPhien();
        MenuAggregate aggregate = menuService.buildMenu(storeId);
        Menu dto = toDto(aggregate, storeId);
        String etag = etagCua(dto);

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(dto);
    }

    @Override
    public ResponseEntity<MenuItem> getMenuItem(UUID itemId) {
        UUID storeId = storeIdCuaPhien();
        MenuItemView view = menuService.findItemView(itemId, storeId)
                .filter(candidate -> !candidate.variants().isEmpty())
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok(toDto(view));
    }

    private UUID storeIdCuaPhien() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
        String sid = jwt.getClaimAsString("sid");
        if (sid == null) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
        return UUID.fromString(sid);
    }

    private Menu toDto(MenuAggregate aggregate, UUID storeId) {
        Menu menu = new Menu(storeId, OffsetDateTime.now(ZoneOffset.UTC), null);
        for (CategoryView category : aggregate.categories()) {
            MenuCategoriesInner categoryDto = new MenuCategoriesInner(category.category().getId(),
                    category.category().getName(), null);
            categoryDto.displayOrder(category.category().getDisplayOrder());
            for (MenuItemView item : category.items()) {
                categoryDto.addItemsItem(toDto(item));
            }
            menu.addCategoriesItem(categoryDto);
        }
        return menu;
    }

    private MenuItem toDto(MenuItemView view) {
        MenuItemEntity item = view.item();
        List<VariantView> variants = view.variants();
        VariantView cheapest = variants.stream()
                .min((a, b) -> Long.compare(a.effectivePriceAmount(), b.effectivePriceAmount()))
                .orElseThrow();

        MenuItem dto = new MenuItem(item.getId(), item.getName(), moneyOf(cheapest.effectivePriceAmount()),
                view.available(), variants.stream().map(this::toDto).toList());
        dto.description(item.getDescription());
        if (item.getImageUrl() != null) {
            dto.imageUrl(URI.create(item.getImageUrl()));
        }
        item.getAllergens().forEach(value -> dto.addAllergensItem(AllergensEnum.fromValue(value)));
        item.getAttributes().forEach(value -> dto.addAttributesItem(AttributesEnum.fromValue(value)));
        view.optionGroups().forEach(group -> dto.addOptionGroupsItem(toDto(group)));
        return dto;
    }

    private MenuItemVariantsInner toDto(VariantView view) {
        MenuVariant variant = view.variant();
        MenuItemVariantsInner dto = new MenuItemVariantsInner(
                variant.getId(), variant.getName(), moneyOf(view.effectivePriceAmount()));
        dto.available(view.available());
        return dto;
    }

    private OptionGroup toDto(OptionGroupView view) {
        OptionGroupEntity group = view.group();
        OptionGroup dto = new OptionGroup(group.getId(), group.getName(),
                SelectionEnum.fromValue(group.getSelection()), null);
        dto.required(group.isRequired());
        dto.minSelect(group.getMinSelect());
        dto.maxSelect(group.getMaxSelect());
        view.options().forEach(option -> dto.addOptionsItem(toDto(option)));
        return dto;
    }

    private OptionGroupOptionsInner toDto(OptionView view) {
        OptionChoice choice = view.choice();
        OptionGroupOptionsInner dto = new OptionGroupOptionsInner(
                choice.getId(), choice.getName(), moneyOf(choice.getSurchargeAmount()));
        dto.available(view.available());
        return dto;
    }

    private static Money moneyOf(long amount) {
        return new Money(amount, CurrencyEnum.VND);
    }

    /**
     * Nội dung quyết định ETag — hai lần dựng thực đơn giống hệt nhau (không món nào hết hàng,
     * không giá nào đổi) phải ra cùng một ETag để {@code If-None-Match} có nghĩa. Không dùng cột
     * "cập nhật lúc" nào của CSDL vì {@code menu_item} không có cột đó (chỉ có {@code created_at}).
     */
    private String etagCua(Menu menu) {
        byte[] json = objectMapper.writeValueAsBytes(menu.getCategories());
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return "\"" + HexFormat.of().formatHex(sha256.digest(json)) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
