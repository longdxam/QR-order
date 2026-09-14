package com.qros.catalog.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.catalog.domain.Category;
import com.qros.catalog.domain.MenuItemEntity;
import com.qros.catalog.domain.MenuItemOptionGroup;
import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.OptionChoice;
import com.qros.catalog.domain.OptionGroupEntity;
import com.qros.catalog.domain.OptionRecipeComponent;
import com.qros.catalog.domain.PriceSchedule;
import com.qros.catalog.domain.RecipeComponent;
import com.qros.catalog.repository.CategoryRepository;
import com.qros.catalog.repository.IngredientRepository;
import com.qros.catalog.repository.MenuItemOptionGroupRepository;
import com.qros.catalog.repository.MenuItemRepository;
import com.qros.catalog.repository.MenuVariantRepository;
import com.qros.catalog.repository.OptionChoiceRepository;
import com.qros.catalog.repository.OptionGroupRepository;
import com.qros.catalog.repository.OptionRecipeComponentRepository;
import com.qros.catalog.repository.PriceScheduleRepository;
import com.qros.catalog.repository.RecipeComponentRepository;
import com.qros.catalog.service.MenuAggregate.CategoryView;
import com.qros.catalog.service.MenuAggregate.MenuItemView;
import com.qros.catalog.service.MenuAggregate.OptionGroupView;
import com.qros.catalog.service.MenuAggregate.OptionView;
import com.qros.catalog.service.MenuAggregate.VariantView;
import com.qros.venue.api.StoreFacade;

/**
 * {@code FR-CUS-03}: dựng thực đơn đang phát hành kèm trạng thái còn/hết theo thời gian thực.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do mọi service khác cần CSDL thật
 * ({@code AuthenticationService}, {@code TableSessionService}...).
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class MenuService {

    private final CategoryRepository categoryRepository;
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

    public MenuService(CategoryRepository categoryRepository, MenuItemRepository menuItemRepository,
            MenuVariantRepository menuVariantRepository, OptionGroupRepository optionGroupRepository,
            OptionChoiceRepository optionChoiceRepository,
            MenuItemOptionGroupRepository menuItemOptionGroupRepository,
            IngredientRepository ingredientRepository, RecipeComponentRepository recipeComponentRepository,
            OptionRecipeComponentRepository optionRecipeComponentRepository,
            PriceScheduleRepository priceScheduleRepository, StoreFacade storeFacade, Clock clock) {
        this.categoryRepository = categoryRepository;
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

    @Transactional(readOnly = true)
    public MenuAggregate buildMenu(UUID storeId) {
        Instant now = Instant.now(clock);
        ZoneId zone = zoneOf(storeId);

        List<Category> categories = categoryRepository.findByStoreIdAndActiveTrueOrderByDisplayOrder(storeId);
        List<MenuItemEntity> items = menuItemRepository
                .findByStoreIdAndPublishedTrueAndManuallyDisabledFalseOrderByDisplayOrder(storeId);

        Context context = loadContext(items, now, zone);

        Map<UUID, List<MenuItemEntity>> itemsByCategory = items.stream()
                .collect(Collectors.groupingBy(MenuItemEntity::getCategoryId, LinkedHashMap::new, Collectors.toList()));

        List<CategoryView> categoryViews = categories.stream()
                .map(category -> new CategoryView(category,
                        itemsByCategory.getOrDefault(category.getId(), List.of()).stream()
                                .map(item -> toItemView(item, context))
                                // Món chưa có biến thể nào (chưa xảy ra qua ứng dụng — chưa có
                                // endpoint quản trị catalog, M3 — nhưng dữ liệu chèn tay/lỗi vẫn có
                                // thể tạo ra) không dựng được basePrice; bỏ qua thay vì vỡ cả thực
                                // đơn vì một món hỏng.
                                .filter(view -> !view.variants().isEmpty())
                                .toList()))
                .filter(view -> !view.items().isEmpty())
                .toList();

        return new MenuAggregate(categoryViews);
    }

    @Transactional(readOnly = true)
    public Optional<MenuItemView> findItemView(UUID itemId, UUID storeId) {
        Instant now = Instant.now(clock);
        ZoneId zone = zoneOf(storeId);

        return menuItemRepository.findByIdAndStoreId(itemId, storeId)
                .map(item -> toItemView(item, loadContext(List.of(item), now, zone)));
    }

    private ZoneId zoneOf(UUID storeId) {
        return storeFacade.find(storeId)
                .map(store -> ZoneId.of(store.timezone()))
                .orElse(ZoneId.of("UTC"));
    }

    private Context loadContext(List<MenuItemEntity> items, Instant now, ZoneId zone) {
        List<UUID> itemIds = items.stream().map(MenuItemEntity::getId).toList();
        if (itemIds.isEmpty()) {
            GroupBundle emptyGroups = new GroupBundle(Map.of(), Map.of(), Map.of(), Map.of());
            return new Context(Map.of(), Map.of(), Map.of(), emptyGroups, Set.of(), now, zone);
        }

        List<MenuVariant> variants = menuVariantRepository.findByMenuItemIdInOrderByDisplayOrder(itemIds);
        Map<UUID, List<MenuVariant>> variantsByItem = variants.stream()
                .collect(Collectors.groupingBy(MenuVariant::getMenuItemId, LinkedHashMap::new, Collectors.toList()));
        List<UUID> variantIds = variants.stream().map(MenuVariant::getId).toList();

        List<RecipeComponent> recipeComponents = variantIds.isEmpty()
                ? List.of() : recipeComponentRepository.findByMenuVariantIdIn(variantIds);
        Map<UUID, List<UUID>> ingredientsByVariant = recipeComponents.stream()
                .collect(Collectors.groupingBy(RecipeComponent::getMenuVariantId,
                        Collectors.mapping(RecipeComponent::getIngredientId, Collectors.toList())));

        List<PriceSchedule> schedules = variantIds.isEmpty()
                ? List.of() : priceScheduleRepository.findByMenuVariantIdIn(variantIds);
        Map<UUID, List<PriceSchedule>> schedulesByVariant = schedules.stream()
                .collect(Collectors.groupingBy(PriceSchedule::getMenuVariantId));

        List<MenuItemOptionGroup> links = menuItemOptionGroupRepository
                .findByMenuItemIdInOrderByDisplayOrder(itemIds);
        Map<UUID, List<UUID>> optionGroupIdsByItem = links.stream()
                .collect(Collectors.groupingBy(MenuItemOptionGroup::getMenuItemId,
                        Collectors.mapping(MenuItemOptionGroup::getOptionGroupId, Collectors.toList())));
        List<UUID> optionGroupIds = links.stream().map(MenuItemOptionGroup::getOptionGroupId).distinct().toList();

        List<OptionGroupEntity> optionGroups = optionGroupIds.isEmpty()
                ? List.of() : optionGroupRepository.findByIdIn(optionGroupIds);
        Map<UUID, OptionGroupEntity> optionGroupById = optionGroups.stream()
                .collect(Collectors.toMap(OptionGroupEntity::getId, group -> group));

        List<OptionChoice> choices = optionGroupIds.isEmpty()
                ? List.of() : optionChoiceRepository.findByOptionGroupIdInOrderByDisplayOrder(optionGroupIds);
        Map<UUID, List<OptionChoice>> choicesByGroup = choices.stream()
                .collect(Collectors.groupingBy(OptionChoice::getOptionGroupId, LinkedHashMap::new, Collectors.toList()));
        List<UUID> choiceIds = choices.stream().map(OptionChoice::getId).toList();

        List<OptionRecipeComponent> optionRecipeComponents = choiceIds.isEmpty()
                ? List.of() : optionRecipeComponentRepository.findByOptionChoiceIdIn(choiceIds);
        Map<UUID, List<UUID>> ingredientsByChoice = optionRecipeComponents.stream()
                .collect(Collectors.groupingBy(OptionRecipeComponent::getOptionChoiceId,
                        Collectors.mapping(OptionRecipeComponent::getIngredientId, Collectors.toList())));

        Set<UUID> soldOutIngredientIds = items.isEmpty() ? Set.of()
                : ingredientRepository.findByStoreIdAndSoldOutTrue(items.get(0).getStoreId()).stream()
                        .map(ingredient -> ingredient.getId())
                        .collect(Collectors.toSet());

        return new Context(variantsByItem, ingredientsByVariant, schedulesByVariant,
                new GroupBundle(optionGroupIdsByItem, optionGroupById, choicesByGroup, ingredientsByChoice),
                soldOutIngredientIds, now, zone);
    }

    private MenuItemView toItemView(MenuItemEntity item, Context context) {
        List<MenuVariant> variants = context.variantsByItem().getOrDefault(item.getId(), List.of());
        List<VariantView> variantViews = variants.stream()
                .map(variant -> toVariantView(variant, context))
                .toList();
        boolean itemAvailable = variantViews.stream().anyMatch(VariantView::available);

        List<UUID> groupIds = context.groups().optionGroupIdsByItem().getOrDefault(item.getId(), List.of());
        List<OptionGroupView> groupViews = groupIds.stream()
                .map(context.groups().optionGroupById()::get)
                .filter(group -> group != null)
                .map(group -> toOptionGroupView(group, context))
                .toList();

        return new MenuItemView(item, itemAvailable, variantViews, groupViews);
    }

    private VariantView toVariantView(MenuVariant variant, Context context) {
        List<UUID> ingredientIds = context.ingredientsByVariant().getOrDefault(variant.getId(), List.of());
        boolean available = variant.isActive()
                && ingredientIds.stream().noneMatch(context.soldOutIngredientIds()::contains);
        List<PriceSchedule> schedules = context.schedulesByVariant().getOrDefault(variant.getId(), List.of());
        long effectivePrice = EffectivePricing.resolve(variant, schedules, context.now(), context.zone());
        return new VariantView(variant, effectivePrice, available);
    }

    private OptionGroupView toOptionGroupView(OptionGroupEntity group, Context context) {
        List<OptionChoice> choices = context.groups().choicesByGroup().getOrDefault(group.getId(), List.of());
        List<OptionView> optionViews = choices.stream()
                .map(choice -> {
                    List<UUID> ingredientIds = context.groups().ingredientsByChoice()
                            .getOrDefault(choice.getId(), List.of());
                    boolean available = choice.isActive()
                            && ingredientIds.stream().noneMatch(context.soldOutIngredientIds()::contains);
                    return new OptionView(choice, available);
                })
                .toList();
        return new OptionGroupView(group, optionViews);
    }

    private record GroupBundle(
            Map<UUID, List<UUID>> optionGroupIdsByItem,
            Map<UUID, OptionGroupEntity> optionGroupById,
            Map<UUID, List<OptionChoice>> choicesByGroup,
            Map<UUID, List<UUID>> ingredientsByChoice) {
    }

    private record Context(
            Map<UUID, List<MenuVariant>> variantsByItem,
            Map<UUID, List<UUID>> ingredientsByVariant,
            Map<UUID, List<PriceSchedule>> schedulesByVariant,
            GroupBundle groups,
            Set<UUID> soldOutIngredientIds,
            Instant now,
            ZoneId zone) {
    }
}
