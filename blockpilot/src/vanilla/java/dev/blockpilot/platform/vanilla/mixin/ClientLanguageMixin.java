package dev.blockpilot.platform.vanilla.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blockpilot.platform.vanilla.VanillaPlatform;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Adds the mod's translations once the resource packs' are read. */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @ModifyArg(method = "loadFrom", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/locale/DeprecatedTranslationsInfo;applyToMap(Ljava/util/Map;)V"))
    private static Map<String, String> blockpilot$addTranslations(Map<String, String> translations,
        @Local(argsOnly = true) List<String> languageStack) {
        VanillaPlatform.addTranslations(languageStack, translations);
        return translations;
    }
}
