package com.jjkdomains.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds domain interiors inside the shared domain_space dimension.
 *
 * Each domain gets:
 *  1. A hollow sphere shell made of theme-appropriate blocks
 *  2. A themed interior layout built from the sphere centre outward
 *  3. A solid floor platform players stand on
 *
 * The sphere radius is always 20 blocks so it comfortably fits all players.
 */
public class DomainSphereBuilder {

    public static final int SPHERE_RADIUS = 20;

    // ─────────────────────────────────────────────────────────────────────────
    //  Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the full domain interior at the given centre position in the
     * domain_space level. Returns the list of block positions changed so they
     * can be restored when the domain ends.
     */
    public static List<BlockPos> buildDomain(ServerLevel domainLevel,
                                              BlockPos centre,
                                              DomainInteriorType type) {
        List<BlockPos> placed = new ArrayList<>();

        buildSphereShell(domainLevel, centre, SPHERE_RADIUS, shellBlock(type), placed);
        buildFloor(domainLevel, centre, placed);
        buildInterior(domainLevel, centre, type, placed);

        return placed;
    }

    /**
     * Clears all blocks placed by buildDomain, restoring air.
     * Called when the domain expires and players are sent home.
     */
    public static void clearDomain(ServerLevel domainLevel, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            domainLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Shell construction — hollow sphere
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildSphereShell(ServerLevel level, BlockPos centre,
                                          int radius, BlockState shell,
                                          List<BlockPos> placed) {
        int r2 = radius * radius;
        int inner2 = (radius - 2) * (radius - 2); // 2-block thick shell

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int dist2 = x * x + y * y + z * z;
                    if (dist2 <= r2 && dist2 >= inner2) {
                        BlockPos pos = centre.offset(x, y, z);
                        level.setBlock(pos, shell, Block.UPDATE_ALL);
                        placed.add(pos);
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Floor — solid disc at y = centre.y - 8 so players have room
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildFloor(ServerLevel level, BlockPos centre, List<BlockPos> placed) {
        int floorY = centre.getY() - 8;
        int r = SPHERE_RADIUS - 2;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r) {
                    BlockPos pos = new BlockPos(centre.getX() + x, floorY, centre.getZ() + z);
                    level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
                    placed.add(pos);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Interior dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildInterior(ServerLevel level, BlockPos centre,
                                       DomainInteriorType type, List<BlockPos> placed) {
        switch (type) {
            case INFINITE_VOID      -> buildInfiniteVoidInterior(level, centre, placed);
            case MALEVOLENT_SHRINE  -> buildMalevolentShrineInterior(level, centre, placed);
            case DEADLY_SENTENCING  -> buildDeadlySentencingInterior(level, centre, placed);
            case COFFIN_OF_IRON_MOUNTAIN -> buildCoffinInterior(level, centre, placed);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INFINITE VOID interior
    //  Deep black void — obsidian floating shards, crying obsidian veins,
    //  end stone patches, soul sand 'ground', chorus plant clusters
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildInfiniteVoidInterior(ServerLevel level, BlockPos centre,
                                                   List<BlockPos> placed) {
        Random rng = new Random(centre.asLong());
        int floorY = centre.getY() - 8;

        // Soul sand floor overlay
        for (int x = -17; x <= 17; x++) {
            for (int z = -17; z <= 17; z++) {
                if (x * x + z * z <= 17 * 17) {
                    setBlock(level, centre.offset(x, floorY - centre.getY() + centre.getY(), z),
                             Blocks.SOUL_SAND.defaultBlockState(), placed);
                    // But use the absolute floorY
                    BlockPos fp = new BlockPos(centre.getX() + x, floorY, centre.getZ() + z);
                    level.setBlock(fp, Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_ALL);
                    // replace the bedrock we placed (already in list so just overwrite)
                    level.setBlock(fp, Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        // Floating obsidian shard clusters (8 clusters)
        for (int i = 0; i < 8; i++) {
            int cx = centre.getX() + (rng.nextInt(28) - 14);
            int cy = centre.getY() + (rng.nextInt(10) - 3);
            int cz = centre.getZ() + (rng.nextInt(28) - 14);
            buildShardCluster(level, new BlockPos(cx, cy, cz),
                    Blocks.OBSIDIAN.defaultBlockState(),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    rng, placed);
        }

        // Crying obsidian pillars from floor
        for (int i = 0; i < 5; i++) {
            int px = centre.getX() + (rng.nextInt(20) - 10);
            int pz = centre.getZ() + (rng.nextInt(20) - 10);
            int height = 4 + rng.nextInt(6);
            for (int h = 0; h < height; h++) {
                setBlock(level, new BlockPos(px, floorY + 1 + h, pz),
                        Blocks.CRYING_OBSIDIAN.defaultBlockState(), placed);
            }
        }

        // End stone scatter on floor
        scatter(level, centre, floorY + 1, 14, Blocks.END_STONE.defaultBlockState(), 30, rng, placed);

        // Chorus plant stumps
        for (int i = 0; i < 4; i++) {
            int px = centre.getX() + (rng.nextInt(16) - 8);
            int pz = centre.getZ() + (rng.nextInt(16) - 8);
            setBlock(level, new BlockPos(px, floorY + 1, pz),
                    Blocks.CHORUS_PLANT.defaultBlockState(), placed);
            setBlock(level, new BlockPos(px, floorY + 2, pz),
                    Blocks.CHORUS_FLOWER.defaultBlockState(), placed);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MALEVOLENT SHRINE interior
    //  Crimson Nether flesh — crimson nylium floor, nether wart, bone pillars,
    //  shroomlight ceiling lights, lava pools, soul fire
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildMalevolentShrineInterior(ServerLevel level, BlockPos centre,
                                                       List<BlockPos> placed) {
        Random rng = new Random(centre.asLong() + 1);
        int floorY = centre.getY() - 8;

        // Crimson nylium floor
        floorLayer(level, centre, floorY, Blocks.CRIMSON_NYLIUM.defaultBlockState(), 17, placed);

        // Nether wart scatter
        scatter(level, centre, floorY + 1, 12, Blocks.NETHER_WART.defaultBlockState(), 20, rng, placed);

        // Bone pillar columns (4 cardinal, 4 diagonal)
        int[][] pillarOffsets = {{8,8},{-8,8},{8,-8},{-8,-8},{12,0},{-12,0},{0,12},{0,-12}};
        for (int[] off : pillarOffsets) {
            int px = centre.getX() + off[0];
            int pz = centre.getZ() + off[1];
            for (int h = 1; h <= 12; h++) {
                BlockState bone = (h == 1 || h == 12) ?
                        Blocks.BONE_BLOCK.defaultBlockState() :
                        Blocks.BONE_BLOCK.defaultBlockState();
                setBlock(level, new BlockPos(px, floorY + h, pz), bone, placed);
            }
            // Shroomlight cap
            setBlock(level, new BlockPos(px, floorY + 13, pz),
                    Blocks.SHROOMLIGHT.defaultBlockState(), placed);
        }

        // Lava pools (2 small)
        for (int i = 0; i < 2; i++) {
            int lx = centre.getX() + (i == 0 ? 6 : -6);
            int lz = centre.getZ() + (i == 0 ? -3 : 3);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    setBlock(level, new BlockPos(lx + dx, floorY, lz + dz),
                            Blocks.LAVA.defaultBlockState(), placed);
                }
            }
        }

        // Crimson fence decorations
        for (int i = 0; i < 6; i++) {
            int fx = centre.getX() + (rng.nextInt(20) - 10);
            int fz = centre.getZ() + (rng.nextInt(20) - 10);
            setBlock(level, new BlockPos(fx, floorY + 1, fz),
                    Blocks.CRIMSON_FENCE.defaultBlockState(), placed);
        }

        // Soul fire accent
        scatter(level, centre, floorY + 1, 10, Blocks.SOUL_FIRE.defaultBlockState(), 6, rng, placed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DEADLY SENTENCING interior
    //  Courtroom — sandstone floor, polished stone walls, two rows of pews
    //  (oak slabs), judge's podium (chiselled stone + lectern), iron bar rail,
    //  banners, torches
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildDeadlySentencingInterior(ServerLevel level, BlockPos centre,
                                                       List<BlockPos> placed) {
        int floorY = centre.getY() - 8;
        int cx = centre.getX();
        int cz = centre.getZ();

        // Smooth sandstone floor
        floorLayer(level, centre, floorY, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 17, placed);

        // Central carpet runner (red carpet along Z axis)
        for (int z = -14; z <= 14; z++) {
            setBlock(level, new BlockPos(cx, floorY + 1, cz + z),
                    Blocks.RED_CARPET.defaultBlockState(), placed);
        }

        // Pew rows — oak slabs on both sides of the runner, 5 rows each side
        for (int row = -10; row <= 10; row += 4) {
            for (int side : new int[]{-3, -4, -5, 3, 4, 5}) {
                setBlock(level, new BlockPos(cx + side, floorY + 1, cz + row),
                        Blocks.OAK_SLAB.defaultBlockState(), placed);
            }
        }

        // Judge's raised podium at +Z end
        for (int px = -3; px <= 3; px++) {
            for (int pz = 11; pz <= 14; pz++) {
                setBlock(level, new BlockPos(cx + px, floorY + 1, cz + pz),
                        Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), placed);
            }
        }
        // Podium step
        for (int px = -2; px <= 2; px++) {
            setBlock(level, new BlockPos(cx + px, floorY + 2, cz + 13),
                    Blocks.STONE_BRICK_SLAB.defaultBlockState(), placed);
        }
        // Lectern (judge's bench centre)
        setBlock(level, new BlockPos(cx, floorY + 2, cz + 13),
                Blocks.LECTERN.defaultBlockState(), placed);

        // Iron bar railing separating dock from gallery
        for (int bx = -8; bx <= 8; bx++) {
            setBlock(level, new BlockPos(cx + bx, floorY + 1, cz),
                    Blocks.IRON_BARS.defaultBlockState(), placed);
            setBlock(level, new BlockPos(cx + bx, floorY + 2, cz),
                    Blocks.IRON_BARS.defaultBlockState(), placed);
        }
        // Gate opening in the centre
        level.setBlock(new BlockPos(cx, floorY + 1, cz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(cx, floorY + 2, cz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Torches on pillars
        int[][] torchPos = {{-8,-8},{8,-8},{-8,8},{8,8}};
        for (int[] tp : torchPos) {
            for (int h = 1; h <= 8; h++) {
                setBlock(level, new BlockPos(cx + tp[0], floorY + h, cz + tp[1]),
                        Blocks.STONE_BRICKS.defaultBlockState(), placed);
            }
            setBlock(level, new BlockPos(cx + tp[0], floorY + 9, cz + tp[1]),
                    Blocks.TORCH.defaultBlockState(), placed);
        }

        // Wall-mounted lanterns
        for (int z2 = -12; z2 <= 12; z2 += 6) {
            setBlock(level, new BlockPos(cx - 16, floorY + 6, cz + z2),
                    Blocks.LANTERN.defaultBlockState(), placed);
            setBlock(level, new BlockPos(cx + 16, floorY + 6, cz + z2),
                    Blocks.LANTERN.defaultBlockState(), placed);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  COFFIN OF THE IRON MOUNTAIN interior
    //  Crushing black cavern — blackstone ceiling lowering toward centre,
    //  magma block floor, lava falls, basalt pillars, iron bars cage feel
    // ─────────────────────────────────────────────────────────────────────────

    private static void buildCoffinInterior(ServerLevel level, BlockPos centre,
                                             List<BlockPos> placed) {
        Random rng = new Random(centre.asLong() + 3);
        int floorY = centre.getY() - 8;
        int cx = centre.getX();
        int cz = centre.getZ();

        // Magma block floor
        floorLayer(level, centre, floorY, Blocks.MAGMA_BLOCK.defaultBlockState(), 17, placed);

        // Blackstone floor overlay (random patches)
        scatter(level, centre, floorY, 14, Blocks.BLACKSTONE.defaultBlockState(), 60, rng, placed);

        // Basalt column pillars — 6 around the arena
        int[][] basaltOffsets = {{10,0},{-10,0},{0,10},{0,-10},{7,7},{-7,-7}};
        for (int[] off : basaltOffsets) {
            int px = cx + off[0];
            int pz = cz + off[1];
            int height = 8 + rng.nextInt(5);
            for (int h = 1; h <= height; h++) {
                setBlock(level, new BlockPos(px, floorY + h, pz),
                        Blocks.BASALT.defaultBlockState(), placed);
            }
            // Iron bar cage around each pillar
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    setBlock(level, new BlockPos(px + dx, floorY + 3, pz + dz),
                            Blocks.IRON_BARS.defaultBlockState(), placed);
                }
            }
        }

        // Low blackstone ceiling that descends toward centre (crushing effect)
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                double distFromCentre = Math.sqrt(x * x + z * z);
                if (distFromCentre <= 15) {
                    // Ceiling height drops from 14 at edges to 8 at centre
                    int ceilH = (int)(14 - (6.0 * (1.0 - distFromCentre / 15.0)));
                    BlockPos ceilPos = new BlockPos(cx + x, floorY + ceilH, cz + z);
                    setBlock(level, ceilPos, Blocks.BLACKSTONE.defaultBlockState(), placed);
                    // Second layer
                    setBlock(level, ceilPos.above(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), placed);
                }
            }
        }

        // Lava falls from ceiling along walls
        int[][] lavaFalls = {{14,0},{-14,0},{0,14},{0,-14}};
        for (int[] lf : lavaFalls) {
            for (int h = 4; h <= 12; h++) {
                setBlock(level, new BlockPos(cx + lf[0], floorY + h, cz + lf[1]),
                        Blocks.LAVA.defaultBlockState(), placed);
            }
        }

        // Glowstone veins in ceiling for eerie light
        scatter(level, centre, floorY + 13, 12, Blocks.GLOWSTONE.defaultBlockState(), 15, rng, placed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static BlockState shellBlock(DomainInteriorType type) {
        return switch (type) {
            case INFINITE_VOID           -> Blocks.OBSIDIAN.defaultBlockState();
            case MALEVOLENT_SHRINE       -> Blocks.CRIMSON_HYPHAE.defaultBlockState();
            case DEADLY_SENTENCING       -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case COFFIN_OF_IRON_MOUNTAIN -> Blocks.BLACKSTONE.defaultBlockState();
        };
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state,
                                  List<BlockPos> placed) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        placed.add(pos);
    }

    private static void floorLayer(ServerLevel level, BlockPos centre, int y,
                                    BlockState state, int radius, List<BlockPos> placed) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    BlockPos pos = new BlockPos(centre.getX() + x, y, centre.getZ() + z);
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    placed.add(pos);
                }
            }
        }
    }

    private static void scatter(ServerLevel level, BlockPos centre, int y, int radius,
                                  BlockState state, int count, Random rng, List<BlockPos> placed) {
        for (int i = 0; i < count; i++) {
            int sx = centre.getX() + (rng.nextInt(radius * 2) - radius);
            int sz = centre.getZ() + (rng.nextInt(radius * 2) - radius);
            BlockPos pos = new BlockPos(sx, y, sz);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            placed.add(pos);
        }
    }

    private static void buildShardCluster(ServerLevel level, BlockPos centre,
                                           BlockState primary, BlockState accent,
                                           Random rng, List<BlockPos> placed) {
        for (int i = 0; i < 6 + rng.nextInt(5); i++) {
            int ox = rng.nextInt(5) - 2;
            int oy = rng.nextInt(5) - 2;
            int oz = rng.nextInt(5) - 2;
            BlockState bs = rng.nextBoolean() ? primary : accent;
            setBlock(level, centre.offset(ox, oy, oz), bs, placed);
        }
    }
}
