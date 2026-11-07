package com.frostworks.content.infuser;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.frostworks.Frostworks;
import com.frostworks.FwConfig;
import com.frostworks.registry.FwFluids;
import com.frostworks.registry.FwRecipeTypes;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.advancement.CreateAdvancement;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.sounds.SoundSource;
import com.frostworks.registry.FwSounds;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Cryogenic infuser: Create's mechanical press (cycle, speed, depot and belt, items on the ground)
 * that infuses with cold gas. A cycle starts only if its tank holds all the gas of the recipe ({@code
 * frostworks:infusing}); the gas is used and the item replaced when the arm comes down, as the press does with its
 * recipe.
 * <ul>
 * <li>tank: cold gas only, {@code infuserTankCapacity}, filled by pipes on every face but the two shaft faces;</li>
 * <li>one fluid at a time: gas or Frosium, never both; the fluid is lost when the block is broken;</li>
 * <li>stress: {@code infuserStressImpact} su per rpm (the press's by default);</li>
 * <li>Over a basin, it freezes what is in it ({@code frostworks:freezing}, water -> ice), as the press
 * compacts, with {@code infuserIceGas} from its tank for each one; no other basin recipe (no compacting).</li>
 * </ul>
 */
public class CryoInfuserBlockEntity extends com.frostworks.platform.PressBase {

    /** What it is doing (goggles). */
    public enum State {
        RUNNING, NO_ROTATION, NO_GAS, NO_FROSIUM, NO_ITEM
    }

    // one fluid at a time: each tank takes its fluid only while the other one is empty
    private final FluidTank tank = new FluidTank(1000, FwFluids::isGas) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return super.isFluidValid(stack) && frosiumTank.isEmpty();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide)
                sendData();
            if (level != null && !level.isClientSide && basinChecker != null)
                basinChecker.scheduleUpdate(); // gas came in: a basin waiting for it can start
        }
    };
    /** Frosium (any cold liquid), for the recipes that take it (the chilled ores). */
    private final FluidTank frosiumTank = new FluidTank(1000, FwFluids::isLiquid) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return super.isFluidValid(stack) && tank.isEmpty();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide)
                sendData();
        }
    };
    /** Both tanks to pipes: each fluid goes to the tank that takes it. */
    private final IFluidHandler fluids = new CombinedTankWrapper(tank, frosiumTank);
    /** Last game tick an item with a recipe waited for gas. */
    private long gasMissingAt = -100;
    /** Last game tick an item waited for Frosium. */
    private long frosiumMissingAt = -100;
    private State state = State.NO_ITEM;

    public CryoInfuserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    /** The press's cycle, with the arm held down on the item before it is infused (see {@link Infusing}). */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.remove(pressingBehaviour);
        pressingBehaviour = new Infusing(this);
        behaviours.add(pressingBehaviour);
    }

    /**
     * Create's press cycle (240 steps, the item processed at step 120, the head all the way down from 120 to 160),
     * where the arm stays down on the item for {@code infuserDwellTicks} (2 s by default) just before step 120: the
     * infusion takes its time, blowing cold gas (sound, particles at the tip), and the item turns at the end of it. Same
     * on both sides, so the arm is drawn held down too.
     * <p>
     * Step 120 is done here, as Create does it but without the press's crushing sound.
     */
    static class Infusing extends PressingBehaviour {
        private final CryoInfuserBlockEntity infuser;
        private int dwell;

        Infusing(CryoInfuserBlockEntity be) {
            super(be);
            infuser = be;
        }

        /** The arm is held down on the item (both sides). */
        boolean dwelling() {
            return running && dwell > 0 && runningTicks == CYCLE / 2 - 1;
        }

        /** How far down the arm is, 0 to 1: all the way down while it dwells and on Create's plateau (120 to 160). */
        float armDown(float partialTicks) {
            int r = Math.abs(runningTicks);
            if (running && r >= CYCLE / 2 - 1 && r <= CYCLE * 2 / 3)
                return 1;
            return getRenderedHeadOffset(partialTicks);
        }

        @Override
        public void tick() {
            int half = CYCLE / 2;
            var level = getWorld();
            if (running && runningTicks < half && runningTicks + getRunningTickSpeed() >= half
                    && dwell < FwConfig.get(FwConfig.INFUSER_DWELL_TICKS)) {
                // held just short of step 120: the item is processed only once the dwell is over
                // a slowed-down frost sound (sounds.json), three times over the dwell so it spreads over all of it
                int dwellTicks = FwConfig.get(FwConfig.INFUSER_DWELL_TICKS);
                if (!level.isClientSide && dwell % Math.max(1, dwellTicks / 3) == 0 && dwell < dwellTicks - 5)
                    level.playSound(null, getPos().below(), FwSounds.INFUSE.get(), SoundSource.BLOCKS, 1f,
                            0.95f + level.random.nextFloat() * 0.1f);
                if (level.isClientSide)
                    infuser.tipParticles();
                dwell++;
                runningTicks = half - 1;
                prevRunningTicks = runningTicks;
                return;
            }
            if (!running || runningTicks >= half)
                dwell = 0;
            if (running && runningTicks == half && !level.isClientSide) {
                // Create's step 120 (items on the ground; the depot and the belt process on their side), no sound
                if (inWorld())
                    applyInWorld();
                if (onBasin())
                    applyOnBasin(); // Freezing in a basin, as the press compacts
                infuser.sendData();
                prevRunningTicks = runningTicks;
                runningTicks += getRunningTickSpeed();
                return;
            }
            super.tick();
        }
    }

    /** Down position of the arm's lower face at rest: our head is drawn 10 px high, 6 of them out. */
    public static final float ARM_REST = 6 / 16f;

    /** How far below its model position the arm is drawn (blocks): 6 px at rest, Create's stroke at the bottom. */
    public float armOffset(float partialTicks) {
        float stroke = pressingBehaviour.mode == null ? 0 : pressingBehaviour.mode.headOffset;
        float down = pressingBehaviour instanceof Infusing infusing ? infusing.armDown(partialTicks)
                : pressingBehaviour.getRenderedHeadOffset(partialTicks);
        return ARM_REST + down * Math.max(0, stroke - ARM_REST);
    }

    /** Cold gas blown out all around the tip of the arm while it dwells on the item (client). */
    void tipParticles() {
        RandomSource r = level.random;
        double tipY = worldPosition.getY() - armOffset(0) + 0.05;
        for (int i = 0; i < 2; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            level.addParticle(r.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE,
                    worldPosition.getX() + 0.5 + dx * 0.33, tipY, worldPosition.getZ() + 0.5 + dz * 0.33,
                    dx * 0.06, 0.005, dz * 0.06);
        }
    }

    // ---- Recipes and gas ----------------------------------------------------------------------------------------

    @Nullable
    public InfusingRecipe recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty())
            return null;
        Optional<InfusingRecipe> found = com.frostworks.platform.Loader.<InfusingRecipe>recipeFor(level,
                FwRecipeTypes.INFUSING.getType(), stack);
        return found.orElse(null);
    }

    /**
     * What the infuser does with an item: the fluids it takes and what it makes of one item. An infusing recipe first
     * (a pack's own recipe on a food wins), else food preserving (the same food with our trait).
     */
    private record Job(int gas, int liquid, java.util.function.UnaryOperator<ItemStack> make) {}

    @Nullable
    private Job jobFor(ItemStack stack) {
        InfusingRecipe recipe = recipeFor(stack);
        if (recipe != null)
            return new Job(recipe.gasAmount(), recipe.frosiumAmount(), input -> infused(recipe, input));
        if (level == null || stack.isEmpty() || !FoodPreserving.available())
            return null;
        FoodPreservingRecipe preserving = preservingFor(stack);
        return preserving == null ? null : new Job(preserving.gasAmount(), preserving.liquidAmount(),
                input -> FoodPreserving.preserve(input, preserving.level()));
    }

    /**
     * The food preserving recipe for this food: of all that match (cold gas, Frosium, a pack's), the highest level
     * whose fluid is in the tanks; with none, one whose fluid kind the tanks hold (its missing amount for the goggles),
     * else the first. Seen in game: the Frosium recipe was taken with only gas in the tank, which then waited
     * for Frosium.
     */
    @Nullable
    private FoodPreservingRecipe preservingFor(ItemStack stack) {
        List<FoodPreservingRecipe> all = com.frostworks.platform.Loader.<FoodPreservingRecipe>recipesFor(level,
                FwRecipeTypes.FOOD_PRESERVING.getType(), stack);
        FoodPreservingRecipe best = null;
        for (FoodPreservingRecipe recipe : all)
            if (recipe.canUse(tank.getFluid(), frosiumTank.getFluid()) && (best == null || recipe.level() > best.level()))
                best = recipe;
        if (best != null)
            return best;
        for (FoodPreservingRecipe recipe : all)
            if (recipe.gasAmount() > 0 ? !tank.isEmpty() : !frosiumTank.isEmpty())
                return recipe;
        return all.isEmpty() ? null : all.get(0);
    }

    /** Are all the fluids of this job there? Remembers when an item waited for them (goggles). */
    private boolean enoughGas(Job job) {
        boolean gas = tank.getFluidAmount() >= job.gas();
        boolean frosium = frosiumTank.getFluidAmount() >= job.liquid();
        if (level != null && !gas)
            gasMissingAt = level.getGameTime();
        if (level != null && !frosium)
            frosiumMissingAt = level.getGameTime();
        return gas && frosium;
    }

    private ItemStack infuse(Job job, ItemStack input) {
        if (job.gas() > 0)
            tank.drain(job.gas(), IFluidHandler.FluidAction.EXECUTE);
        if (job.liquid() > 0)
            frosiumTank.drain(job.liquid(), IFluidHandler.FluidAction.EXECUTE);
        return job.make().apply(input);
    }

    private ItemStack infused(InfusingRecipe recipe, ItemStack input) {
        if (recipe.marks()) // The same item, chilled
            return com.frostworks.content.cold.ChilledMark.mark(input.copyWithCount(1));
        List<ItemStack> results = com.frostworks.platform.Loader.rollResults(recipe, level.random);
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
    }

    @Override
    public boolean tryProcessOnBelt(TransportedItemStack input, List<ItemStack> outputList, boolean simulate) {
        Job recipe = jobFor(input.stack);
        if (recipe == null || !enoughGas(recipe))
            return false;
        if (simulate)
            return true;
        ItemStack result = infuse(recipe, input.stack);
        if (outputList != null && !result.isEmpty())
            outputList.add(result);
        return true;
    }

    @Override
    public boolean tryProcessInWorld(ItemEntity entity, boolean simulate) {
        ItemStack item = entity.getItem();
        Job recipe = jobFor(item);
        if (recipe == null || !enoughGas(recipe))
            return false;
        if (simulate)
            return true;
        ItemStack result = infuse(recipe, item);
        if (item.getCount() == 1)
            entity.setItem(result);
        else {
            ItemStack rest = item.copy();
            rest.shrink(1);
            entity.setItem(rest);
            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), result));
        }
        return true;
    }

    // ---- Freezing in a basin (Create's basin logic, the press's way) -----------------------------

    private static final Object FREEZING_RECIPES_KEY = new Object();

    /** Cold gas for one freezing (mB). */
    private static int iceGas() {
        return FwConfig.get(FwConfig.INFUSER_ICE_GAS);
    }

    /** Is the gas of one freezing there? Remembers when the basin waited for it (goggles). */
    private boolean enoughIceGas() {
        if (tank.getFluidAmount() >= iceGas())
            return true;
        if (level != null)
            gasMissingAt = level.getGameTime();
        return false;
    }

    /** Only our freezing recipes in a basin: the infuser compacts nothing. */
    @Override
    protected @Nullable Boolean fwRecipeType(net.minecraft.world.item.crafting.RecipeType<?> type) {
        return type == FwRecipeTypes.FREEZING.getType();
    }

    @Override
    protected Object getRecipeCacheKey() {
        return FREEZING_RECIPES_KEY;
    }

    /** The arm comes down on the basin only with the gas of one freezing in the tank. */
    @Override
    public void startProcessingBasin() {
        if (!enoughIceGas())
            return;
        super.startProcessingBasin();
    }

    /** The arm is down: the basin's content becomes its result, and the gas is used (only if it did). */
    @Override
    public boolean tryProcessInBasin(boolean simulate) {
        if (currentRecipe == null || !enoughIceGas() || !matchBasinRecipe(currentRecipe))
            return false;
        if (simulate)
            return true;
        applyBasinRecipe();
        tank.drain(iceGas(), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    @Override
    protected Optional<CreateAdvancement> getProcessedRecipeTrigger() {
        return Optional.empty(); // not Create's compacting
    }

    @Override
    public boolean canProcessInBulk() {
        return false;
    }

    /** As the press: on a basin, the next one at once if it can go on; nothing to do on a depot or a belt. */
    @Override
    public void onPressingCompleted() {
        if (pressingBehaviour.onBasin())
            super.onPressingCompleted();
    }

    // ---- Stress -------------------------------------------------------------------------------------------------

    @Override
    public float calculateStressApplied() {
        float impact = (float) (FwConfig.get(FwConfig.INFUSER_STRESS_IMPACT) * com.frostworks.energy.Energy.stressScale()); // Greate's scale
        lastStressApplied = impact;
        return impact;
    }

    // ---- Fluid handler ------------------------------------------------------------------------------------------

    /**
     * Pipes on the faces with the inlet hole only, turned with the block (null side: tools). Our
     * model shows the hole ({@code entry}) on both of its X faces, but only X+ took the gas: a pipe on the
     * other hole did not connect. Both holes take it now (the two faces across the shaft axis).
     */
    @Nullable
    @Override
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return side == null || isInletFace(side) ? fluids : null;
    }

    /** A face with the gas inlet hole: either side across the shaft (the model's X faces). */
    public boolean isInletFace(Direction side) {
        return side.getAxis() == getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise().getAxis();
    }

    /** The Frosium tank, for the chilled ores. */
    public FluidTank frosiumTank() {
        return frosiumTank;
    }

    public FluidTank tank() {
        return tank;
    }

    // ---- Tick ---------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        tank.setCapacity(FwConfig.get(FwConfig.INFUSER_TANK_CAPACITY));
        frosiumTank.setCapacity(FwConfig.get(FwConfig.INFUSER_FROSIUM_CAPACITY));
        super.tick();
        if (level.isClientSide)
            return;
        // a basin with water but not the gas: no cycle starts, the goggles say what is missing
        if (!pressingBehaviour.running && level.getGameTime() % 10 == 0 && currentRecipe != null
                && tank.getFluidAmount() < iceGas() && matchBasinRecipe(currentRecipe))
            gasMissingAt = level.getGameTime();
        State now = getSpeed() == 0 ? State.NO_ROTATION
                : pressingBehaviour.running ? State.RUNNING
                : level.getGameTime() - gasMissingAt < 40 ? State.NO_GAS
                : level.getGameTime() - frosiumMissingAt < 40 ? State.NO_FROSIUM
                : State.NO_ITEM;
        if (now != state) {
            state = now;
            sendData();
        }
    }

    public State state() {
        return state;
    }

    // ---- Goggles ------------------------------------------------------------------------------------------------

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        new LangBuilder(Frostworks.MODID).translate("goggles.infuser").forGoggles(tooltip);
        new LangBuilder(Frostworks.MODID).translate("goggles.infuser.tank", tank.getFluidAmount(), tank.getCapacity())
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        new LangBuilder(Frostworks.MODID).translate("goggles.infuser.Frosium", frosiumTank.getFluidAmount(),
                frosiumTank.getCapacity()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        new LangBuilder(Frostworks.MODID).translate("goggles.infuser." + state.name().toLowerCase(Locale.ROOT))
                .style(state == State.RUNNING ? ChatFormatting.GREEN : ChatFormatting.GOLD).forGoggles(tooltip, 1);
        return true;
    }

    // ---- NBT ----------------------------------------------------------------------------------------------------

    @Override
    protected void writeFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Gas", com.frostworks.platform.Loader.writeTank(tank, registries, new CompoundTag()));
        tag.put("Frosium", com.frostworks.platform.Loader.writeTank(frosiumTank, registries, new CompoundTag()));
        tag.putString("State", state.name());
    }

    @Override
    protected void readFw(CompoundTag tag, @Nullable HolderLookup.Provider registries, boolean clientPacket) {
        com.frostworks.platform.Loader.readTank(tank, registries, tag.getCompound("Gas"));
        com.frostworks.platform.Loader.readTank(frosiumTank, registries, tag.getCompound("Frosium"));
        try {
            state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = State.NO_ITEM;
        }
    }
}
