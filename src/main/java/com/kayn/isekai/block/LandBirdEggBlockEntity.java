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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
    private static final int HATCH_RETRY_TICKS = 20;
    private static final TagKey<Block> HEAT_SOURCES = TagKey.of(RegistryKeys.BLOCK, Isekai.id("egg_heat_sources"));
    private static final List<BlockPos> HEAT_OFFSETS = heatOffsets();
    private static final RawAnimation SHAKE = RawAnimation.begin().thenLoop("egg.interact");
    private static final RawAnimation HATCH = RawAnimation.begin().thenPlayAndHold("egg.eclode");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int incubationTicks;
    private int hatchRetryTicks = HATCH_RETRY_TICKS;
    private boolean incubating;
    private boolean hatching;

    public LandBirdEggBlockEntity(BlockPos pos, BlockState state) {
        super(IsekaiBlock.LAND_BIRD_EGG, pos, state);
    }

    public int getPlumage() {
        return ((LandBirdEggBlock) getCachedState().getBlock()).getPlumage();
    }

    public int getIncubationSeconds() {
        return Math.min(incubationTicks / 20, IsekaiConfig.eggIncubationSeconds());
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
        int incubationTargetTicks = IsekaiConfig.eggIncubationTicks();
        if (incubationTicks >= incubationTargetTicks) {
            if (--hatchRetryTicks == 0) {
                hatchRetryTicks = HATCH_RETRY_TICKS;
                hatch(world, pos);
            }
            return;
        }
        boolean heated = hasHeat(world, pos);
        boolean changed = heated != incubating;
        incubating = heated;
        if (incubating) {
            incubationTicks++;
            world.markDirty(pos);
        }
        if (changed || (incubating && incubationTicks == hatchAnimationStart(incubationTargetTicks))) {
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
        if (incubationTicks == incubationTargetTicks) {
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

    private void hatch(ServerWorld world, BlockPos pos) {
        LandBirdEntity chick = IsekaiEntity.LAND_BIRD.create(world);
        if (chick == null) {
            return;
        }
        chick.initializeHatchling(getPlumage());
        chick.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                world.getRandom().nextFloat() * 360, 0);
        if (world.isSpaceEmpty(chick) && world.spawnEntity(chick) && world.getBlockEntity(pos) == this) {
            world.removeBlock(pos, false);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("IncubationTicks", incubationTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        incubationTicks = Math.max(0, nbt.getInt("IncubationTicks"));
        incubating = nbt.getBoolean("Incubating");
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
