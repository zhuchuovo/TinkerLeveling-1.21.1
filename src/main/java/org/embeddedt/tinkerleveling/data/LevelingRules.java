package org.embeddedt.tinkerleveling.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.embeddedt.tinkerleveling.TinkerConfig;
import org.embeddedt.tinkerleveling.TinkerLeveling;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.DoubleLoadable;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.json.predicate.tool.ToolStackPredicate;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Data pack driven leveling rules for Tinker Leveling.
 * <p>
 * Rules live in {@code data/<namespace>/tinkerleveling/leveling/<name>.json}. Every rule may target a subset of tools
 * through {@code tool} (a Tinkers tool stack predicate) and describes:
 * <ul>
 *   <li>{@code xp} - the experience curve used for the next level.</li>
 *   <li>{@code max_level} - an optional per rule level cap.</li>
 *   <li>{@code slots} - the slots granted when reaching a given level.</li>
 * </ul>
 * When several rules match the same tool the highest {@code priority} wins. With no matching data pack rule the mod
 * falls back to the values in {@code config/tinkerleveling-server.toml}.
 */
public final class LevelingRules extends SimpleJsonResourceReloadListener {
    /** Folder inside the data pack, relative to the namespace */
    public static final String FOLDER = "tinkerleveling/leveling";

    /** Singleton instance of the rule manager */
    public static final LevelingRules INSTANCE = new LevelingRules();

    /** Rule used when no data pack rule matches a tool; the values come from the server config at query time */
    public static final LevelingRule CONFIG_DEFAULT = new LevelingRule(
            TinkerLeveling.id("default"), 0, ToolStackPredicate.ANY, XpCurve.EMPTY, -1, List.of());

    private List<LevelingRule> rules = List.of();
    private Map<ResourceLocation,LevelingRule> rulesById = Map.of();
    private boolean loaded = false;
    private boolean parseFailure = false;
    /** Last rule lookup, valid for one tool shape */
    private volatile RuleCache cache;

    private LevelingRules() {
        super(JsonHelper.DEFAULT_GSON, FOLDER);
    }

    /** For internal use only */
    public void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddReloadListenerEvent.class, this::addDataPackListeners);
    }

    /** Adds this manager as a data pack listener */
    private void addDataPackListeners(final AddReloadListenerEvent event) {
        event.addListener(this);
    }

    @Override
    protected void apply(Map<ResourceLocation,JsonElement> splashList, ResourceManager resourceManager, ProfilerFiller profiler) {
        long time = System.nanoTime();
        Map<ResourceLocation,LevelingRule> byId = new HashMap<>();
        boolean failed = false;
        for (Entry<ResourceLocation,JsonElement> entry : splashList.entrySet()) {
            ResourceLocation key = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "leveling rule");
                byId.put(key, LevelingRule.LOADABLE.deserialize(json).withId(key));
            } catch (JsonParseException e) {
                failed = true;
                TinkerLeveling.LOG.error("Failed to load leveling rule {}", key, e);
            }
        }
        this.rulesById = Map.copyOf(byId);
        List<LevelingRule> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparingInt(LevelingRule::priority).reversed());
        this.rules = List.copyOf(sorted);
        this.cache = null;
        this.parseFailure = failed;
        this.loaded = true;
        TinkerLeveling.LOG.info("Loaded {} leveling rules in {} ms", sorted.size(), (System.nanoTime() - time) / 1000000f);
        if (failed) {
            TinkerLeveling.LOG.warn("Some leveling rules failed to load and were skipped; affected tools fall back to the server config");
        }
    }

    /** All loaded rules, sorted from highest to lowest priority */
    public List<LevelingRule> getRules() {
        return rules;
    }

    /** Gets a rule by its data pack ID, or null when absent */
    @Nullable
    public LevelingRule getRule(ResourceLocation id) {
        return rulesById.get(id);
    }

    /** True once a data pack reload finished at least once */
    public boolean isLoaded() {
        return loaded;
    }

    /** True when the last reload contained a rule that failed to parse */
    public boolean hasParseFailure() {
        return parseFailure;
    }

    /**
     * Finds the rule that applies to the given tool.
     * <p>
     * Rules are evaluated on every XP gain, which happens once per block break, so the last answer is memoized against
     * the tool's identity. Changing a tool's materials or modifiers changes that identity and re evaluates.
     * @param tool  Tool to look up
     * @return  Highest priority matching rule, or {@link #CONFIG_DEFAULT} when nothing matches
     */
    public LevelingRule getRule(IToolStackView tool) {
        RuleCache cache = this.cache;
        if (cache != null && cache.matches(tool)) {
            return cache.rule;
        }
        LevelingRule match = CONFIG_DEFAULT;
        for (LevelingRule rule : rules) {
            if (rule.tool().matches(tool)) {
                match = rule;
                break;
            }
        }
        this.cache = new RuleCache(tool.getItem(), tool.getMaterials(), tool.getModifiers(), match);
        return match;
    }

    /** Memoized predicate answer for one tool shape */
    private record RuleCache(Object item, Object materials, Object modifiers, LevelingRule rule) {
        boolean matches(IToolStackView tool) {
            return item == tool.getItem() && materials.equals(tool.getMaterials()) && modifiers.equals(tool.getModifiers());
        }
    }

    /** Collects every slot grant triggered by reaching the given level */
    public static SlotGrantResult grantsFor(LevelingRule rule, int level) {
        List<SlotGrant> grants = new ArrayList<>();
        for (SlotGrant grant : rule.slots()) {
            if (grant.levels().contains(level)) {
                grants.add(grant);
            }
        }
        return new SlotGrantResult(grants);
    }

    /** How a slot grant hands the slots to the player */
    public enum SlotMode {
        /** Every listed slot type is granted immediately */
        GRANT,
        /** The player picks exactly one of the listed slot types */
        CHOOSE
    }

    /**
     * Experience curve for a rule.
     * @param base        Experience needed to go from level 1 to level 2
     * @param multiplier  Factor the cost is multiplied by for every further level
     */
    public record XpCurve(double base, double multiplier) {
        /** Curve that defers to the server config */
        public static final XpCurve EMPTY = new XpCurve(-1, -1);

        public static final RecordLoadable<XpCurve> LOADABLE = RecordLoadable.create(
                DoubleLoadable.ANY.defaultField("base", -1d, XpCurve::base),
                DoubleLoadable.ANY.defaultField("multiplier", -1d, XpCurve::multiplier),
                XpCurve::new);

        /** True when this curve carries no data pack values */
        public boolean isEmpty() {
            return base < 0 && multiplier < 0;
        }

        /**
         * Experience required to advance from {@code level} to {@code level + 1}.
         * @param fallbackBase  Base value from the server config, used when this curve omits {@code base}
         * @param fallbackMul   Multiplier from the server config, used when this curve omits {@code multiplier}
         */
        public int xpForLevel(int level, double fallbackBase, double fallbackMul) {
            double resolvedBase = base >= 0 ? base : fallbackBase;
            double resolvedMul = multiplier >= 0 ? multiplier : fallbackMul;
            double required = resolvedBase * Math.pow(resolvedMul, Math.max(1, level) - 1.0);
            if (!(required >= 1)) {
                // also covers NaN
                return 1;
            }
            return required >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
        }
    }

    /**
     * Inclusive range of levels a grant applies to.
     * @param min  First level, must be at least 1
     * @param max  Last level, must be at least {@code min}. Defaults to {@link Integer#MAX_VALUE}, meaning "and above"
     */
    public record LevelRange(int min, int max) {
        public static final RecordLoadable<LevelRange> LOADABLE = RecordLoadable.create(
                IntLoadable.FROM_ONE.requiredField("min", LevelRange::min),
                IntLoadable.FROM_ONE.defaultField("max", Integer.MAX_VALUE, LevelRange::max),
                LevelRange::new).validate((range, error) -> {
                    if (range.max < range.min) {
                        throw error.create("'max' must not be smaller than 'min'");
                    }
                    return range;
                });

        public boolean contains(int level) {
            return level >= min && level <= max;
        }
    }

    /**
     * Slots handed out when a tool reaches a level inside {@link #levels()}.
     * @param levels     Levels that trigger this grant
     * @param mode       How the options are distributed
     * @param slotTypes  Slot types to hand out, in display order. The first entry is the default.
     */
    public record SlotGrant(LevelRange levels, SlotMode mode, List<SlotType> slotTypes) {
        public static final Loadable<List<SlotType>> SLOT_TYPES = SlotTypeLoadable.INSTANCE.list(1);

        public static final RecordLoadable<SlotGrant> LOADABLE = RecordLoadable.create(
                LevelRange.LOADABLE.requiredField("levels", SlotGrant::levels),
                new EnumLoadable<>(SlotMode.class).defaultField("mode", SlotMode.GRANT, SlotGrant::mode),
                SLOT_TYPES.requiredField("slots", SlotGrant::slotTypes),
                SlotGrant::new);

        /** True when reaching this grant should open the selection screen */
        public boolean needsChoice() {
            return mode == SlotMode.CHOOSE && slotTypes.size() > 1;
        }
    }

    /** Rules and grants that matched a level up */
    public record SlotGrantResult(List<SlotGrant> grants) {
        public boolean isEmpty() {
            return grants.isEmpty();
        }

        /** True when at least one grant wants the player to pick a slot type */
        public boolean needsChoice() {
            for (SlotGrant grant : grants) {
                if (grant.needsChoice()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A single leveling rule.
     * @param id        Data pack ID, filled in after parsing
     * @param priority  Higher priority rules win when several match the same tool
     * @param tool      Predicate selecting the tools this rule applies to
     * @param xp        Experience curve, may be empty to use the server config
     * @param maxLevel  Level cap, or {@code -1} to use the server config
     * @param slots     Slots granted on level up
     */
    public record LevelingRule(ResourceLocation id, int priority, IJsonPredicate<IToolStackView> tool,
                               XpCurve xp, int maxLevel, List<SlotGrant> slots) {
        public static final RecordLoadable<LevelingRule> LOADABLE = RecordLoadable.create(
                IntLoadable.ANY_FULL.defaultField("priority", 0, LevelingRule::priority),
                ToolStackPredicate.LOADER.nullableField("tool", LevelingRule::tool),
                XpCurve.LOADABLE.defaultField("xp", XpCurve.EMPTY, LevelingRule::xp),
                IntLoadable.FROM_MINUS_ONE.defaultField("max_level", -1, LevelingRule::maxLevel),
                SlotGrant.LOADABLE.list(0).defaultField("slots", List.of(), LevelingRule::slots),
                (priority, tool, xp, maxLevel, slots) -> new LevelingRule(
                        TinkerLeveling.id("unknown"), priority, tool == null ? ToolStackPredicate.ANY : tool,
                        xp, maxLevel, slots));

        /** Returns a copy of this rule carrying the given data pack ID */
        public LevelingRule withId(ResourceLocation newId) {
            return new LevelingRule(newId, priority, tool, xp, maxLevel, slots);
        }

        /** True when this rule is the config fallback rather than a data pack rule */
        public boolean isConfigDefault() {
            return id.equals(CONFIG_DEFAULT.id());
        }

        /** Experience required to advance from {@code level} to {@code level + 1} */
        public int xpForLevel(int level) {
            return xp.xpForLevel(level, TinkerConfig.defaultBaseXP.get(), TinkerConfig.levelMultiplier.get());
        }

        /** True when the tool may still gain a level at the given level */
        public boolean canLevelUp(int currentLevel) {
            if (maxLevel >= 0 && currentLevel >= maxLevel) {
                return false;
            }
            return TinkerConfig.canLevelUp(currentLevel);
        }
    }
}
