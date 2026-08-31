# Visual staging only. No inventories, recipes, energy, or multiblock state are configured.
fill 12 96 0 22 105 10 minecraft:air
fill 12 96 0 22 96 10 minecraft:smooth_stone
fill 12 96 0 22 96 0 minecraft:light_blue_concrete
setblock 12 96 10 minecraft:sea_lantern
setblock 22 96 10 minecraft:sea_lantern

# Workstations.
setblock 13 97 2 etherology:brewing_cauldron
setblock 15 97 2 etherology:pedestal
setblock 17 97 2 etherology:empowerment_table
setblock 19 97 2 etherology:inventor_table
setblock 21 97 2 etherology:jewelry_table

# Transmutation, detection, processing, and generation blocks.
setblock 13 97 5 etherology:armillary_sphere
setblock 15 97 5 etherology:arcanelight_detector
setblock 17 97 5 etherology:ethereal_furnace
setblock 19 97 5 etherology:spinner
setblock 21 97 5 etherology:metronome

# Additional functional blocks.
setblock 13 97 8 etherology:levitator
setblock 15 97 8 etherology:tuning_fork

# Bay label.
fill 17 97 9 17 99 9 minecraft:quartz_pillar
setblock 17 99 10 minecraft:oak_wall_sign[facing=south]
data merge block 17 99 10 {front_text:{color:"black",has_glowing_text:1b,messages:['{"text":"FUNCTIONAL","color":"dark_aqua"}','{"text":"MACHINES","color":"dark_aqua"}','{"text":"unconfigured","color":"gray","italic":true}','{"text":"x 12..22","color":"dark_gray"}']}}
