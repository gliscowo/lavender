package io.wispforest.lavender;

import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.RangeConstraint;

@Modmenu(modId = Lavender.MOD_ID)
@Config(name = "lavender", wrapperName = "LavenderConfig")
public class LavenderConfigModel {
    @RangeConstraint(min = 0.0, max = 1.0)
    public float structurePreviewAlpha = 0.5f;
}
