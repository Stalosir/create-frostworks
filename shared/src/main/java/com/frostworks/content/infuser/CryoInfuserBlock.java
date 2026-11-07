package com.frostworks.content.infuser;

import com.frostworks.registry.FwBlockEntities;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Cryogenic infuser: Create's mechanical press (shape, horizontal shaft, placement, work two blocks
 * below on a depot or a belt), with its own block entity that infuses with cold gas instead of pressing.
 */
public class CryoInfuserBlock extends MechanicalPressBlock {

    public CryoInfuserBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<MechanicalPressBlockEntity> getBlockEntityClass() {
        return (Class) CryoInfuserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalPressBlockEntity> getBlockEntityType() {
        return FwBlockEntities.CRYO_INFUSER.get();
    }
}
