package com.qros.catalog.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.catalog.api.CatalogFacade;
import com.qros.catalog.api.PricedLine;
import com.qros.catalog.api.PricedLine.PricedOption;
import com.qros.catalog.domain.MenuItemEntity;
import com.qros.catalog.domain.MenuItemOptionGroup;
import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.OptionChoice;
import com.qros.catalog.domain.OptionGroupEntity;
import com.qros.catalog.repository.IngredientRepository;
import com.qros.catalog.repository.MenuItemOptionGroupRepository;
import com.qros.catalog.repository.MenuItemRepository;
import com.qros.catalog.repository.MenuVariantRepository;
import com.qros.catalog.repository.OptionChoiceRepository;
import com.qros.catalog.repository.OptionGroupRepository;
import com.qros.catalog.repository.OptionRecipeComponentRepository;
import com.qros.catalog.repository.PriceScheduleRepository;
import com.qros.catalog.repository.RecipeComponentRepository;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.venue.api.StoreFacade;

/**
 * {@code ADR-06}: định giá phía máy chủ, phần dành cho {@code ordering} gọi vào qua
 * {@link CatalogFacade}. Chỉ nạp khi có {@code DataSource}, cùng lý do {@code MenuService}.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class CatalogFacadeImpl implements CatalogFacade {

    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionChoiceRepository optionChoiceRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeComponentRepository recipeComponentRepository;
    private final OptionRecipeComponentRepository optionRecipeComponentRepository;
    private final PriceScheduleRepository priceScheduleRepository;
    private final StoreFacade storeFacade;
    private final Clock clock;

    public CatalogFacadeImpl(MenuItemRepository menuItemRepository, MenuVariantRepository menuVariantRepository,
            OptionGroupRepository optionGroupRepository, OptionChoiceRepository optionChoiceRepository,
            MenuItemOptionGroupRepository menuItemOptionGroupRepository,
            IngredientRepository ingredientRepository, RecipeComponentRepository recipeComponentRepository,
            OptionRecipeComponentRepository optionRecipeComponentRepository,
            PriceScheduleRepository priceScheduleRepository, StoreFacade storeFacade, Clock clock) {
        this.menuItemRepository = menuItemRepository;
        this.menuVariantRepository = menuVariantRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionChoiceRepository = optionChoiceRepository;
        this.menuItemOptionGroupRepository = menuItemOptionGroupRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeComponentRepository = recipeComponentRepository;
        this.optionRecipeComponentRepository = optionRecipeComponentRepository;
        this.priceScheduleRepository = priceScheduleRepository;
        this.storeFacade = storeFacade;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PricedLine priceLine(UUID storeId, UUID menuItemId, UUID variantId, List<UUID> optionChoiceIds) {
        MenuItemEntity item = menuItemRepository.findByIdAndStoreId(menuItemId, storeId)
                .filter(MenuItemEntity::isVisible)
                .orElseThrow(CatalogFacadeImpl::monKhongHopLe);

        MenuVariant variant = menuVariantRepository.findByMenuItemIdOrderByDisplayOrder(menuItemId).stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(CatalogFacadeImpl::monKhongHopLe);

        Set<UUID> soldOutIngredientIds = ingredientRepository.findByStoreIdAndSoldOutTrue(storeId).stream()
                .map(ingredient -> ingredient.getId())
                .collect(Collectors.toSet());

        List<UUID> ingredientsOfVariant = recipeComponentRepository.findByMenuVariantIdIn(List.of(variantId))
                .stream().map(rc -> rc.getIngredientId()).toList();
        boolean variantAvailable = variant.isActive()
                && ingredientsOfVariant.stream().noneMatch(soldOutIngredientIds::contains);

        List<UUID> validOptionGroupIds = menuItemOptionGroupRepository
                .findByMenuItemIdInOrderByDisplayOrder(List.of(menuItemId)).stream()
                .map(MenuItemOptionGroup::getOptionGroupId)
                .toList();
        List<OptionGroupEntity> optionGroups = validOptionGroupIds.isEmpty()
                ? List.of() : optionGroupRepository.findByIdIn(validOptionGroupIds);

        List<UUID> requestedIds = optionChoiceIds == null ? List.of() : optionChoiceIds;
        List<OptionChoice> validChoices = validOptionGroupIds.isEmpty()
                ? List.of()
                : optionChoiceRepository.findByOptionGroupIdInOrderByDisplayOrder(validOptionGroupIds);
        java.util.Map<UUID, OptionChoice> choiceById = validChoices.stream()
                .collect(Collectors.toMap(OptionChoice::getId, c -> c));

        List<OptionChoice> chosen = requestedIds.stream()
                .map(id -> {
                    OptionChoice choice = choiceById.get(id);
                    if (choice == null) {
                        throw monKhongHopLe();
                    }
                    return choice;
                })
                .toList();

        validateSelectionCounts(optionGroups, chosen);

        List<UUID> chosenIds = chosen.stream().map(OptionChoice::getId).toList();
        List<UUID> ingredientsOfChosenOptions = chosenIds.isEmpty()
                ? List.of()
                : optionRecipeComponentRepository.findByOptionChoiceIdIn(chosenIds).stream()
                        .map(orc -> orc.getIngredientId())
                        .toList();
        boolean optionsAvailable = chosen.stream().allMatch(OptionChoice::isActive)
                && ingredientsOfChosenOptions.stream().noneMatch(soldOutIngredientIds::contains);

        ZoneId zone = storeFacade.find(storeId).map(store -> ZoneId.of(store.timezone()))
                .orElse(ZoneId.of("UTC"));
        long variantPrice = EffectivePricing.resolve(variant,
                priceScheduleRepository.findByMenuVariantIdIn(List.of(variantId)), Instant.now(clock), zone);
        long surcharge = chosen.stream().mapToLong(OptionChoice::getSurchargeAmount).sum();

        List<PricedOption> pricedOptions = chosen.stream()
                .map(c -> new PricedOption(c.getId(), c.getName(), c.getSurchargeAmount()))
                .toList();

        return new PricedLine(item.getName(), variant.getName(), item.getStation(),
                variantAvailable && optionsAvailable, variantPrice + surcharge, pricedOptions);
    }

    /**
     * {@code FR-CUS-05}: một nhóm bắt buộc phải có ít nhất một lựa chọn; mọi nhóm phải nằm trong
     * {@code [minSelect, maxSelect]}. Không phải nhóm nào của món cũng bị đụng tới (khách có thể bỏ
     * qua nhóm không bắt buộc), nên chỉ kiểm những nhóm THẬT SỰ có lựa chọn hoặc bắt buộc.
     */
    private static void validateSelectionCounts(List<OptionGroupEntity> optionGroups, List<OptionChoice> chosen) {
        for (OptionGroupEntity group : optionGroups) {
            long soLuongDaChon = chosen.stream()
                    .filter(c -> c.getOptionGroupId().equals(group.getId()))
                    .count();
            if (group.isRequired() && soLuongDaChon < 1) {
                throw monKhongHopLe();
            }
            if (soLuongDaChon > 0 && (soLuongDaChon < group.getMinSelect() || soLuongDaChon > group.getMaxSelect())) {
                throw monKhongHopLe();
            }
        }
    }

    private static QrosException monKhongHopLe() {
        return new QrosException(ErrorCode.VALIDATION_FAILED, "Món hoặc tuỳ chọn không khớp thực đơn hiện tại");
    }
}
