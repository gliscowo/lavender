```json
{
  "title": "A Profounder Page With a Long Name",
  "icon": "minecraft:melon_slice{Enchantments:[{id:'minecraft:unbreaking', lvl:1}]}",
  "category": "b_category",
  "associated_items": [
    "minecraft:enchanted_book{StoredEnchantments:[{id:'minecraft:unbreaking', lvl:3s}]}",
    "#minecraft:candles"
  ],
  "additional_search_terms": [
    "incredible",
    "im 14 and this is deep",
    "I NEED PSYCHIATRIC HELP"
  ]
}
```

and let's also have some **markdown** here

> a very profound quote

and a [link](https://wispforest.io)

---

macro_moment(aaaa,bruh)

;;;;;

- list item 1
- and another one
    - nesting moment, with some really long
      prose because that should wrap correctly now
- less nesting

ayo, were on page 2!

<block;furnace[lit=true,facing=east]> <item;minecraft:diamond>

;;;;;

page 3

> we'll maybe put a quote here
>> and for good measure, let's also nest one
> and then back to normal

still page three

[**ritual basics**](^lavender-flower:ritual_basics)

---

rule here

and now just some prose. pretty epic

;;;;;

page 4

<item;minecraft:netherite_ingot{Enchantments:[{id:"sharpness", lvl:1}]}>
<block;minecraft:beacon>
<entity;minecraft:frog{variant:"warm"}>

;;;;;

page 5

<recipe;minecraft:stick>

;;;;;

While being the most basic substance involved in summoning, {light_purple}Conjuration Essence{} is still of utmost importance. It embodies 
the most basic properties all souls share and is therefore involved in the creation of many more capable materials.


One can obtain some essence of their own from {gold}breaking ordinary spawners{} as well as {gold}plundering chests{}.

;;;;;

For when a less concentrated material is required, simply {gold}sneak-right-clicking{} some essence on any stone-like surface 
smashes it into 4 pieces of {light_purple}Lesser Conjuration Essence{}

*~a fine predicament*


[a category link](^lavender-flower:a_category)

```xml owo-ui
<flow-layout direction="horizontal">
    <children>
        <flow-layout direction="horizontal">
            <children>
                <item>
                    <stack>minecraft:diamond</stack>
                    <set-tooltip-from-stack>true</set-tooltip-from-stack>
                </item>

                <label>
                    <text translate="true">item.minecraft.diamond</text>
                    <shadow>true</shadow>
                </label>
            </children>

            <gap>5</gap>
            <vertical-alignment>center</vertical-alignment>
            <padding>
                <all>5</all>
            </padding>
            <surface>
                <panel dark="true"/>
            </surface>

        </flow-layout>
    </children>
    <sizing>
        <horizontal method="fill">100</horizontal>
    </sizing>
    <horizontal-alignment>center</horizontal-alignment>
</flow-layout>
```

;;;;;

<structure;lavender-flower:nether_portal>

%{text.lavender.keybind_tooltip}%