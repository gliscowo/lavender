package io.wispforest.lavender.md;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemListComponent extends ItemComponent {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private @Nullable ImmutableList<ItemStack> items;

    private float time = 0f;
    private List<TooltipComponent> extraTooltipSection = List.of();
    private int currentStackIndex;

    public ItemListComponent() {
        super(ItemStack.EMPTY);
        this.setTooltipFromStack(true);
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);

        this.time += delta;
        if (this.time >= 20) {
            this.time -= 20;
            this.updateForItems();
        }
    }

    @Override
    public Component tooltip(List<TooltipComponent> tooltip) {
        if (tooltip == null) return super.tooltip((List<TooltipComponent>) null);

        tooltip = new ArrayList<>(tooltip);
        tooltip.addAll(this.extraTooltipSection);

        this.tooltip = tooltip;
        return this;
    }

    private void updateForItems() {
        if (this.items != null && !this.items.isEmpty()) {
            this.currentStackIndex = (this.currentStackIndex + 1) % this.items.size();
            this.stack(this.items.get(this.currentStackIndex));
        } else {
            this.currentStackIndex = 0;
            this.stack(ItemStack.EMPTY);
        }
    }

    public ItemListComponent slotDisplay(SlotDisplay display) {
        this.items = ImmutableList.copyOf(display.getStacks(SlotDisplayContexts.createParameters(MinecraftClient.getInstance().world)));
        this.updateForItems();

        return this;
    }

    public ItemListComponent ingredient(Ingredient ingredient) {
        return this.slotDisplay(ingredient.toDisplay());
    }

    public ItemListComponent tag(TagKey<Item> tag) {
        this.items = Registries.ITEM.getOptional(tag)
            .map(entries -> entries.stream().map(RegistryEntry::value).map(Item::getDefaultStack).collect(ImmutableList.toImmutableList()))
            .orElse(ImmutableList.of());
        this.updateForItems();

        return this;
    }

    public void extraTooltipSection(List<TooltipComponent> section) {
        this.extraTooltipSection = section;
        this.updateTooltipForStack();
    }

    @Override
    public void parseProperties(UIModel model, Element element, Map<String, Element> children) {
        super.parseProperties(model, element, children);

        UIParsing.apply(children, "tag", tagElement -> TagKey.of(RegistryKeys.ITEM, UIParsing.parseIdentifier(tagElement)), this::tag);
        UIParsing.apply(
            children,
            "ingredient",
            ingredientElement -> Ingredient.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(ingredientElement.getTextContent().strip(), JsonElement.class)).getOrThrow(),
            this::ingredient
        );
    }
}
