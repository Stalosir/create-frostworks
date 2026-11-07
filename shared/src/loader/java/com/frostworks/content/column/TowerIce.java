package com.frostworks.content.column;

import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.frostworks.platform.Loader;
import com.frostworks.registry.FwDataMaps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The ice a tower uses, held by its controller: a small reserve of any item of {@link FwDataMaps#ICE_VALUE} (so that a
 * funnel does not stop at it), and the packed ice already taken out of it and not used yet (the credit). Counted in
 * packed ice.
 */
final class TowerIce {
    static final int SLOTS = 4, SLOT_LIMIT = 16;

    private final ItemStackHandler reserve;
    private double credit;
    /** The reserve as pipes and arms see it: ice goes in (while {@code accepts} says so), nothing comes out. */
    final IItemHandler input;

    TowerIce(Runnable onChange, BooleanSupplier accepts) {
        reserve = new ItemStackHandler(SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return FwDataMaps.iceValue(stack) > 0;
            }

            @Override
            public int getSlotLimit(int slot) {
                return SLOT_LIMIT;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChange.run();
            }
        };
        input = new Input(accepts);
    }

    /** Packed ice left: the credit and the reserve. */
    double stock() {
        double stock = credit;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = reserve.getStackInSlot(i);
            stock += s.getCount() * FwDataMaps.iceValue(s);
        }
        return stock;
    }

    /** Takes items out of the reserve until the credit reaches {@code need}; false if it runs short. */
    boolean take(double need) {
        for (int i = 0; i < SLOTS && credit < need; i++)
            while (credit < need && !reserve.getStackInSlot(i).isEmpty()) {
                ItemStack one = reserve.extractItem(i, 1, false);
                credit += FwDataMaps.iceValue(one);
            }
        return credit >= need;
    }

    /** Uses this much packed ice from the credit. */
    void use(double amount) {
        credit = Math.max(0, credit - amount);
    }

    /** Takes another tower's ice; what does not fit drops at {@code pos}. */
    void absorb(TowerIce other, Level level, BlockPos pos) {
        credit += other.credit;
        other.credit = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(reserve, other.reserve.getStackInSlot(i), false);
            other.reserve.setStackInSlot(i, ItemStack.EMPTY);
            if (!rest.isEmpty())
                Block.popResource(level, pos, rest);
        }
    }

    /** The reserve drops at {@code pos} (the last block of a tower broken, as a basin drops its items). */
    void drop(Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, pos, reserve.getStackInSlot(i));
            reserve.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Montages and tests: the reserve full of blue ice. */
    void fillWithBlueIce() {
        for (int i = 0; i < SLOTS; i++)
            reserve.setStackInSlot(i, new ItemStack(Items.BLUE_ICE, SLOT_LIMIT));
    }

    void write(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        tag.put("Ice", Loader.writeItems(reserve, registries));
        tag.putDouble("IceCredit", credit);
    }

    void read(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        Loader.readItems(reserve, registries, tag.getCompound("Ice"));
        if (reserve.getSlots() != SLOTS)
            reserve.setSize(SLOTS);
        credit = tag.getDouble("IceCredit");
    }

    /** Insert only, and only while the tower takes ice. */
    private final class Input implements IItemHandler {
        private final BooleanSupplier accepts;

        Input(BooleanSupplier accepts) {
            this.accepts = accepts;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return reserve.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return accepts.getAsBoolean() ? reserve.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return SLOT_LIMIT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return reserve.isItemValid(slot, stack);
        }
    }
}
