# Visual staging only. No ether, fluids, inventories, directions, or connections are configured.
kill @e[type=minecraft:armor_stand,tag=eb_utilities]
fill 36 96 0 46 105 10 minecraft:air
fill 36 96 0 46 96 10 minecraft:smooth_stone
fill 36 96 0 46 96 0 minecraft:yellow_concrete
setblock 36 96 10 minecraft:sea_lantern
setblock 46 96 10 minecraft:sea_lantern

# Ether storage and transport blocks.
setblock 37 97 2 etherology:ethereal_storage
setblock 39 97 2 etherology:ethereal_channel
setblock 41 97 2 etherology:ethereal_fork
setblock 43 97 2 etherology:ethereal_socket
setblock 45 97 2 etherology:channel_case

# General storage and fluid utility blocks.
setblock 37 97 5 etherology:crate
setblock 39 97 5 etherology:spill_barrel
setblock 41 97 5 etherology:jug
setblock 43 97 5 etherology:clay_jug
setblock 45 97 5 etherology:samovar

# Furniture/storage slab variants.
setblock 37 97 8 etherology:furniture_slab
setblock 39 97 8 etherology:closet_slab
setblock 41 97 8 etherology:shelf_slab

# Representative transport and diagnostic item models.
summon minecraft:armor_stand 43.5 97.2 8.5 {Tags:["eb_gallery","eb_utilities","eb_utility_glint"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_utility_glint,limit=1] weapon.mainhand with etherology:glint_shard
summon minecraft:armor_stand 44.5 97.2 8.5 {Tags:["eb_gallery","eb_utilities","eb_utility_key"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_utility_key,limit=1] weapon.mainhand with etherology:stream_key
summon minecraft:armor_stand 45.5 97.2 8.5 {Tags:["eb_gallery","eb_utilities","eb_utility_counter"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_utility_counter,limit=1] weapon.mainhand with etherology:warp_counter

# Bay label.
fill 41 97 9 41 99 9 minecraft:quartz_pillar
setblock 41 99 10 minecraft:oak_wall_sign[facing=south]
data merge block 41 99 10 {front_text:{color:"black",has_glowing_text:1b,messages:['{"text":"STORAGE","color":"gold"}','{"text":"& UTILITIES","color":"gold"}','{"text":"unconfigured","color":"gray","italic":true}','{"text":"x 36..46","color":"dark_gray"}']}}
