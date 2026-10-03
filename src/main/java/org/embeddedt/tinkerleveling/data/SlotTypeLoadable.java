package org.embeddedt.tinkerleveling.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import org.embeddedt.tinkerleveling.TinkerLeveling;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.tools.SlotType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loadable for a {@link SlotType} that validates the name.
 * <p>
 * Tinkers' own {@link SlotType#LOADABLE} calls {@link SlotType#getOrCreate(String)}, which happily invents a brand new
 * slot type for any typo. A misspelled name would then grant slots that no modifier can ever use, and the mistake would
 * only show up as an unexplained extra slot in the tool tooltip. This loadable rejects unknown names instead, and
 * accepts a few friendlier aliases so a data pack can write {@code "upgrade"} rather than {@code "upgrades"}.
 */
public final class SlotTypeLoadable implements Loadable<SlotType> {
    /** Canonical names, plus the aliases we accept for them */
    private static final Map<String,SlotType> NAMES = new LinkedHashMap<>();

    static {
        // canonical names first, these are what the mod serializes back to
        registerAlias(SlotType.UPGRADE, "upgrades", "upgrade");
        registerAlias(SlotType.ABILITY, "abilities", "ability");
        registerAlias(SlotType.DEFENSE, "defense", "defence");
        registerAlias(SlotType.SOUL, "souls", "soul");
    }

    private static void registerAlias(SlotType type, String... names) {
        for (String name : names) {
            NAMES.put(name, type);
        }
    }

    /** Shared instance */
    public static final SlotTypeLoadable INSTANCE = new SlotTypeLoadable();

    private SlotTypeLoadable() {}

    /** Resolves a slot type name, throwing when the name is not one of the built in slot types */
    public static SlotType resolve(String name) {
        SlotType type = NAMES.get(name);
        if (type == null) {
            throw new JsonSyntaxException("Unknown slot type '" + name + "'. Expected one of " + String.join(", ", NAMES.keySet()));
        }
        return type;
    }

    /** Canonical name for a slot type, used when writing the value back out */
    private static String canonicalName(SlotType type) {
        for (Map.Entry<String,SlotType> entry : NAMES.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return type.getName();
    }

    @Override
    public SlotType convert(JsonElement element, String key, TypedMap context) {
        String name = GsonHelper.convertToString(element, key);
        SlotType type = NAMES.get(name);
        if (type == null) {
            throw new JsonSyntaxException("Unknown slot type '" + name + "' at " + key + ". Expected one of " + String.join(", ", NAMES.keySet()));
        }
        return type;
    }

    @Override
    public JsonElement serialize(SlotType value) {
        return new JsonPrimitive(canonicalName(value));
    }

    @Override
    public SlotType decode(FriendlyByteBuf buffer, TypedMap context) {
        return SlotType.read(buffer);
    }

    @Override
    public void encode(FriendlyByteBuf buffer, SlotType object) {
        object.write(buffer);
    }

    /** All accepted names, for command suggestions and error messages */
    public static Iterable<String> acceptedNames() {
        return NAMES.keySet();
    }

    /** Maps a slot type to its canonical name, logging when the type is not a built in one */
    public static String nameOf(SlotType type) {
        String name = canonicalName(type);
        if (!NAMES.containsKey(name)) {
            TinkerLeveling.LOG.warn("Tool holds slots of unknown type '{}'; they will still be shown but cannot be granted by data packs", name);
        }
        return name;
    }
}
