package com.kayn.isekai.entity.custom;

import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.config.IsekaiConfig;
import com.kayn.isekai.entity.ai.LandBirdFleeGoal;
import com.kayn.isekai.entity.ai.LandBirdFollowOwnerGoal;
import com.kayn.isekai.entity.anim.PhaseAnimationController;
import com.kayn.isekai.item.IsekaiItem;
import com.kayn.isekai.sound.IsekaiSound;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.HorseArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class LandBirdEntity extends AbstractHorseEntity implements GeoEntity {
    public static final int PLUMAGE_COUNT = 6;
    public static final float ADULT_MODEL_SCALE = 0.8f;
    // Juvenile geo models are ~21px tall; a vanilla chicken is ~15px, so shrink them to match.
    public static final float BABY_MODEL_SCALE = 0.72f;
    // Type dimensions are 1.0 x 1.85; this yields a chicken-sized 0.38 x 0.70 hitbox.
    private static final float BABY_HITBOX_SCALE = 0.7f / 1.85f;
    // Height of the back at the seat (model pixels), averaged over each gait's cycle; the body sinks
    // while it moves. Measured with tools/land_bird_preview.py.
    private static final float SEAT_BACK_IDLE = 24.51f;
    private static final float SEAT_BACK_WALK = 22.41f;
    private static final float SEAT_BACK_SPRINT = 22.03f;
    private static final float SEAT_BACK_WALK_BACK = 23.15f;
    // Vanilla player in the riding pose: underside of the thighs above the feet (0.75 * 0.9375 - 2/16 * 0.9375),
    // and the -0.35 PlayerEntity#getHeightOffset that vanilla adds to the mount offset.
    private static final double RIDER_THIGH_HEIGHT = 0.586;
    private static final double RIDER_HEIGHT_OFFSET = 0.35;
    private static final double SEAT_CLEARANCE = 0.5 / 16.0 * ADULT_MODEL_SCALE;
    private static final float SEAT_SMOOTHING = 0.2f;
    private static final double SADDLE_SEAT_BACK = 5.0 / 16.0;
    // A falling bird without a rider spreads its wings and drifts down no faster than this (blocks/tick).
    // Ridden birds keep their wings folded and drop at full speed unless the rider glides.
    private static final double SLOW_FALL_SPEED = 0.22;
    private static final int DAY_TICKS = 24_000;
    private static final int NIGHT_START = 13_000;
    private static final int NIGHT_END = 23_000;
    // A following bird only sleeps once its owner has stood still this long.
    private static final int OWNER_STILL_TICKS_TO_SLEEP = 60;
    private static final int OWNER_STILL_TICKS_TO_SLEEP_AWAY = 200;
    private static final double OWNER_MOVED_DISTANCE_SQ = 0.5 * 0.5;
    private static final double SLEEP_NEAR_OWNER_DISTANCE_SQ = 4.0 * 4.0;
    // Whistled for (Callable Horses): wakes up and runs over, giving up after this long.
    private static final int SUMMON_TIMEOUT_TICKS = 600;
    private static final double SUMMON_ARRIVE_DISTANCE_SQ = 3.0 * 3.0;
    // After arriving it stays up this long, so the caller has time to mount before it dozes off again.
    private static final int SUMMON_STAY_AWAKE_TICKS = 200;
    private static final int SUMMON_REPATH_TICKS = 10;
    private static final int ALARM_TICKS = 100;
    private static final int ALARM_REFRESH_TICKS = 40;
    private static final double THREAT_SAFE_DISTANCE_SQ = 12.0 * 12.0;
    private static final double HOSTILE_SCAN_RANGE = 10.0;
    private static final double AMBIENT_FLOCK_RANGE = 12.0;
    private static final float AMBIENT_FLOCK_VOICES = 2.5f;
    private static final int BABY_GROWTH_SECONDS_PER_CARROT = 50;
    private static final int CARROT_REGEN_TICKS = 200;
    private static final int MAX_CARROT_REGEN_TICKS = 1_200;
    private static final Ingredient TEMPTING_FOOD = Ingredient.ofItems(Items.CARROT, Items.GOLDEN_CARROT);
    private static final int DEFAULT_PLUMAGE = 1;
    private static final String PLUMAGE_NBT_KEY = "Plumage";
    private static final int ARMOR_SLOT = 1;
    private static final String ARMOR_NBT_KEY = "ArmorItem";
    private static final UUID HORSE_ARMOR_BONUS_ID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F754");
    private static final TrackedData<Integer> PLUMAGE =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> BABY_REACTION =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> MODE =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> AWAITING_OWNER =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> NIGHT_REST =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // Whether the rider is holding jump in the air; drives the flapping animation for other players.
    private static final TrackedData<Boolean> GLIDING =
            DataTracker.registerData(LandBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final byte NO_REACTION = 0;
    private static final byte FEED_REACTION = 1;
    private static final byte HAPPY_REACTION = 2;

    private static final RawAnimation ADULT_IDLE = RawAnimation.begin().thenLoop("adult.idle");
    private static final RawAnimation ADULT_WALK = RawAnimation.begin().thenLoop("adult.walk");
    private static final RawAnimation ADULT_SPRINT = RawAnimation.begin().thenLoop("adult.sprint");
    private static final RawAnimation ADULT_SIT = RawAnimation.begin().thenLoop("adult.sit");
    private static final RawAnimation ADULT_DEATH = RawAnimation.begin().thenPlayAndHold("adult.death");
    private static final RawAnimation ADULT_FLAP = RawAnimation.begin().thenLoop("adult.flap");
    private static final RawAnimation ADULT_GLIDE = RawAnimation.begin().thenLoop("adult.glide");
    private static final RawAnimation ADULT_AIR = RawAnimation.begin().thenLoop("adult.air");
    private static final RawAnimation ADULT_WALK_BACK = RawAnimation.begin().thenLoop("adult.walk_back");
    // Ticks off the ground before the wings start flapping, so stepping up a block does not trigger it.
    private static final int FLAP_AIR_TICKS = 3;
    // Ground speed (blocks per tick) each gait was authored for. Walk: 15px stride per 0.375s stance,
    // sprint: 14px per 0.1575s, walking backwards: the walk at 85% stride.
    private static final float WALK_DESIGN_SPEED = 0.100f;
    private static final float SPRINT_DESIGN_SPEED = 0.222f;
    private static final float WALK_BACK_DESIGN_SPEED = 0.085f;
    // Without a rider the gait follows speed: wandering walks, following or fleeing sprints.
    private static final float SPRINT_SPEED_THRESHOLD = 0.15f;
    private static final float STANDING_SPEED = 0.008f;
    // Ridden birds cover far more ground than a stride allows (walk ~0.26, sprint ~0.65 blocks/tick), so
    // playback only follows speed up to a natural cadence (walk ~4, sprint ~5 steps a second) and the feet
    // are allowed to slide past that, instead of the legs whirring.
    private static final double MIN_GAIT_PLAYBACK = 0.6;
    private static final double MAX_WALK_PLAYBACK = 1.25;
    private static final double MAX_SPRINT_PLAYBACK = 1.15;
    private static final double MAX_WALK_BACK_PLAYBACK = 1.2;
    private static final float SPEED_SMOOTHING = 0.25f;
    // Chick gaits, authored the same way on the 0.72-scale juvenile model.
    private static final float BABY_WALK_DESIGN_SPEED = 0.055f;
    private static final float BABY_RUN_DESIGN_SPEED = 0.113f;
    private static final float BABY_RUN_SPEED_THRESHOLD = 0.09f;
    private static final RawAnimation BABY_IDLE = RawAnimation.begin().thenLoop("baby.idle");
    private static final RawAnimation BABY_WALK = RawAnimation.begin().thenLoop("baby.walk");
    private static final RawAnimation BABY_RUN = RawAnimation.begin().thenLoop("baby.run");
    private static final RawAnimation BABY_FEED = RawAnimation.begin().thenPlay("baby.feed");
    private static final RawAnimation BABY_HAPPY = RawAnimation.begin().thenPlay("baby.happy");
    private static final RawAnimation BABY_SIT = RawAnimation.begin().thenLoop("baby.sit");
    private static final RawAnimation BABY_DEATH = RawAnimation.begin().thenPlayAndHold("baby.death");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int babyReactionTicks;
    private int airTicks;
    private float groundSpeed;
    // Ground speed along the facing direction; negative while backing up.
    private float forwardSpeed;
    private float seatBack = SEAT_BACK_IDLE;
    // Set while a rider glides; keeps the jump bar from charging until they let go of jump after landing.
    private boolean jumpLocked;
    @Nullable
    private Vec3d ownerAnchor;
    private int ownerStillTicks;
    private int alarmTicks;
    @Nullable
    private LivingEntity threat;
    // True while this bird runs its own tick, so navigation calls from elsewhere can be told apart.
    private boolean ticking;
    @Nullable
    private PlayerEntity summoner;
    private double summonSpeed;
    private int summonTicks;
    private int summonAwakeTicks;

    public LandBirdEntity(EntityType<? extends LandBirdEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createBirdAttributes() {
        return createBaseHorseAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3f)
                .add(EntityAttributes.HORSE_JUMP_STRENGTH, 0.5);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new Goal() {
            {
                setControls(EnumSet.of(Control.MOVE, Control.JUMP));
            }

            @Override
            public boolean canStart() {
                return isSitting();
            }

            @Override
            public void start() {
                getNavigation().stop();
            }
        });
        goalSelector.add(1, new Goal() {
            private int repathTicks;

            {
                setControls(EnumSet.of(Control.MOVE, Control.LOOK));
            }

            @Override
            public boolean canStart() {
                return summoner != null;
            }

            @Override
            public void start() {
                repathTicks = 0;
            }

            @Override
            public void tick() {
                if (summoner == null) {
                    return;
                }
                getLookControl().lookAt(summoner);
                if (--repathTicks <= 0) {
                    repathTicks = getTickCount(SUMMON_REPATH_TICKS);
                    getNavigation().startMovingTo(summoner, summonSpeed);
                }
            }

            @Override
            public void stop() {
                getNavigation().stop();
            }
        });
        goalSelector.add(2, new LandBirdFleeGoal(this));
        goalSelector.add(2, new EscapeDangerGoal(this, 1.2));
        goalSelector.add(3, new AnimalMateGoal(this, 1.0));
        goalSelector.add(4, new TemptGoal(this, 1.15, TEMPTING_FOOD, false) {
            @Override
            public boolean canStart() {
                return canBeTempted() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return canBeTempted() && super.shouldContinue();
            }
        });
        goalSelector.add(5, new LandBirdFollowOwnerGoal(this));
        goalSelector.add(6, new FollowParentGoal(this, 1.2) {
            @Override
            public boolean canStart() {
                return !isTame() && getMode() == Mode.ROAM && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !isTame() && getMode() == Mode.ROAM && super.shouldContinue();
            }

            @Override
            public void stop() {
                super.stop();
                getNavigation().stop();
            }
        });
        goalSelector.add(7, new WanderAroundFarGoal(this, 0.7) {
            @Override
            public boolean canStart() {
                return getMode() == Mode.ROAM && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return getMode() == Mode.ROAM && super.shouldContinue();
            }
        });
        goalSelector.add(8, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(PLUMAGE, DEFAULT_PLUMAGE);
        dataTracker.startTracking(BABY_REACTION, NO_REACTION);
        dataTracker.startTracking(MODE, Mode.ROAM.ordinal());
        dataTracker.startTracking(AWAITING_OWNER, false);
        dataTracker.startTracking(NIGHT_REST, false);
        dataTracker.startTracking(GLIDING, false);
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        if (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION) {
            choosePlumageFor(world.getBiome(getBlockPos()));
        } else {
            randomizePlumage();
        }
        return super.initialize(world, difficulty, reason, entityData, entityNbt);
    }

    /** Natural spawns take the plumage configured for their biome. */
    private void choosePlumageFor(RegistryEntry<Biome> biome) {
        List<Integer> matches = new ArrayList<>();
        for (int plumage = 1; plumage <= PLUMAGE_COUNT; plumage++) {
            if (IsekaiConfig.plumageBiomes(plumage).test(biome)) {
                matches.add(plumage);
            }
        }
        if (matches.isEmpty()) {
            randomizePlumage();
        } else {
            setPlumage(matches.get(random.nextInt(matches.size())));
        }
    }

    public static boolean canSpawn(EntityType<LandBirdEntity> type, ServerWorldAccess world, SpawnReason reason,
                                   BlockPos pos, Random random) {
        // Unlike most animals, land birds also live on sand, terracotta, mud and snow.
        BlockState ground = world.getBlockState(pos.down());
        boolean walkable = ground.isIn(BlockTags.ANIMALS_SPAWNABLE_ON) || ground.isIn(BlockTags.DIRT)
                || ground.isIn(BlockTags.SAND) || ground.isIn(BlockTags.TERRACOTTA) || ground.isOf(Blocks.SNOW_BLOCK);
        return walkable && isLightLevelValidForNaturalSpawn(world, pos);
    }

    public int getPlumage() {
        return dataTracker.get(PLUMAGE);
    }

    private void setPlumage(int plumage) {
        dataTracker.set(PLUMAGE, plumage >= 1 && plumage <= PLUMAGE_COUNT ? plumage : DEFAULT_PLUMAGE);
    }

    public void randomizePlumage() {
        setPlumage(random.nextInt(PLUMAGE_COUNT) + 1);
    }

    public void initializeHatchling(int plumage) {
        setBaby(true);
        setPlumage(plumage);
        dataTracker.set(AWAITING_OWNER, true);
    }

    public Mode getMode() {
        return Mode.fromId(dataTracker.get(MODE));
    }

    public boolean isSitting() {
        return getMode() == Mode.SIT || dataTracker.get(NIGHT_REST);
    }

    private boolean canBeTempted() {
        return !isSitting() && !hasPassengers() && !hasVehicle();
    }

    @Override
    public boolean isImmobile() {
        // AbstractHorseEntity only freezes a dead horse while it is ridden and saddled, which lets the
        // AI keep fleeing during our long death animation.
        return isDead() || isSitting() || super.isImmobile();
    }

    @Override
    public boolean eatsGrass() {
        return false;
    }

    @Override
    public EntityView method_48926() {
        // This unnamed Tameable method supplies the world for owner lookup.
        return getWorld();
    }

    @Override
    public float getScaleFactor() {
        // The type already uses adult dimensions. Juveniles get a chicken-sized hitbox.
        return isBaby() ? BABY_HITBOX_SCALE : 1.0f;
    }

    @Override
    public boolean canSprintAsVehicle() {
        // Lets the rider's sprint key work while mounted, as it does for camels.
        return true;
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity rider) {
        float speed = (float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        // Walking and sprinting (sprint key) are configurable shares of the base speed; walking never
        // outpaces sprinting.
        float sprint = speed * IsekaiConfig.ridingSprintSpeedPercent() / 100.0f;
        float riding = rider.isSprinting() ? sprint
                : Math.min(sprint, speed * IsekaiConfig.ridingWalkSpeedPercent() / 100.0f);
        // Gliding scales the walking or sprinting speed by a configurable percentage.
        return isGlidingNow() ? riding * IsekaiConfig.glideSpeedPercent() / 100.0f : riding;
    }

    /** Ridden birds sprint when the rider does; others sprint once they move fast enough. */
    private boolean isSprintGait() {
        if (getControllingPassenger() instanceof PlayerEntity rider) {
            return rider.isSprinting();
        }
        return groundSpeed >= SPRINT_SPEED_THRESHOLD;
    }

    /**
     * Puts the rider's thighs on the back. Vanilla places the passenger's feet at this offset minus 0.35, and
     * a riding player's thighs sit 0.586 above their feet; the back itself sinks while the bird walks or
     * runs, so the seat eases toward the height of the current gait.
     */
    @Override
    public double getMountedHeightOffset() {
        return seatBack / 16.0 * ADULT_MODEL_SCALE + SEAT_CLEARANCE - RIDER_THIGH_HEIGHT + RIDER_HEIGHT_OFFSET;
    }

    private float targetSeatBack() {
        if (isAirborne() || groundSpeed < STANDING_SPEED) {
            return SEAT_BACK_IDLE;
        }
        if (isBackingUp()) {
            return SEAT_BACK_WALK_BACK;
        }
        return isSprintGait() ? SEAT_BACK_SPRINT : SEAT_BACK_WALK;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        // The mount bone sits five model pixels behind the entity origin.
        float bodyAngle = getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        double distance = SADDLE_SEAT_BACK * ADULT_MODEL_SCALE;
        double offsetX = MathHelper.sin(bodyAngle) * distance;
        double offsetZ = -MathHelper.cos(bodyAngle) * distance;
        super.updatePassengerPosition(passenger,
                (rider, x, y, z) -> positionUpdater.accept(rider, x + offsetX, y, z + offsetZ));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ActionResult command = interactCommand(player, hand);
        if (command.isAccepted()) {
            return command;
        }
        if (player.isSpectator() || (isTame() && player.shouldCancelInteraction())) {
            return ActionResult.PASS;
        }

        boolean client = getWorld().isClient;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(Items.GOLDEN_CARROT)) {
            return feedGoldenCarrot(player, stack, client);
        }
        if (stack.isOf(Items.CARROT) && isTame() && (isBaby() || getHealth() < getMaxHealth())) {
            if (!client) {
                consume(player, stack);
                playSound(getEatSound(), 1.0f, 1.0f);
                regenerateFromCarrot();
                if (isBaby()) {
                    growUp(BABY_GROWTH_SECONDS_PER_CARROT);
                    react(FEED_REACTION, 40);
                }
            }
            return ActionResult.success(client);
        }
        if (!isTame() || getMode() == Mode.SIT) {
            return ActionResult.PASS;
        }
        return super.interactMob(player, hand);
    }

    /** Golden carrots heal fully, start breeding for adults and speed up a tame chick's growth. */
    private ActionResult feedGoldenCarrot(PlayerEntity player, ItemStack stack, boolean client) {
        boolean heal = getHealth() < getMaxHealth();
        boolean grow = isBaby() && isTame();
        if (client) {
            // The client cannot see the breeding cooldown, so it only rules out the obvious no-op.
            return heal || grow || !isBaby() ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        boolean breed = !isBaby() && canStartBreeding() && canEat();
        if (!heal && !grow && !breed) {
            return ActionResult.PASS;
        }
        consume(player, stack);
        playSound(getEatSound(), 1.0f, 1.0f);
        if (heal) {
            setHealth(getMaxHealth());
        }
        if (breed) {
            lovePlayer(player);
        }
        if (grow) {
            growUp(BABY_GROWTH_SECONDS_PER_CARROT);
            react(FEED_REACTION, 40);
        }
        return ActionResult.CONSUME;
    }

    /** Each carrot adds ten seconds of Regeneration I, up to a minute. */
    private void regenerateFromCarrot() {
        StatusEffectInstance current = getStatusEffect(StatusEffects.REGENERATION);
        int remaining = current != null && current.getAmplifier() == 0 ? current.getDuration() : 0;
        addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,
                Math.min(MAX_CARROT_REGEN_TICKS, remaining + CARROT_REGEN_TICKS), 0));
    }

    public ActionResult interactCommand(PlayerEntity player, Hand hand) {
        if (player.isSpectator() || !isAlive()) {
            return ActionResult.PASS;
        }
        boolean client = getWorld().isClient;
        ItemStack stack = player.getStackInHand(hand);
        boolean awaitingOwner = dataTracker.get(AWAITING_OWNER);
        if (!isTame() && awaitingOwner) {
            if (!client) {
                claim(player);
            }
            return ActionResult.success(client);
        }
        if (!isTame() && stack.isOf(Items.CARROT)) {
            if (!client) {
                consume(player, stack);
                playSound(getEatSound(), 1.0f, 1.0f);
                regenerateFromCarrot();
                if (random.nextInt(100) < IsekaiConfig.tameChancePercent()) {
                    claim(player);
                } else {
                    // Smoke puffs, like a horse refusing its rider.
                    getWorld().sendEntityStatus(this, (byte) 6);
                }
            }
            return ActionResult.success(client);
        }
        if (isTame() && player.shouldCancelInteraction() && player.getUuid().equals(getOwnerUuid())) {
            if (!client) {
                cycleMode(player);
            }
            return ActionResult.success(client);
        }
        return ActionResult.PASS;
    }

    private void claim(PlayerEntity player) {
        bondWithPlayer(player);
        dataTracker.set(AWAITING_OWNER, false);
        if (isBaby()) {
            getNavigation().stop();
            react(HAPPY_REACTION, 50);
        }
    }

    private void cycleMode(PlayerEntity player) {
        Mode mode = getMode().next();
        dataTracker.set(MODE, mode.ordinal());
        getNavigation().stop();
        if (mode == Mode.SIT) {
            removeAllPassengers();
            setVelocity(getVelocity().multiply(0, 1, 0));
        }
        player.sendMessage(Text.translatable("message.isekai_creature.yokibird.mode",
                Text.translatable(mode.translationKey)), true);
    }

    private static void consume(PlayerEntity player, ItemStack stack) {
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
    }

    private void react(byte type, int duration) {
        dataTracker.set(BABY_REACTION, type);
        babyReactionTicks = duration;
    }

    @Override
    public void tick() {
        ticking = true;
        try {
            tickBird();
        } finally {
            ticking = false;
        }
    }

    private void tickBird() {
        if (!getWorld().isClient) {
            updateSummon();
            updateNightRest();
        }
        super.tick();
        airTicks = isOnGround() || isTouchingWater() || hasVehicle() ? 0 : airTicks + 1;
        double dx = getX() - prevX;
        double dz = getZ() - prevZ;
        groundSpeed += ((float) Math.sqrt(dx * dx + dz * dz) - groundSpeed) * SPEED_SMOOTHING;
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float along = (float) (-MathHelper.sin(yaw) * dx + MathHelper.cos(yaw) * dz);
        forwardSpeed += (along - forwardSpeed) * SPEED_SMOOTHING;
        seatBack += (targetSeatBack() - seatBack) * SEAT_SMOOTHING;
        updateGliding();
        // Spread wings break the fall: only the height dropped with folded wings counts when landing. This runs
        // on both sides, because the server tracks a ridden bird's fall from the rider's movement packets.
        if (isGlidingNow() || isSlowFalling()) {
            fallDistance = 0;
        }
        if (babyReactionTicks > 0 && --babyReactionTicks == 0) {
            dataTracker.set(BABY_REACTION, NO_REACTION);
        }
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world) {
            @Override
            public boolean startMovingTo(Entity target, double speed) {
                // Our own goals path from inside tick(). A call from outside it that sends the bird to a player is
                // Callable Horses answering its owner's whistle.
                if (!ticking && target instanceof PlayerEntity player && !getWorld().isClient) {
                    onSummoned(player, speed);
                }
                return super.startMovingTo(target, speed);
            }
        };
    }

    /**
     * Called to its owner: wakes up if asleep and runs over. A bird told to sit gets up and switches to following,
     * since one that sat back down on arrival could not be ridden.
     */
    private void onSummoned(PlayerEntity player, double speed) {
        if (hasPassengers() || !isAlive()) {
            return;
        }
        if (getMode() == Mode.SIT) {
            dataTracker.set(MODE, Mode.FOLLOW.ordinal());
            player.sendMessage(Text.translatable("message.isekai_creature.yokibird.mode",
                    Text.translatable(Mode.FOLLOW.translationKey)), true);
        }
        summoner = player;
        summonSpeed = speed;
        summonTicks = SUMMON_TIMEOUT_TICKS;
        summonAwakeTicks = 0;
        ownerAnchor = null;
        ownerStillTicks = 0;
        dataTracker.set(NIGHT_REST, false);
    }

    private void updateSummon() {
        if (summoner == null) {
            if (summonAwakeTicks > 0) {
                summonAwakeTicks--;
            }
            return;
        }
        if (--summonTicks <= 0 || !summoner.isAlive() || summoner.isSpectator() || summoner.getWorld() != getWorld()
                || hasPassengers() || hasVehicle() || getMode() == Mode.SIT) {
            summoner = null;
        } else if (squaredDistanceTo(summoner) <= SUMMON_ARRIVE_DISTANCE_SQ) {
            summoner = null;
            summonAwakeTicks = SUMMON_STAY_AWAKE_TICKS;
        }
    }

    private boolean isAnsweringCall() {
        return summoner != null || summonAwakeTicks > 0;
    }

    private boolean isAirborne() {
        return airTicks >= FLAP_AIR_TICKS;
    }

    private boolean isBackingUp() {
        return forwardSpeed < -STANDING_SPEED && -forwardSpeed > groundSpeed * 0.6f;
    }

    /** The rider holding jump, seen from whichever side has their input (server, or the rider's client). */
    private boolean isRiderHoldingJump() {
        return getControllingPassenger() instanceof PlayerEntity rider && rider.jumping;
    }

    private void updateGliding() {
        boolean holding = isRiderHoldingJump();
        if (!isOnGround() && holding) {
            jumpLocked = true;
        } else if (!holding) {
            jumpLocked = false;
        }
        if (!getWorld().isClient) {
            dataTracker.set(GLIDING, !isOnGround() && !isTouchingWater() && holding);
        }
    }

    /** Actively flapping: a rider holding jump in the air (local prediction for the rider's own client). */
    private boolean isFlapping() {
        if (getWorld().isClient && isLogicalSideForUpdatingMovement()) {
            return !isOnGround() && isRiderHoldingJump();
        }
        return dataTracker.get(GLIDING);
    }

    @Override
    public void travel(Vec3d movementInput) {
        super.travel(movementInput);
        // A rider holding jump glides at the configured rate; a ridden bird otherwise drops at full speed,
        // while a bird on its own spreads its wings and drifts down.
        if (isGlidingNow() || isSlowFalling()) {
            Vec3d velocity = getVelocity();
            double limit = isGlidingNow() ? -IsekaiConfig.glideFallSpeedPerTick() : -SLOW_FALL_SPEED;
            if (velocity.y < limit) {
                setVelocity(velocity.x, limit, velocity.z);
            }
        }
    }

    /** A rider holding jump in the air: the wings are spread and hold the fall back. */
    private boolean isGlidingNow() {
        return isAlive() && isRiderHoldingJump() && !isOnGround() && !isTouchingWater() && !isInLava()
                && !hasNoGravity() && !isClimbing();
    }

    /** Falling with no rider: the wings open on their own. */
    private boolean isSlowFalling() {
        return isAlive() && getControllingPassenger() == null && !isOnGround() && !isTouchingWater()
                && !isInLava() && !hasNoGravity() && !isClimbing();
    }

    @Override
    public boolean canJump() {
        // In the air the jump key glides, so the client must not charge (and queue) a jump with it. After
        // landing it stays locked until the key is released, or letting go would launch a jump.
        if (getWorld().isClient) {
            return super.canJump() && isOnGround() && !jumpLocked;
        }
        return super.canJump();
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        // Falls with folded wings hurt the bird and its rider as they would a horse (the landing sound is swapped
        // in playSound). Gliding and slow falling reset fallDistance every tick, so they land safely.
        return super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    private boolean isNight() {
        World world = getWorld();
        long dayTime = Math.floorMod(world.getTimeOfDay(), DAY_TICKS);
        return world.getDimension().hasSkyLight() && dayTime >= NIGHT_START && dayTime < NIGHT_END;
    }

    private void updateNightRest() {
        World world = getWorld();
        updateAlarm();
        boolean night = isNight();
        boolean ownerSettled = updateOwnerSettled(night);
        boolean resting = night && ownerSettled && !isAlarmed() && !isAnsweringCall()
                && !isTouchingWater() && !isInLava() && !hasPassengers() && !hasVehicle()
                && (isOnGround() || hasGroundUnderfoot(world));
        if (resting && !dataTracker.get(NIGHT_REST)) {
            getNavigation().stop();
            setVelocity(getVelocity().multiply(0, 1, 0));
        }
        dataTracker.set(NIGHT_REST, resting);
    }

    /**
     * A bird following its owner stays awake at night until the owner has stood still
     * (or gone to bed) for about three seconds. Other birds always count as settled.
     */
    private boolean updateOwnerSettled(boolean night) {
        LivingEntity owner = night && isTame() && getMode() == Mode.FOLLOW ? getOwner() : null;
        if (owner == null || !owner.isAlive() || owner.getWorld() != getWorld()) {
            ownerAnchor = null;
            ownerStillTicks = 0;
            return true;
        }
        Vec3d position = owner.getPos();
        if (ownerAnchor == null || position.squaredDistanceTo(ownerAnchor) > OWNER_MOVED_DISTANCE_SQ) {
            ownerAnchor = position;
            ownerStillTicks = 0;
        } else {
            ownerStillTicks++;
        }
        // Give the bird time to walk over before it settles down next to its owner.
        return ownerStillTicks >= OWNER_STILL_TICKS_TO_SLEEP
                && (squaredDistanceTo(owner) <= SLEEP_NEAR_OWNER_DISTANCE_SQ
                || ownerStillTicks >= OWNER_STILL_TICKS_TO_SLEEP_AWAY);
    }

    public boolean isAlarmed() {
        return alarmTicks > 0;
    }

    @Nullable
    public LivingEntity getThreat() {
        return threat;
    }

    /** Stays awake while the attacker or any monster is close, then counts down to sleep. */
    private void updateAlarm() {
        if (alarmTicks <= 0) {
            return;
        }
        if (threat != null && (!threat.isAlive() || threat.getWorld() != getWorld()
                || squaredDistanceTo(threat) > THREAT_SAFE_DISTANCE_SQ)) {
            threat = null;
        }
        if (threat == null && age % 10 == 0) {
            threat = getWorld().getEntitiesByClass(HostileEntity.class,
                            getBoundingBox().expand(HOSTILE_SCAN_RANGE), Entity::isAlive).stream()
                    .min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
        }
        if (threat != null) {
            alarmTicks = Math.max(alarmTicks, ALARM_REFRESH_TICKS);
        }
        if (--alarmTicks == 0) {
            threat = null;
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean asleep = dataTracker.get(NIGHT_REST);
        boolean hurt = super.damage(source, amount);
        // A bird told to sit holds its place; one sleeping or wandering at night runs for safety.
        if (hurt && !getWorld().isClient && isAlive() && getMode() != Mode.SIT && (asleep || isNight())) {
            alarmTicks = ALARM_TICKS;
            if (source.getAttacker() instanceof LivingEntity attacker && attacker != this) {
                threat = attacker;
            }
        }
        return hurt;
    }

    private boolean hasGroundUnderfoot(World world) {
        Box bounds = getBoundingBox();
        Box underfoot = new Box(bounds.minX, bounds.minY - 1.0 / 16.0, bounds.minZ,
                bounds.maxX, bounds.minY, bounds.maxZ);
        return world.getBlockCollisions(this, underfoot).iterator().hasNext();
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return !isBaby() && stack.isOf(Items.GOLDEN_CARROT);
    }

    private boolean canStartBreeding() {
        return getBreedingAge() == 0 && !isSitting() && !hasPassengers() && !hasVehicle();
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        return other != this && other instanceof LandBirdEntity mate
                && canStartBreeding() && mate.canStartBreeding() && isInLove() && mate.isInLove();
    }

    @Override
    public void breed(ServerWorld world, AnimalEntity mate) {
        if (!canBreedWith(mate)) {
            return;
        }

        int plumage = random.nextInt(PLUMAGE_COUNT) + 1;
        ItemEntity egg = new ItemEntity(world, getX(), getY(), getZ(), new ItemStack(IsekaiItem.egg(plumage)));
        egg.setToDefaultPickupDelay();
        if (world.spawnEntity(egg)) {
            super.breed(world, mate, null);
        }
    }

    @Nullable
    @Override
    public LandBirdEntity createChild(ServerWorld world, PassiveEntity other) {
        if (!(other instanceof LandBirdEntity mate)) {
            return null;
        }
        LandBirdEntity child = IsekaiEntity.LAND_BIRD.create(world);
        if (child != null) {
            child.setPlumage(random.nextBoolean() ? getPlumage() : mate.getPlumage());
            setChildAttributes(mate, child);
            child.setHealth(child.getMaxHealth());
        }
        return child;
    }

    @Override
    public boolean hasArmorSlot() {
        return true;
    }

    @Override
    public boolean isHorseArmor(ItemStack stack) {
        return stack.getItem() instanceof HorseArmorItem;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt(PLUMAGE_NBT_KEY, getPlumage());
        nbt.putInt("BirdMode", getMode().ordinal());
        nbt.putBoolean("AwaitingOwner", dataTracker.get(AWAITING_OWNER));
        ItemStack armor = items.getStack(ARMOR_SLOT);
        if (!armor.isEmpty()) {
            nbt.put(ARMOR_NBT_KEY, armor.writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setPlumage(nbt.getInt(PLUMAGE_NBT_KEY));
        dataTracker.set(MODE, Mode.fromId(nbt.getInt("BirdMode")).ordinal());
        dataTracker.set(AWAITING_OWNER, !isTame() && nbt.getBoolean("AwaitingOwner"));
        if (nbt.contains(ARMOR_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            ItemStack armor = ItemStack.fromNbt(nbt.getCompound(ARMOR_NBT_KEY));
            if (isHorseArmor(armor)) {
                items.setStack(ARMOR_SLOT, armor);
            }
        }
        updateSaddle();
    }

    @Override
    protected void updateSaddle() {
        super.updateSaddle();
        if (getWorld().isClient) {
            return;
        }

        ItemStack stack = items.getStack(ARMOR_SLOT);
        equipStack(EquipmentSlot.CHEST, stack);
        // The horse inventory already drops the armor on death.
        setEquipmentDropChance(EquipmentSlot.CHEST, 0.0f);

        EntityAttributeInstance armor = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        armor.removeModifier(HORSE_ARMOR_BONUS_ID);
        if (stack.getItem() instanceof HorseArmorItem horseArmor && horseArmor.getBonus() != 0) {
            armor.addTemporaryModifier(new EntityAttributeModifier(
                    HORSE_ARMOR_BONUS_ID, "Horse armor bonus", horseArmor.getBonus(),
                    EntityAttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public void onInventoryChanged(Inventory inventory) {
        ItemStack previousArmor = getEquippedStack(EquipmentSlot.CHEST);
        super.onInventoryChanged(inventory);
        ItemStack currentArmor = getEquippedStack(EquipmentSlot.CHEST);
        if (age > 20 && isHorseArmor(currentArmor) && previousArmor != currentArmor) {
            playSound(SoundEvents.ENTITY_HORSE_ARMOR, 0.5f, 1.0f);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return IsekaiSound.LAND_BIRD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return IsekaiSound.LAND_BIRD_DEATH;
    }

    @Override
    protected SoundEvent getEatSound() {
        return IsekaiSound.LAND_BIRD_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return IsekaiSound.LAND_BIRD_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return IsekaiSound.LAND_BIRD_ANGRY;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Footsteps come from the walk animations' footfall keyframes (see onSoundKeyframe), so they match
        // the feet instead of firing every 1.7 blocks, which turned into a rattle at riding speed. This also
        // silences the horse's hoof, gallop and breathing sounds.
    }

    @Override
    protected void playJumpSound() {
        playSound(IsekaiSound.LAND_BIRD_JUMP, 0.35f, 1.0f);
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 480;
    }

    @Override
    protected float getSoundVolume() {
        return isBaby() ? 0.45f : 0.6f;
    }

    @Override
    protected boolean shouldAmbientStand() {
        // The horse's random rearing also calls out; land birds do not rear.
        return false;
    }

    @Override
    public void playAmbientSound() {
        if (dataTracker.get(NIGHT_REST)) {
            return;
        }
        // In a flock only some birds speak up, so a crowd sounds about as busy as a few birds.
        int neighbours = getWorld().getEntitiesByClass(LandBirdEntity.class,
                getBoundingBox().expand(AMBIENT_FLOCK_RANGE), bird -> bird != this && bird.isAlive()).size();
        if (neighbours > 0 && random.nextFloat() * (neighbours + 1) > AMBIENT_FLOCK_VOICES) {
            return;
        }
        super.playAmbientSound();
    }

    /** Client-side sound cues placed in the animations: footfalls and wing beats. */
    private void onSoundKeyframe(String cue) {
        World world = getWorld();
        if (!world.isClient || isSilent()) {
            return;
        }
        float pitch = isBaby() ? 1.35f + random.nextFloat() * 0.15f : 0.92f + random.nextFloat() * 0.16f;
        if (cue.equals("step")) {
            if (!isOnGround() || isTouchingWater()) {
                return;
            }
            float weight = isBaby() ? 0.45f : hasPassengers() ? 1.0f : 0.8f;
            // The ground's own step sound, low and soft, under a padded claw step.
            BlockSoundGroup ground = world.getBlockState(getSteppingPos()).getSoundGroup();
            world.playSound(getX(), getY(), getZ(), ground.getStepSound(), getSoundCategory(),
                    ground.getVolume() * 0.14f * weight, ground.getPitch() * 0.82f, false);
            world.playSound(getX(), getY(), getZ(), IsekaiSound.LAND_BIRD_STEP, getSoundCategory(),
                    0.32f * weight, pitch, false);
        } else if (cue.equals("flap")) {
            world.playSound(getX(), getY(), getZ(), IsekaiSound.LAND_BIRD_FLAP, getSoundCategory(),
                    0.3f, pitch, false);
        }
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        // AbstractHorseEntity#handleFallDamage hardcodes the horse landing sound.
        super.playSound(sound == SoundEvents.ENTITY_HORSE_LAND ? IsekaiSound.LAND_BIRD_LAND : sound, volume, pitch);
    }

    @Override
    protected void updatePostDeath() {
        int animationTicks = isBaby() ? 50 : 65;
        // Vanilla removes the body as soon as its counter reaches 20, so hand over on the last tick.
        if (deathTime < animationTicks - 1) {
            deathTime++;
        } else {
            super.updatePostDeath();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        PhaseAnimationController<LandBirdEntity> body = new PhaseAnimationController<>(this, "bird", 4, state -> {
            RawAnimation animation = selectAnimation();
            ((PhaseAnimationController<?>) state.getController()).setPlayback(gaitPlayback(animation));
            return state.setAndContinue(animation);
        });
        body.setSoundKeyframeHandler(event -> onSoundKeyframe(event.getKeyframeData().getSound()));
        controllers.add(body);
        // Layered on top of the body animation: only the wing bones move. Rising or a rider holding jump
        // flaps; a bird on its own spreads its wings to glide down, while a ridden one keeps them folded.
        AnimationController<LandBirdEntity> wings = new AnimationController<>(this, "wings", 4, state -> {
            if (isBaby() || isDead() || isSitting() || !isAirborne()) {
                return PlayState.STOP;
            }
            boolean rising = getY() - prevY > 0.02;
            if (isFlapping() || rising) {
                return state.setAndContinue(ADULT_FLAP);
            }
            if (getControllingPassenger() != null) {
                return PlayState.STOP;
            }
            return state.setAndContinue(ADULT_GLIDE);
        });
        wings.setSoundKeyframeHandler(event -> onSoundKeyframe(event.getKeyframeData().getSound()));
        controllers.add(wings);
    }

    /** Gaits follow ground speed between a slow amble and a natural top cadence (see MAX_*_PLAYBACK). */
    private double gaitPlayback(RawAnimation animation) {
        if (animation == ADULT_WALK || animation == BABY_WALK) {
            float design = animation == ADULT_WALK ? WALK_DESIGN_SPEED : BABY_WALK_DESIGN_SPEED;
            return MathHelper.clamp(groundSpeed / design, MIN_GAIT_PLAYBACK, MAX_WALK_PLAYBACK);
        }
        if (animation == ADULT_SPRINT || animation == BABY_RUN) {
            float design = animation == ADULT_SPRINT ? SPRINT_DESIGN_SPEED : BABY_RUN_DESIGN_SPEED;
            return MathHelper.clamp(groundSpeed / design, MIN_GAIT_PLAYBACK, MAX_SPRINT_PLAYBACK);
        }
        if (animation == ADULT_WALK_BACK) {
            return MathHelper.clamp(groundSpeed / WALK_BACK_DESIGN_SPEED, MIN_GAIT_PLAYBACK, MAX_WALK_BACK_PLAYBACK);
        }
        return 1.0;
    }

    private RawAnimation selectAnimation() {
        if (isDead()) {
            return isBaby() ? BABY_DEATH : ADULT_DEATH;
        }
        if (isSitting()) {
            return isBaby() ? BABY_SIT : ADULT_SIT;
        }
        if (isBaby()) {
            if (groundSpeed >= STANDING_SPEED) {
                return groundSpeed >= BABY_RUN_SPEED_THRESHOLD ? BABY_RUN : BABY_WALK;
            }
            return switch (dataTracker.get(BABY_REACTION)) {
                case FEED_REACTION -> BABY_FEED;
                case HAPPY_REACTION -> BABY_HAPPY;
                default -> BABY_IDLE;
            };
        }
        if (isAirborne()) {
            return ADULT_AIR;
        }
        if (groundSpeed < STANDING_SPEED) {
            return ADULT_IDLE;
        }
        if (isBackingUp()) {
            return ADULT_WALK_BACK;
        }
        return isSprintGait() ? ADULT_SPRINT : ADULT_WALK;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public enum Mode {
        ROAM("mode.isekai_creature.yokibird.roam"),
        SIT("mode.isekai_creature.yokibird.sit"),
        FOLLOW("mode.isekai_creature.yokibird.follow");

        private static final Mode[] VALUES = values();
        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        private Mode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        private static Mode fromId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : ROAM;
        }
    }
}
