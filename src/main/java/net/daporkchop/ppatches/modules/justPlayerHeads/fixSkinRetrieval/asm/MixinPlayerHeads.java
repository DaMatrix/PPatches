package net.daporkchop.ppatches.modules.justPlayerHeads.fixSkinRetrieval.asm;

import com.mojang.authlib.GameProfile;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.natamus.justplayerheads.util.PlayerHeads", remap = false)
public abstract class MixinPlayerHeads {
    @Shadow
    static String playerdataurl;

    @Shadow
    static String skindataurl;

    @Shadow
    public static String getUrlData(String url) {
        return null;
    }

    /**
     * @reason the existing code is stupid and tries to implement JSON parsing using {@link String#split(String)} lol
     * @author DaPorkchop_
     */
    @Dynamic
    @Overwrite
    public static ItemStack getPlayerHead(String playername, Integer amount) {
        if (false) {
            //original code:
            String data1 = getUrlData(playerdataurl + playername.toLowerCase());
            if (data1 == null || data1.isEmpty()) {
                return null;
            }

            String[] sdata1 = data1.split("\":\"");
            String pid = sdata1[1].split("\"")[0];
            String pname = sdata1[2].split("\"")[0];
            String data2 = getUrlData(skindataurl + pid);
            if (data2 == null || data2.isEmpty()) {
                return null;
            }
            String[] sdata2 = data2.split("value\":\"");
            String tvalue = sdata2[1].split("\"")[0];
            String d = new String(Base64.getDecoder().decode(tvalue.getBytes()));
            String texture = Base64.getEncoder().encodeToString(("{\"textures\"" + d.split("\"textures\"")[1]).getBytes());
            String id = (new UUID(texture.hashCode(), texture.hashCode())).toString();
            ItemStack playerhead = new ItemStack(Items.SKULL, amount.intValue(), 3);
            NBTTagCompound skullOwner = new NBTTagCompound();
            skullOwner.setString("Id", id);
            NBTTagCompound properties = new NBTTagCompound();
            NBTTagList textures = new NBTTagList();
            NBTTagCompound tex = new NBTTagCompound();
            tex.setString("Value", texture);
            textures.appendTag(tex);
            properties.setTag("textures", textures);
            skullOwner.setTag("Properties", properties);
            playerhead.setTagInfo("SkullOwner", skullOwner);
            playerhead.setStackDisplayName(pname + "'s Head");
            return playerhead;
        }

        GameProfile profile = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getGameProfileForUsername(playername);
        if (profile == null) { //player doesn't exist
            return null;
        }

        //clone profile instance to avoid duplicate work
        profile = new GameProfile(Objects.requireNonNull(profile.getId(), "uuid"), Objects.requireNonNull(profile.getName(), "name"));
        FMLCommonHandler.instance().getMinecraftServerInstance().getMinecraftSessionService().fillProfileProperties(profile, true);

        if (profile.getProperties().isEmpty()) { //API request presumably failed
            return null;
        }

        //remove all properties which aren't "textures", and strip signature from all texture properties
        /*List<Property> textureProperties = new ArrayList<>(profile.getProperties().get("textures"));
        profile.getProperties().clear();
        for (Property textureProperty : textureProperties) {
            profile.getProperties().put("textures", new Property(textureProperty.getName(), textureProperty.getValue()));
        }*/

        NBTTagCompound skullOwner = new NBTTagCompound();
        NBTUtil.writeGameProfile(skullOwner, profile);

        ItemStack playerhead = new ItemStack(Items.SKULL, amount, 3);
        playerhead.setTagInfo("SkullOwner", skullOwner);
        playerhead.setStackDisplayName(profile.getName() + "'s Head");
        return playerhead;
    }
}
