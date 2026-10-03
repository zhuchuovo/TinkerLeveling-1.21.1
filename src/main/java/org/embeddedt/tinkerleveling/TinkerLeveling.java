package org.embeddedt.tinkerleveling;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.embeddedt.tinkerleveling.capability.DamageXp;
import org.embeddedt.tinkerleveling.data.LevelingRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;
import slimeknights.tconstruct.library.modifiers.util.StaticModifier;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.function.Supplier;

@Mod(TinkerLeveling.MODID)
public final class TinkerLeveling {
    public static final String MODID = "tinkerleveling";
    public static final Logger LOG = LoggerFactory.getLogger("Tinker Leveling");

    private static final ModifierDeferredRegister MODIFIERS = ModifierDeferredRegister.create(MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final StaticModifier<ModToolLeveling> LEVELING_MODIFIER =
            MODIFIERS.register("leveling", ModToolLeveling::new);
    public static final ResourceLocation SOUND_LEVELUP_LOCATION = id("levelup");
    public static final Supplier<SoundEvent> SOUND_LEVELUP = SOUND_EVENTS.register(
            "levelup", () -> SoundEvent.createVariableRangeEvent(SOUND_LEVELUP_LOCATION));
    public static final Supplier<AttachmentType<DamageXp>> DAMAGE_XP = ATTACHMENT_TYPES.register(
            "damage_xp", () -> AttachmentType.builder(DamageXp::new)
                    .serialize(new IAttachmentSerializer<ListTag, DamageXp>() {
                        @Override
                        public DamageXp read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) {
                            DamageXp damageXp = new DamageXp();
                            damageXp.deserializeNBT(tag);
                            return damageXp;
                        }

                        @Override
                        public ListTag write(DamageXp attachment, HolderLookup.Provider provider) {
                            return attachment.serializeNBT();
                        }
                    })
                    .build());

    public TinkerLeveling(IEventBus modBus, ModContainer container) {
        MODIFIERS.register(modBus);
        SOUND_EVENTS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(TinkerPacketHandler::register);
        container.registerConfig(ModConfig.Type.SERVER, TinkerConfig.SERVER_CONFIG);

        LevelingRules.INSTANCE.init();

        NeoForge.EVENT_BUS.addListener(this::onDeath);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void processInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(TinkerTags.Items.MODIFIABLE)
                    && ModifierUtil.getModifierLevel(stack, LEVELING_MODIFIER.getId()) == 0) {
                ToolStack tool = ToolStack.from(stack);
                tool.addModifier(LEVELING_MODIFIER.getId(), 1);
                tool.updateStack(stack);
                inventory.setChanged();
            }
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            processInventory(player.getInventory());
        }
    }

    private void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) {
            event.getEntity().getExistingData(DAMAGE_XP.get())
                    .ifPresent(damageXp -> damageXp.distributeXpToTools(event.getEntity()));
        }
    }
}
