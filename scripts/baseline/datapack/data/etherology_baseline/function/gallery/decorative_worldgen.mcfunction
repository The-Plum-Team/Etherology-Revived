# Visual staging only. This does not test biome, feature, tree, or crop generation.
fill 0 96 0 10 105 10 minecraft:air
fill 0 96 0 10 96 10 minecraft:smooth_stone
fill 0 96 0 10 96 0 minecraft:green_concrete
setblock 0 96 10 minecraft:sea_lantern
setblock 10 96 10 minecraft:sea_lantern

# Peach wood family and foliage.
fill 1 97 2 1 99 2 etherology:peach_log
fill 2 97 2 2 99 2 etherology:stripped_peach_log
fill 3 97 2 3 99 2 etherology:weeping_peach_log
setblock 4 97 2 etherology:peach_planks
setblock 5 97 2 etherology:peach_stairs
setblock 6 97 2 etherology:peach_slab
setblock 7 97 2 etherology:peach_fence
setblock 8 97 2 etherology:peach_leaves
setblock 9 97 2 etherology:peach_trapdoor

# Slitherite and Attrahite swatches.
setblock 1 97 4 etherology:slitherite
setblock 2 97 4 etherology:polished_slitherite
setblock 3 97 4 etherology:polished_slitherite_bricks
setblock 4 97 4 etherology:chiseled_polished_slitherite
setblock 5 97 4 etherology:chiseled_polished_slitherite_bricks
setblock 6 97 4 etherology:cracked_polished_slitherite_bricks
setblock 7 97 4 etherology:attrahite
setblock 8 97 4 etherology:attrahite_bricks
setblock 9 97 4 etherology:attrahite_brick_stairs

# Stable material blocks and representative Golden Forest plants.
setblock 1 97 7 etherology:azel_block
setblock 2 97 7 etherology:ethril_block
setblock 3 97 7 etherology:ebony_block
setblock 4 97 7 etherology:potted_peach_sapling
setblock 5 96 7 minecraft:dirt
setblock 5 97 7 etherology:beamer[age=3,is_farmland=false]
setblock 6 96 7 minecraft:dirt
setblock 6 97 7 etherology:thuja[age=25,shape=bush]
setblock 7 97 6 etherology:peach_log
setblock 7 97 7 etherology:forest_lantern[age=4,facing=south]
setblock 8 96 7 minecraft:grass_block
setblock 8 97 7 etherology:lightelet

# Bay label.
fill 5 97 9 5 99 9 minecraft:quartz_pillar
setblock 5 99 10 minecraft:oak_wall_sign[facing=south]
data merge block 5 99 10 {front_text:{color:"black",has_glowing_text:1b,messages:['{"text":"DECORATIVE","color":"dark_green"}','{"text":"& WORLDGEN","color":"dark_green"}','{"text":"visual staging","color":"gray","italic":true}','{"text":"x 0..10","color":"dark_gray"}']}}
