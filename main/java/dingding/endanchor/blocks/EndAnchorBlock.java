package dingding.endanchor.blocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.CompassItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class EndAnchorBlock extends Block {
    public static final IntProperty CHARGES;
    public BlockPos TELEPORT_POS;
    private static final ImmutableList<Vec3i> VALID_HORIZONTAL_SPAWN_OFFSETS;
    private static final ImmutableList<Vec3i> VALID_TP_OFFSETS;

    public EndAnchorBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(getDefaultState().with(CHARGES, 0));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // handle exploding in other dimensions
        if (world.getDimension().bedWorks() || world.getDimension().respawnAnchorWorks()) {
            if (!world.isClient)
                explode(state, world, pos); // explode if in overworld or nether
            return ActionResult.success(world.isClient);
        }

        // item interactions
        ItemStack mainHand = player.getStackInHand(hand);
        if (mainHand.getItem().equals(Items.END_CRYSTAL)) {
            if (canCharge(state)) {
                charge(player, world, pos, state);
                if (!player.isCreative())
                    mainHand.decrement(1);
                return ActionResult.success(world.isClient);
            } else
                return ActionResult.PASS;
        } else if (mainHand.getItem().equals(Items.COMPASS) && CompassItem.hasLodestone(mainHand)) {
            NbtCompound nbt = mainHand.getNbt();
            if (World.CODEC.parse(NbtOps.INSTANCE, nbt.get("LodestoneDimension")).result().get() != World.END)
                return ActionResult.PASS;
            GlobalPos lodestonePos = CompassItem.createLodestonePos(nbt);
            if (lodestonePos != null) {
                TELEPORT_POS = lodestonePos.getPos();
                world.playSound(null, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return ActionResult.success(world.isClient);
        } else {
            if (state.get(CHARGES) > 0 && TELEPORT_POS != null) {
                if (world.getBlockState(TELEPORT_POS).getBlock().equals(Blocks.LODESTONE)) {
                    world.setBlockState(pos, state.with(CHARGES, state.get(CHARGES)-1));
                    Optional<Vec3d> teleportPos = findTeleportPosition(EntityType.PLAYER, world, TELEPORT_POS);
                    teleportPos.ifPresent(vec3d -> player.teleport(vec3d.getX(), vec3d.getY(), vec3d.getZ()));
                } else if (world.isClient)
                    player.sendMessage(Text.literal("Lodestone missing or obstructed"), true);

                world.playSound(null, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                return ActionResult.success(world.isClient);
            }
        }

        return ActionResult.CONSUME;
    }

    private static boolean canCharge(BlockState state) {
        return state.get(CHARGES) < 4;
    }
    public static void charge(@Nullable Entity charger, World world, BlockPos pos, BlockState state) {
        BlockState blockState = state.with(CHARGES, state.get(CHARGES) + 1);
        world.setBlockState(pos, blockState, 3);
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(charger, blockState));
        world.playSound(null, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    private static boolean hasStillWater(BlockPos pos, World world) {
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isIn(FluidTags.WATER)) {
            return false;
        } else if (fluidState.isStill()) {
            return true;
        } else {
            float f = (float)fluidState.getLevel();
            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidState2 = world.getFluidState(pos.down());
                return !fluidState2.isIn(FluidTags.WATER);
            }
        }
    }

    private void explode(BlockState state, World world, final BlockPos explodedPos) {
        world.removeBlock(explodedPos, false);
        Stream<Direction> var10000 = Direction.Type.HORIZONTAL.stream();
        Objects.requireNonNull(explodedPos);
        boolean bl = var10000.map(explodedPos::offset).anyMatch((pos) -> hasStillWater(pos, world));
        final boolean bl2 = bl || world.getFluidState(explodedPos.up()).isIn(FluidTags.WATER);
        ExplosionBehavior explosionBehavior = new ExplosionBehavior() {
            public Optional<Float> getBlastResistance(Explosion explosion, BlockView world, BlockPos pos, BlockState blockState, FluidState fluidState) {
                return pos.equals(explodedPos) && bl2 ? Optional.of(Blocks.WATER.getBlastResistance()) : super.getBlastResistance(explosion, world, pos, blockState, fluidState);
            }
        };
        Vec3d vec3d = explodedPos.toCenterPos();
        world.createExplosion(null, world.getDamageSources().badRespawnPoint(vec3d), explosionBehavior, vec3d, 5.0F, true, World.ExplosionSourceType.BLOCK);
    }

    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(CHARGES);
    }
    public static int getLightLevel(BlockState state, int maxLevel) {
        return MathHelper.floor((float)(state.get(CHARGES)) / 4.0F * (float)maxLevel);
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {return true;}
    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {return getLightLevel(state, 15);}

    public static Optional<Vec3d> findTeleportPosition(EntityType<?> entity, CollisionView world, BlockPos pos) {
        Optional<Vec3d> optional = findTeleportPos(entity, world, pos, true);
        return optional.isPresent() ? optional : findTeleportPos(entity, world, pos, false);
    }
    private static Optional<Vec3d> findTeleportPos(EntityType<?> entity, CollisionView world, BlockPos pos, boolean ignoreInvalidPos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        UnmodifiableIterator<Vec3i> tpOffsets = VALID_TP_OFFSETS.iterator();

        Vec3d vec3d;
        do {
            if (!tpOffsets.hasNext())
                return Optional.empty();

            Vec3i vec3i = tpOffsets.next();
            mutable.set(pos).move(vec3i);
            vec3d = Dismounting.findRespawnPos(entity, world, mutable, ignoreInvalidPos);
        } while(vec3d == null);

        return Optional.of(vec3d);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(CHARGES) != 0) {
            if (random.nextInt(100) == 0) {
                world.playSound(null, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5,
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }

            double d = (double)pos.getX() + 0.5 + (0.5 - random.nextDouble());
            double e = (double)pos.getY() + 1.0;
            double f = (double)pos.getZ() + 0.5 + (0.5 - random.nextDouble());
            double g = (double)random.nextFloat() * 0.04;
            world.addParticle(ParticleTypes.REVERSE_PORTAL, d, e, f, 0.0, g, 0.0);
        }
    }

    static {
        CHARGES = Properties.CHARGES;
        VALID_HORIZONTAL_SPAWN_OFFSETS = ImmutableList.of(new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 0), new Vec3i(-1, 0, -1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, 1));
        VALID_TP_OFFSETS = (new ImmutableList.Builder()).addAll(VALID_HORIZONTAL_SPAWN_OFFSETS).addAll(VALID_HORIZONTAL_SPAWN_OFFSETS.stream().map(Vec3i::down).iterator()).addAll(VALID_HORIZONTAL_SPAWN_OFFSETS.stream().map(Vec3i::up).iterator()).add(new Vec3i(0, 1, 0)).build();
    }
}