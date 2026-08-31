# Visual staging only. This does not test discovery, extraction, or aspect behavior.
kill @e[type=minecraft:armor_stand,tag=eb_aspects]
fill 24 96 0 34 105 10 minecraft:air
fill 24 96 0 34 96 10 minecraft:smooth_stone
fill 24 96 0 34 96 0 minecraft:magenta_concrete
setblock 24 96 10 minecraft:sea_lantern
setblock 34 96 10 minecraft:sea_lantern

# Four deterministic seal displays. Their NBT is fixed for repeatable scale and rotation.
setblock 25 97 2 minecraft:quartz_pillar
setblock 25 98 2 etherology:keta_seal
data merge block 25 98 2 {max_points:128.0f,radius:16,points:128.0f,pitch:0.0f,yaw:0.0f}
setblock 27 97 2 minecraft:quartz_pillar
setblock 27 98 2 etherology:rella_seal
data merge block 27 98 2 {max_points:128.0f,radius:16,points:128.0f,pitch:0.0f,yaw:0.0f}
setblock 29 97 2 minecraft:quartz_pillar
setblock 29 98 2 etherology:via_seal
data merge block 29 98 2 {max_points:128.0f,radius:16,points:128.0f,pitch:0.0f,yaw:0.0f}
setblock 31 97 2 minecraft:quartz_pillar
setblock 31 98 2 etherology:clos_seal
data merge block 31 98 2 {max_points:128.0f,radius:16,points:128.0f,pitch:0.0f,yaw:0.0f}
setblock 33 97 2 etherology:sedimentary_stone

# Keta sedimentary appearance stages.
setblock 25 97 4 etherology:sedimentary_stone_keta_low
setblock 25 97 5 etherology:sedimentary_stone_keta_medium
setblock 25 97 6 etherology:sedimentary_stone_keta_high
setblock 25 97 7 etherology:sedimentary_stone_keta_full

# Rella sedimentary appearance stages.
setblock 27 97 4 etherology:sedimentary_stone_rella_low
setblock 27 97 5 etherology:sedimentary_stone_rella_medium
setblock 27 97 6 etherology:sedimentary_stone_rella_high
setblock 27 97 7 etherology:sedimentary_stone_rella_full

# Via sedimentary appearance stages.
setblock 29 97 4 etherology:sedimentary_stone_via_low
setblock 29 97 5 etherology:sedimentary_stone_via_medium
setblock 29 97 6 etherology:sedimentary_stone_via_high
setblock 29 97 7 etherology:sedimentary_stone_via_full

# Clos sedimentary appearance stages.
setblock 31 97 4 etherology:sedimentary_stone_clos_low
setblock 31 97 5 etherology:sedimentary_stone_clos_medium
setblock 31 97 6 etherology:sedimentary_stone_clos_high
setblock 31 97 7 etherology:sedimentary_stone_clos_full

# Invisible stands expose the four registered Primoshard item models without dropped-item motion.
summon minecraft:armor_stand 25.5 97.2 8.5 {Tags:["eb_gallery","eb_aspects","eb_aspect_keta"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_aspect_keta,limit=1] weapon.mainhand with etherology:primoshard_keta
summon minecraft:armor_stand 27.5 97.2 8.5 {Tags:["eb_gallery","eb_aspects","eb_aspect_rella"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_aspect_rella,limit=1] weapon.mainhand with etherology:primoshard_rella
summon minecraft:armor_stand 29.5 97.2 8.5 {Tags:["eb_gallery","eb_aspects","eb_aspect_via"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_aspect_via,limit=1] weapon.mainhand with etherology:primoshard_via
summon minecraft:armor_stand 31.5 97.2 8.5 {Tags:["eb_gallery","eb_aspects","eb_aspect_clos"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_aspect_clos,limit=1] weapon.mainhand with etherology:primoshard_clos

# Bay label.
fill 29 97 9 29 99 9 minecraft:quartz_pillar
setblock 29 99 10 minecraft:oak_wall_sign[facing=south]
data merge block 29 99 10 {front_text:{color:"black",has_glowing_text:1b,messages:['{"text":"SEALS","color":"dark_purple"}','{"text":"& ASPECTS","color":"dark_purple"}','{"text":"visual staging","color":"gray","italic":true}','{"text":"x 24..34","color":"dark_gray"}']}}
