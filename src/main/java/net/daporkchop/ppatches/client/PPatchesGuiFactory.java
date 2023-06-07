package net.daporkchop.ppatches.client;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author DaPorkchop_
 */
@SideOnly(Side.CLIENT)
public class PPatchesGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new GuiConfig(parentScreen, PPatchesConfig.CONFIGURATION.getCategoryNames().stream()
                .filter(categoryName -> categoryName.indexOf('.') < 0)
                .sorted()
                .map(categoryName -> new ConfigElement(PPatchesConfig.CONFIGURATION.getCategory(categoryName)))
                .collect(Collectors.toList()),
                PPatchesMod.MODID, PPatchesMod.NAME, false, false, I18n.format(PPatchesMod.MODID + ".config.title"));
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
