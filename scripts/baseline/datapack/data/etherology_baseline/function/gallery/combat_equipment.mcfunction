# Visual staging only. This does not test damage, blocking, enchantments, or two-handed behavior.
kill @e[type=minecraft:armor_stand,tag=eb_combat]
fill 48 96 0 58 105 10 minecraft:air
fill 48 96 0 58 96 10 minecraft:smooth_stone
fill 48 96 0 58 96 0 minecraft:red_concrete
setblock 48 96 10 minecraft:sea_lantern
setblock 58 96 10 minecraft:sea_lantern

# Ebony armor and tools.
summon minecraft:armor_stand 50.5 97.0 4.5 {Tags:["eb_gallery","eb_combat","eb_combat_ebony"],Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[300.0f,0.0f,10.0f],LeftArm:[300.0f,0.0f,-10.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] armor.head with etherology:ebony_helmet
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] armor.chest with etherology:ebony_chestplate
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] armor.legs with etherology:ebony_leggings
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] armor.feet with etherology:ebony_boots
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] weapon.mainhand with etherology:ebony_sword
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_ebony,limit=1] weapon.offhand with etherology:ebony_pickaxe

# Tuning Mace and Iron Shield.
summon minecraft:armor_stand 53.5 97.0 4.5 {Tags:["eb_gallery","eb_combat","eb_combat_mace"],Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[300.0f,0.0f,10.0f],LeftArm:[300.0f,0.0f,-10.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_mace,limit=1] weapon.mainhand with etherology:tuning_mace
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_mace,limit=1] weapon.offhand with etherology:iron_shield

# Broadsword and representative Battle Pickaxe.
summon minecraft:armor_stand 56.5 97.0 4.5 {Tags:["eb_gallery","eb_combat","eb_combat_broadsword"],Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[300.0f,0.0f,10.0f],LeftArm:[300.0f,0.0f,-10.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_broadsword,limit=1] weapon.mainhand with etherology:broadsword
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_broadsword,limit=1] weapon.offhand with etherology:ebony_battle_pickaxe

# Remaining Ebony tool silhouettes on small stands.
summon minecraft:armor_stand 49.5 97.2 8.0 {Tags:["eb_gallery","eb_combat","eb_combat_axe"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_axe,limit=1] weapon.mainhand with etherology:ebony_axe
summon minecraft:armor_stand 51.0 97.2 8.0 {Tags:["eb_gallery","eb_combat","eb_combat_hoe"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_hoe,limit=1] weapon.mainhand with etherology:ebony_hoe
summon minecraft:armor_stand 52.5 97.2 8.0 {Tags:["eb_gallery","eb_combat","eb_combat_shovel"],Invisible:1b,Invulnerable:1b,NoGravity:1b,NoBasePlate:1b,ShowArms:1b,Small:1b,Rotation:[0.0f,0.0f],Pose:{RightArm:[270.0f,0.0f,0.0f]}}
item replace entity @e[type=minecraft:armor_stand,tag=eb_combat_shovel,limit=1] weapon.mainhand with etherology:ebony_shovel

# Bay label.
fill 53 97 9 53 99 9 minecraft:quartz_pillar
setblock 53 99 10 minecraft:oak_wall_sign[facing=south]
data merge block 53 99 10 {front_text:{color:"black",has_glowing_text:1b,messages:['{"text":"COMBAT","color":"dark_red"}','{"text":"EQUIPMENT","color":"dark_red"}','{"text":"visual staging","color":"gray","italic":true}','{"text":"x 48..58","color":"dark_gray"}']}}
