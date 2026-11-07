package com.frostworks.client;

import java.util.ArrayList;
import java.util.List;

import com.frostworks.Frostworks;
import com.frostworks.network.BeltPassagePayload;
import com.frostworks.room.RoomWalls;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltModel;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Belt passages drawn with our casing sheet ({@code textures/block/belt_passage.png}, laid out as Create's
 * belt casing sheet). The server tells which segments are passages ({@link BeltPassagePayload}); this wraps Create's
 * belt model and, on those segments, swaps the casing sheet as Create's own {@link BeltModel} does for andesite.
 */
public class BeltPassageModel extends BakedModelWrapper<BakedModel> {

    /** The casing sheet of Create's belt model (brass) to ours. */
    public static final SpriteShiftEntry SHIFT = SpriteShifter.get(
            ResourceLocation.fromNamespaceAndPath("create", "block/belt/brass_belt_casing"),
            Frostworks.id("block/belt_passage"));
    private static final ModelProperty<Boolean> PASSAGE = new ModelProperty<>();
    /** Which of our strips the segment shows: bit 0 the top one, bit 1 + 2D value the side ones. */
    private static final ModelProperty<Integer> FLAPS = new ModelProperty<>();
    private static final ResourceLocation FLAP_TOP = Frostworks.id("block/belt/belt_flap_default"),
            FLAP_SIDE = Frostworks.id("block/belt/belt_flap_vertical");
    private static BakedModel flapTop, flapSide;

    /** Client: the passages of the level the player is in. */
    private static final LongOpenHashSet PASSAGES = new LongOpenHashSet();
    private static ClientLevel passagesOf;

    public BeltPassageModel(BakedModel belt) {
        super(belt);
    }

    public static void receive(BeltPassagePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != passagesOf) { // another world or dimension: the server sends its passages again
            PASSAGES.clear();
            passagesOf = level;
        }
        boolean changed = payload.passage() ? PASSAGES.add(payload.pos().asLong())
                : PASSAGES.remove(payload.pos().asLong());
        BlockPos p = payload.pos();
        if (changed && level != null)
            Minecraft.getInstance().levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
    }

    /** Our strips are loaded as extra models (no block uses them). */
    public static void registerFlaps(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(FLAP_TOP));
        event.register(ModelResourceLocation.standalone(FLAP_SIDE));
    }

    /** A flat passage under a wall of ours: its top strip (drawn by {@link BeltFlapAnimator}). */
    static boolean hasTopFlap(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return PASSAGES.contains(pos.asLong()) && state.getBlock() instanceof BeltBlock
                && state.getValue(BeltBlock.CASING) && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL
                && RoomWalls.isWall(level.getBlockState(pos.above()));
    }

    /** Is this segment a belt passage, by the list the server sent (goggles)? */
    public static boolean isListed(BlockPos pos) {
        return Minecraft.getInstance().level == passagesOf && PASSAGES.contains(pos.asLong());
    }

    static long[] passages() {
        return Minecraft.getInstance().level == passagesOf ? PASSAGES.toLongArray() : new long[0];
    }

    static BakedModel flapTop() {
        return flapTop;
    }

    /** Wraps Create's belt model (after Create has put its own {@link BeltModel} on it). */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        flapTop = event.getModels().get(ModelResourceLocation.standalone(FLAP_TOP));
        flapSide = event.getModels().get(ModelResourceLocation.standalone(FLAP_SIDE));
        ResourceLocation belt = ResourceLocation.fromNamespaceAndPath("create", "belt");
        for (var entry : event.getModels().entrySet()) {
            ModelResourceLocation id = entry.getKey();
            if (id.id().equals(belt))
                entry.setValue(new BeltPassageModel(entry.getValue()));
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        data = super.getModelData(level, pos, state, data);
        // Ponder draws its blocks without their block entity's model data, so Create's belt model fell back
        // to its brass sheet and ours never saw the andesite casing: read it from the block entity then
        if (data.get(BeltModel.CASING_PROPERTY) == null
                && level.getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity be)
            data = be.getModelData();
        // level is the chunk region being built, not the client world: the list is for the world the player is in
        boolean listed = Minecraft.getInstance().level == passagesOf && PASSAGES.contains(pos.asLong());
        // a Ponder world loads its belts from the schematic, mark included (the server never sends it its passages)
        if (!listed && !com.frostworks.content.passage.BeltPassage.is(level, pos, state))
            return data;
        return data.derive().with(PASSAGE, true).with(FLAPS, flaps(level, pos, state)).build();
    }

    /**
     * Our strips, for a look of a piece with the walls (the top strip of a flat belt under a wall of ours is
     * {@link BeltFlapAnimator}'s): on a belt laid on its side (rotation only), a side strip toward each wall of ours beside it; on a
     * sloped belt, none.
     */
    private static int flaps(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int flaps = 0;
        BeltSlope slope = state.getValue(BeltBlock.SLOPE);
        Direction facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        // the top strip of a flat belt swings with the items: drawn every frame by BeltFlapAnimator, not here
        if (slope == BeltSlope.SIDEWAYS)
            for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()})
                if (RoomWalls.isWall(level.getBlockState(pos.relative(side))))
                    flaps |= 1 << (1 + side.get2DDataValue());
        return flaps;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet types = super.getRenderTypes(state, rand, data);
        Integer flaps = data.get(FLAPS);
        // the strip texture is see-through (alpha 142), drawn like the door strips
        return flaps == null || flaps == 0 ? types : ChunkRenderTypeSet.union(types, ChunkRenderTypeSet.of(RenderType.translucent()));
    }

    /** The strips drawn for the segment, turned to its direction (the models stand across a belt running along Z). */
    private static List<BakedQuad> flapQuads(BlockState state, Direction side, RandomSource rand, int flaps) {
        List<BakedQuad> out = new ArrayList<>();
        Direction facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        if ((flaps & 1) != 0 && flapTop != null)
            out.addAll(turned(flapTop, facing.getAxis() == Direction.Axis.X ? 90 : 0, state, side, rand));
        for (Direction d : Direction.Plane.HORIZONTAL)
            if ((flaps & 1 << (1 + d.get2DDataValue())) != 0 && flapSide != null)
                // the side strip stands on the east: turn it clockwise from east to the wall's side
                out.addAll(turned(flapSide, Math.floorMod(d.get2DDataValue() - Direction.EAST.get2DDataValue(), 4) * 90,
                        state, side, rand));
        return out;
    }

    private static List<BakedQuad> turned(BakedModel model, int y, BlockState state, Direction side, RandomSource rand) {
        List<BakedQuad> quads = model.getQuads(state, null, rand, ModelData.EMPTY, null);
        if (side != null)
            return List.of(); // the strips stand inside the block: all their faces come with the unculled ones
        return y == 0 ? quads : QuadTransformers.applying(BlockModelRotation.by(0, y).getRotation().blockCenterToCorner())
                .process(quads);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                    RenderType renderType) {
        Integer flaps = data.get(FLAPS);
        if (renderType == RenderType.translucent() && flaps != null && flaps != 0)
            return flapQuads(state, side, rand, flaps);
        if (!Boolean.TRUE.equals(data.get(PASSAGE)) || data.get(BeltModel.CASING_PROPERTY) != CasingType.ANDESITE)
            return super.getQuads(state, side, rand, data, renderType);
        // Create would shift its brass sheet to andesite: ask for the plain brass quads and shift them to ours instead
        ModelData plain = data.derive().with(BeltModel.CASING_PROPERTY, CasingType.BRASS)
                .with(BeltModel.COVER_PROPERTY, false).build();
        List<BakedQuad> quads = new ArrayList<>(super.getQuads(state, side, rand, plain, renderType));
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            if (quad.getSprite() != SHIFT.getOriginal())
                continue;
            int[] vertices = quad.getVertices().clone();
            for (int v = 0; v < 4; v++) {
                BakedQuadHelper.setU(vertices, v, SHIFT.getTargetU(BakedQuadHelper.getU(vertices, v)));
                BakedQuadHelper.setV(vertices, v, SHIFT.getTargetV(BakedQuadHelper.getV(vertices, v)));
            }
            // a quad of our sprite: Create's own shift (brass to andesite) leaves it alone, whichever model wraps the other
            quads.set(i, new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), SHIFT.getTarget(), quad.isShade(),
                    quad.hasAmbientOcclusion()));
        }
        return quads;
    }
}
