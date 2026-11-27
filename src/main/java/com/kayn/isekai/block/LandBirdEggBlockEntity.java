package com.kayn.isekai.block;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.config.IsekaiConfig;
import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class LandBirdEggBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final int HEAT_RADIUS = 3;
    private static final int HATCH_ANIMATION_TICKS = 59;
    /** Incubation is only re-evaluated once a second; the elapsed world time is credited in one go. */
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final TagKey<Block> HEAT_SOURCES = TagKey.of(RegistryKeys.BLOCK, Isekai.id("egg_heat_sources"));
    private static final List<BlockPos> HEAT_OFFSETS = heatOffsets();
    private static final RawAnimation SHAKE = RawAnimation.begin().thenLoop("egg.interact");
    private static final RawAnimation HATCH = RawAnimation.begin().thenPlayAndHold("egg.eclode");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int incubationTicks;
    /** World time of the last incubation check, or -1 before the first one. */
    private long lastCheckTime = -1;
    private boolean incubating;
    private boolean hatching;

    public LandBirdEggBlockEntity(BlockPos pos, BlockState state) {
        super(IsekaiBlock.LAND_BIRD_EGG, pos, state);
    }

    public int getPlumage() {
        return ((LandBirdEggBlock) getCachedState().getBlock()).getPlumage();
    }

    /** Describes the egg's state for the player who right-clicks it. */
    public Text describeProgress(ServerWorld world) {
        int target = IsekaiConfig.eggIncubationTicks();
        int ticks = Math.min(incubationTicks, target);
        int percent = (int) (ticks * 100L / target);
        boolean roomToHatch = createChick(world, pos) != null;
        if (ticks >= target) {
            return roomToHatch
                    ? Text.translatable("message.isekai_creature.yokibird_egg.hatching")
                    : Text.translatable("message.isekai_creature.yokibird_egg.blocked");
        }
        if (!incubating) {
            return ticks == 0
                    ? Text.translatable("message.isekai_creature.yokibird_egg.cold")
                    : Text.translatable("message.isekai_creature.yokibird_egg.paused", percent);
        }
        Text remaining = formatDuration((target - ticks + 19) / 20);
        return roomToHatch
                ? Text.translatable("message.isekai_creature.yokibird_egg.progress", percent, remaining)
                : Text.translatable("message.isekai_creature.yokibird_egg.progress_blocked", percent, remaining);
    }

    private static Text formatDuration(int seconds) {
        int minutes = seconds / 60;
        return minutes > 0
                ? Text.translatable("message.isekai_creature.duration.minutes", minutes, seconds % 60)
                : Text.translatable("message.isekai_creature.duration.seconds", seconds);
    }

    private static int hatchAnimationStart(int incubationTargetTicks) {
        return incubationTargetTicks - Math.min(HATCH_ANIMATION_TICKS, incubationTargetTicks / 2);
    }

    public static void tick(World world, BlockPos pos, BlockState state, LandBirdEggBlockEntity egg) {
        if (world instanceof ServerWorld server) {
            egg.tickServer(server, pos, state);
        }
    }

    private void tickServer(ServerWorld world, BlockPos pos, BlockState state) {
        long now = world.getTime();
        // Stagger eggs across the second so they don't all check on the same tick.
        if (lastCheckTime >= 0 && Math.floorMod(now + pos.asLong(), CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        int target = IsekaiConfig.eggIncubationTicks();
        if (incubationTicks >= target) {
            lastCheckTime = now;
            hatch(world, pos);
            return;
        }
        boolean heated = hasHeat(world, pos);
        // World time keeps running while the chunk is unloaded, so the gap since the last check is
        // credited as long as the egg was warm then and is still warm now.
        long elapsed = lastCheckTime < 0 ? 0 : Math.max(0, now - lastCheckTime);
        lastCheckTime = now;
        boolean changed = heated != incubating;
        int before = incubationTicks;
        if (heated && incubating && elapsed > 0) {
            int animationStart = hatchAnimationStart(target);
            long progressed = Math.min((long) target, before + elapsed);
            // A long jump (e.g. after the chunk reloads) stops at the hatch animation so it still plays.
            if (before < animationStart && progressed > animationStart) {
                progressed = animationStart;
            }
            incubationTicks = (int) progressed;
        }
        incubating = heated;
        if (incubationTicks != before || changed) {
            markDirty();
        }
        int animationStart = hatchAnimationStart(target);
        if (changed || (before < animationStart && incubationTicks >= animationStart)) {
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
        if (incubationTicks >= target) {
            hatch(world, pos);
        }
    }

    private static List<BlockPos> heatOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (BlockPos offset : BlockPos.iterateOutwards(BlockPos.ORIGIN, HEAT_RADIUS, HEAT_RADIUS, HEAT_RADIUS)) {
            if (!offset.equals(BlockPos.ORIGIN) && offset.getManhattanDistance(BlockPos.ORIGIN) <= HEAT_RADIUS) {
                offsets.add(offset.toImmutable());
            }
        }
        return List.copyOf(offsets);
    }

    private static boolean hasHeat(ServerWorld world, BlockPos pos) {
        BlockPos.Mutable nearby = new BlockPos.Mutable();
        for (BlockPos offset : HEAT_OFFSETS) {
            nearby.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
            if (!world.isChunkLoaded(nearby)) {
                continue;
            }
            BlockState state = world.getBlockState(nearby);
            if (state.isIn(HEAT_SOURCES) && (!state.contains(Properties.LIT) || state.get(Properties.LIT))) {
                return true;
            }
        }
        return false;
    }

    /** A chick placed on top of the egg, or null when there is no room for it. */
    @Nullable
    private LandBirdEntity createChick(ServerWorld world, BlockPos pos) {
        LandBirdEntity chick = IsekaiEntity.LAND_BIRD.create(world);
        if (chick == null) {
            return null;
        }
        chick.initializeHatchling(getPlumage());
        chick.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                world.getRandom().nextFloat() * 360, 0);
        return world.isSpaceEmpty(chick) ? chick : null;
    }

    private void hatch(ServerWorld world, BlockPos pos) {
        LandBirdEntity chick = createChick(world, pos);
        if (chick != null && world.spawnEntity(chick) && world.getBlockEntity(pos) == this) {
            world.removeBlock(pos, false);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("IncubationTicks", incubationTicks);
        nbt.putLong("LastCheckTime", lastCheckTime);
        nbt.putBoolean("Warm", incubating);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        incubationTicks = Math.max(0, nbt.getInt("IncubationTicks"));
        lastCheckTime = nbt.contains("LastCheckTime") ? nbt.getLong("LastCheckTime") : -1;
        // Server saves carry "Warm"; client sync packets carry "Incubating".
        incubating = nbt.getBoolean(nbt.contains("Warm") ? "Warm" : "Incubating");
        hatching = nbt.getBoolean("Hatching");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = createNbt();
        nbt.putBoolean("Incubating", incubating);
        nbt.putBoolean("Hatching", incubationTicks >= hatchAnimationStart(IsekaiConfig.eggIncubationTicks()));
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "egg", 0, state -> {
            if (!incubating) {
                state.getController().forceAnimationReset();
                return PlayState.STOP;
            }
            return state.setAndContinue(hatching ? HATCH : SHAKE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
