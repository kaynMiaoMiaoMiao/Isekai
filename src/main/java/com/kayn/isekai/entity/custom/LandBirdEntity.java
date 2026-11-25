package com.kayn.isekai.entity.custom;

import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.entity.ai.LandBirdFollowOwnerGoal;
import com.kayn.isekai.item.IsekaiItem;
import net.minecraft.entity.Entity;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

public class LandBirdEntity extends AbstractHorseEntity implements GeoEntity {
    public static final int PLUMAGE_COUNT = 6;
    public static final float ADULT_MODEL_SCALE = 0.8f;
    private static final float BABY_WALK_MIN_STRIDE = 0.025f;
    private static final double SADDLE_SEAT_HEIGHT = 24.6 / 16.0;
    private static final double SADDLE_SEAT_BACK = 5.0 / 16.0;
    private static final int DAY_TICKS = 24_000;
    private static final int NIGHT_START = 13_000;
    private static final int NIGHT_END = 23_000;
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
    private static final byte NO_REACTION = 0;
    private static final byte FEED_REACTION = 1;
    private static final byte HAPPY_REACTION = 2;

    private static final RawAnimation ADULT_IDLE = RawAnimation.begin().thenLoop("adult.idle");
    private static final RawAnimation ADULT_WALK = RawAnimation.begin().thenLoop("adult.walk");
    private static final RawAnimation ADULT_SPRINT = RawAnimation.begin().thenLoop("adult.sprint");
    private static final RawAnimation ADULT_SIT = RawAnimation.begin().thenLoop("adult.sit");
    private static final RawAnimation ADULT_DEATH = RawAnimation.begin().thenPlayAndHold("adult.death");
    private static final RawAnimation BABY_IDLE = RawAnimation.begin().thenLoop("baby.idle");
    private static final RawAnimation BABY_WALK = RawAnimation.begin().thenLoop("baby.walk");
    private static final RawAnimation BABY_FEED = RawAnimation.begin().thenPlay("baby.feed");
    private static final RawAnimation BABY_HAPPY = RawAnimation.begin().thenPlay("baby.happy");
    private static final RawAnimation BABY_SIT = RawAnimation.begin().thenLoop("baby.sit");
    private static final RawAnimation BABY_DEATH = RawAnimation.begin().thenPlayAndHold("baby.death");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int babyReactionTicks;

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
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        randomizePlumage();
        return super.initialize(world, difficulty, reason, entityData, entityNbt);
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
        return isSitting() || super.isImmobile();
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
        // The type already uses adult dimensions. Juveniles keep their original hitbox.
        return isBaby() ? super.getScaleFactor() / ADULT_MODEL_SCALE : 1.0f;
    }

    @Override
    public double getMountedHeightOffset() {
        return SADDLE_SEAT_HEIGHT * ADULT_MODEL_SCALE;
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
        if (isBreedingItem(stack)) {
            if (client) {
                return ActionResult.SUCCESS;
            }
            if (!canStartBreeding() || !canEat()) {
                return ActionResult.PASS;
            }
            consume(player, stack);
            lovePlayer(player);
            playSound(getEatSound(), 1.0f, 1.0f);
            return ActionResult.CONSUME;
        }
        if (isBaby() && isTame() && stack.isOf(Items.CARROT)) {
            if (!client) {
                consume(player, stack);
                growUp(10);
                playSound(getEatSound(), 1.0f, 1.0f);
                react(FEED_REACTION, 40);
            }
            return ActionResult.success(client);
        }
        if (!isTame() || getMode() == Mode.SIT) {
            return ActionResult.PASS;
        }
        return super.interactMob(player, hand);
    }

    public ActionResult interactCommand(PlayerEntity player, Hand hand) {
        if (player.isSpectator() || !isAlive()) {
            return ActionResult.PASS;
        }
        boolean client = getWorld().isClient;
        ItemStack stack = player.getStackInHand(hand);
        boolean awaitingOwner = dataTracker.get(AWAITING_OWNER);
        if (!isTame() && (awaitingOwner || stack.isOf(Items.CARROT))) {
            if (!client) {
                if (!awaitingOwner) {
                    consume(player, stack);
                }
                claim(player);
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
        player.sendMessage(Text.translatable("message.isekai.land_bird.mode",
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
        if (!getWorld().isClient) {
            updateNightRest();
        }
        super.tick();
        if (babyReactionTicks > 0 && --babyReactionTicks == 0) {
            dataTracker.set(BABY_REACTION, NO_REACTION);
        }
    }

    private void updateNightRest() {
        World world = getWorld();
        long dayTime = Math.floorMod(world.getTimeOfDay(), DAY_TICKS);
        boolean resting = world.getDimension().hasSkyLight() && dayTime >= NIGHT_START && dayTime < NIGHT_END
                && !isTouchingWater() && !isInLava() && !hasPassengers() && !hasVehicle()
                && (isOnGround() || hasGroundUnderfoot(world));
        if (resting && !dataTracker.get(NIGHT_REST)) {
            getNavigation().stop();
            setVelocity(getVelocity().multiply(0, 1, 0));
        }
        dataTracker.set(NIGHT_REST, resting);
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
        return SoundEvents.ENTITY_HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_HORSE_DEATH;
    }

    @Override
    protected SoundEvent getEatSound() {
        return SoundEvents.ENTITY_HORSE_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.ENTITY_HORSE_ANGRY;
    }

    @Override
    protected void updatePostDeath() {
        int animationTicks = isBaby() ? 50 : 65;
        if (deathTime < animationTicks - 20) {
            deathTime++;
        } else {
            super.updatePostDeath();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this, "bird", 0, state -> state.setAndContinue(selectAnimation(
                        state.isMoving(), state.getLimbSwingAmount()))));
    }

    private RawAnimation selectAnimation(boolean moving, float stride) {
        if (isDead()) {
            return isBaby() ? BABY_DEATH : ADULT_DEATH;
        }
        if (isSitting()) {
            return isBaby() ? BABY_SIT : ADULT_SIT;
        }
        if (isBaby()) {
            if (stride > BABY_WALK_MIN_STRIDE) {
                return BABY_WALK;
            }
            return switch (dataTracker.get(BABY_REACTION)) {
                case FEED_REACTION -> BABY_FEED;
                case HAPPY_REACTION -> BABY_HAPPY;
                default -> BABY_IDLE;
            };
        }
        if (!moving) {
            return ADULT_IDLE;
        }
        return stride >= 0.9f ? ADULT_SPRINT : ADULT_WALK;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public enum Mode {
        ROAM("mode.isekai.land_bird.roam"),
        SIT("mode.isekai.land_bird.sit"),
        FOLLOW("mode.isekai.land_bird.follow");

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
