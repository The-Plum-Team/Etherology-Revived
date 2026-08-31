# Rebuild every fixed visual-only gallery bay.
function etherology_baseline:gallery/internal/clear
function etherology_baseline:gallery/decorative_worldgen
function etherology_baseline:gallery/functional_machines
function etherology_baseline:gallery/seals_aspects
function etherology_baseline:gallery/storage_utilities
function etherology_baseline:gallery/combat_equipment

tellraw @s [{"text":"Etherology 0.1.7 visual gallery rebuilt at ","color":"gold"},{"text":"0 96 0","color":"yellow"},{"text":". Run ","color":"gold"},{"text":"/function etherology_baseline:gallery/teleport","color":"aqua"},{"text":" for the overview.","color":"gold"}]
