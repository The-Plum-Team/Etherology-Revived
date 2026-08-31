"""Freeze the Forge 1.20.1 particle-registry report contract for profile v8."""

from __future__ import annotations

import re


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v8"
SCENARIO_ID = "particle-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v8.json"
)
PROFILE_MANIFEST_SIZE = 1188
PROFILE_MANIFEST_SHA256 = (
    "20dbb0dcdce93f92b42c2565628172f64a25cd684063cd9d0f6721eb904ad995"
)
REQUIRED_MOD_IDS = (
    "etherology",
    "etherology_e2e_server_probe",
)
FORBIDDEN_MOD_IDS = (
    "etherology_e2e_harness",
    "quickskin",
    "cpm",
    "ears",
    "modmenu",
    "roughlyenoughitems",
    "emi",
)
RELOAD_PACK_DIRECTORY = "etherology-e2e-ether-source-reload"
RELOAD_PACK_ENABLED_NAME = f"file/{RELOAD_PACK_DIRECTORY}"
RELOAD_PACK_RESOURCES = (
    "pack.mcmeta",
    "data/etherology/ether_sources/default.json",
    "data/etherology/ether_sources/probe_addition.json",
)
STABLE_LOADED_MOD_IDS = (
    "architectury",
    *REQUIRED_MOD_IDS,
    "forge",
    "geckolib",
    "minecraft",
)
ETHER_SOURCE_LISTENER_CLASS = (
    "ru.feytox.etherology.data.ethersource.EtherSourceLoader"
)
ENCHANTMENT_REGISTRY_ID = "minecraft:enchantment"
NON_TREASURE_TAG_ID = "minecraft:non_treasure"
ENCHANTMENT_IDS = (
    "etherology:peal",
    "etherology:reflection",
)
ENCHANTMENTS = {
    "peal": {
        "id": "etherology:peal",
        "class": "ru.feytox.etherology.registry.misc.PealEnchantment",
        "max_level": 3,
        "min_powers": [1, 12, 23],
        "max_powers": [21, 32, 43],
        "in_non_treasure": True,
    },
    "reflection": {
        "id": "etherology:reflection",
        "class": "ru.feytox.etherology.registry.misc.ReflectionEnchantment",
        "max_level": 1,
        "min_powers": [1],
        "max_powers": [21],
        "in_non_treasure": True,
    },
}
PARTICLE_REGISTRY_ID = "minecraft:particle_type"
FEY_PARTICLE_TYPE_CLASS = (
    "ru.feytox.etherology.particle.effects.misc.FeyParticleType"
)
_PARTICLE_SPECS = (
    ("alchemy", "simple", "SimpleParticleEffect", ""),
    ("armillary_sphere", "moving", "MovingParticleEffect", "1.0 2.0 3.0"),
    (
        "electricity1",
        "electricity",
        "ElectricityParticleEffect",
        "SIMPLE",
    ),
    (
        "electricity2",
        "electricity",
        "ElectricityParticleEffect",
        "MATRIX",
    ),
    ("energy_absorption", "simple", "SimpleParticleEffect", ""),
    ("ether_dot", "moving", "MovingParticleEffect", "1.0 2.0 3.0"),
    ("ether_star", "moving", "MovingParticleEffect", "1.0 2.0 3.0"),
    ("glint_particle", "moving", "MovingParticleEffect", "1.0 2.0 3.0"),
    ("haze", "simple", "SimpleParticleEffect", ""),
    (
        "item",
        "item",
        "ItemParticleEffect",
        "minecraft:diamond 1.0 2.0 3.0",
    ),
    ("light", "light", "LightParticleEffect", "SIMPLE 1.0 2.0 3.0"),
    ("lightning_bolt", "scalable", "ScalableParticleEffect", "1.5"),
    ("redstone_flash", "simple", "SimpleParticleEffect", ""),
    ("redstone_stream", "simple", "SimpleParticleEffect", ""),
    ("resonation", "scalable", "ScalableParticleEffect", "1.5"),
    ("rising", "simple", "SimpleParticleEffect", ""),
    ("scalable_sweep", "scalable", "ScalableParticleEffect", "1.5"),
    ("seal", "seal", "SealParticleEffect", "KETA 1.0 2.0 3.0"),
    ("shockwave", "simple", "SimpleParticleEffect", ""),
    ("spark", "spark", "SparkParticleEffect", "1.0 2.0 3.0 JEWELRY"),
    ("steam", "simple", "SimpleParticleEffect", ""),
    ("vital", "moving", "MovingParticleEffect", "1.0 2.0 3.0"),
)
PARTICLES = {
    path: {
        "id": f"etherology:{path}",
        "family": family,
        "type_class": FEY_PARTICLE_TYPE_CLASS,
        "should_always_spawn": False,
        "codec_present": True,
        "parameters_factory_present": True,
        "factory_sample_effect_class": (
            f"ru.feytox.etherology.particle.effects.{effect_class}"
        ),
        "factory_sample_type_matches": True,
        "factory_sample_as_string": (
            f"etherology:{path} {parameters}"
            if parameters
            else f"etherology:{path}"
        ),
        "packet_round_trip_exact": True,
        "codec_round_trip_exact": True,
    }
    for path, family, effect_class, parameters in _PARTICLE_SPECS
}
PARTICLE_IDS = tuple(entry["id"] for entry in PARTICLES.values())
PARTICLE_PAYLOAD_FAMILIES = tuple(
    sorted({entry["family"] for entry in PARTICLES.values()})
)
SEAL_TYPE_ORDER = (
    "EMPTY",
    "KETA",
    "RELLA",
    "VIA",
    "CLOS",
)
SEAL_TYPES = {
    "empty": {
        "enum_name": "EMPTY",
        "as_string": "empty",
        "start_color": None,
        "end_color": None,
        "texture_id": None,
        "texture_light_id": None,
    },
    "keta": {
        "enum_name": "KETA",
        "as_string": "keta",
        "start_color": "128,205,247",
        "end_color": "105,128,231",
        "texture_id": "etherology:textures/block/keta_seal.png",
        "texture_light_id": (
            "etherology:textures/block/keta_seal_light.png"
        ),
    },
    "rella": {
        "enum_name": "RELLA",
        "as_string": "rella",
        "start_color": "177,229,106",
        "end_color": "106,182,81",
        "texture_id": "etherology:textures/block/rella_seal.png",
        "texture_light_id": (
            "etherology:textures/block/rella_seal_light.png"
        ),
    },
    "via": {
        "enum_name": "VIA",
        "as_string": "via",
        "start_color": "248,122,95",
        "end_color": "205,58,76",
        "texture_id": "etherology:textures/block/via_seal.png",
        "texture_light_id": "etherology:textures/block/via_seal_light.png",
    },
    "clos": {
        "enum_name": "CLOS",
        "as_string": "clos",
        "start_color": "106,182,81",
        "end_color": "208,158,89",
        "texture_id": "etherology:textures/block/clos_seal.png",
        "texture_light_id": (
            "etherology:textures/block/clos_seal_light.png"
        ),
    },
}
INITIAL_ETHER_SOURCE_ENTRIES = {
    "etherology:primoshard_clos": 4.0,
    "etherology:primoshard_keta": 4.0,
    "etherology:primoshard_rella": 4.0,
    "etherology:primoshard_via": 4.0,
    "minecraft:ancient_debris": 4.0,
    "minecraft:blaze_powder": 2.0,
    "minecraft:chorus_fruit": 2.0,
    "minecraft:crying_obsidian": 6.0,
    "minecraft:echo_shard": 12.0,
    "minecraft:ender_eye": 6.0,
    "minecraft:ender_pearl": 4.0,
    "minecraft:experience_bottle": 8.0,
    "minecraft:ghast_tear": 4.0,
    "minecraft:glowstone_dust": 1.0,
    "minecraft:gunpowder": 1.0,
    "minecraft:heart_of_the_sea": 12.0,
    "minecraft:honeycomb": 1.0,
    "minecraft:lapis_lazuli": 1.0,
    "minecraft:magma_cream": 2.0,
    "minecraft:prismarine_crystals": 1.0,
    "minecraft:quartz": 1.0,
    "minecraft:redstone": 2.0,
    "minecraft:sculk": 12.0,
}
RELOADED_ETHER_SOURCE_ENTRIES = {
    **INITIAL_ETHER_SOURCE_ENTRIES,
    "minecraft:diamond": 13.0,
    "minecraft:redstone": 9.5,
}
EXPECTED_LIFECYCLE = (
    "tags_updated_initial",
    "server_started",
    "reload_requested",
    "tags_updated_reload",
    "reload_command_returned",
    "stop_requested",
    "server_stopping",
    "server_stopped",
)


def canonical_ether_source_entries(entries: dict[str, float]) -> str:
    """Serializes a sorted Ether-source map for the probe assertion contract."""
    return ",".join(
        f"{identifier}={entries[identifier]}" for identifier in sorted(entries)
    )


EXPECTED_ASSERTION_NAMES = (
    "distribution_dedicated_server",
    "runtime_kind_loom_userdev",
    *(f"mod_loaded:{mod_id}" for mod_id in REQUIRED_MOD_IDS),
    *(f"mod_absent:{mod_id}" for mod_id in FORBIDDEN_MOD_IDS),
    "mods_forbidden_intersection_empty",
    "registry:game_event:etherology:etherology_resonance",
    "registry:game_event_etherology_ids_exact",
    "registry_internal_id",
    "registry_range",
    "registry:enchantment:etherology:peal",
    "registry:enchantment:etherology:reflection",
    "registry:enchantment_etherology_ids_exact",
    "enchantment:peal_class",
    "enchantment:reflection_class",
    "enchantment:peal_max_level",
    "enchantment:peal_min_power_level_1",
    "enchantment:peal_min_power_level_2",
    "enchantment:peal_min_power_level_3",
    "enchantment:peal_max_power_level_1",
    "enchantment:peal_max_power_level_2",
    "enchantment:peal_max_power_level_3",
    "enchantment:reflection_max_level",
    "enchantment:reflection_min_power_level_1",
    "enchantment:reflection_max_power_level_1",
    "tag:enchantment_non_treasure_contains_peal",
    "tag:enchantment_non_treasure_contains_reflection",
    "tag:enchantment_non_treasure_etherology_entries_exact",
    "enchantments_captured_after_server_data_load",
    "server_started_enchantments_rechecked",
    "enchantment_registry_stable_after_reload",
    "enchantment_properties_stable_after_reload",
    "enchantment_tag_stable_after_reload",
    *(f"registry:particle_type:{particle['id']}" for particle in PARTICLES.values()),
    "registry:particle_type_etherology_ids_exact",
    "particle_capture_error",
    "particle_payload_families_exact",
    "particle_type_classes_exact",
    "particle_should_always_spawn_false_exact",
    "particle_codecs_present_exact",
    "particle_parameters_factories_present_exact",
    "particle_factory_sample_effect_classes_exact",
    "particle_factory_sample_types_exact",
    "particle_factory_sample_as_strings_exact",
    "particle_packet_round_trips_exact",
    "particle_codec_round_trips_exact",
    "seal_type_order_exact",
    "seal_type_codec_round_trips_exact",
    "seal_type_colors_exact",
    "seal_type_textures_exact",
    "particles_captured_after_server_data_load",
    "server_started_particles_rechecked",
    "particle_registry_stable_after_reload",
    "particle_type_contract_stable_after_reload",
    "particle_wire_contract_stable_after_reload",
    "registry:loot_condition:etherology:random_chance_with_fortune",
    "registry:loot_condition_etherology_ids_exact",
    "registry:loot_condition_serializer_class",
    "loot_table:probe_table_loaded",
    "loot_table:empty_tool_items_exact",
    "loot_table:fortune_one_items_exact",
    "loot_condition_captured_after_server_data_load",
    "ether_source_listener_class",
    "ether_source_resource_directory",
    "ether_source_initial_capture_error",
    "ether_source_initial_entry_count",
    "ether_source_initial_entries_exact",
    "ether_source_initial_rella_value",
    "ether_source_initial_legacy_rela_absent",
    "ether_source_initial_redstone_value",
    "ether_source_captured_after_server_data_load",
    "server_started_ether_sources_rechecked",
    "reload_pack_directory",
    "reload_pack_resources_exact",
    "reload_pack_enabled",
    "enabled_data_packs_exact",
    "reload_failure",
    "reload_command",
    "reload_command_result",
    "reload_completed",
    "reload_update_cause",
    "reload_static_data",
    "ether_source_reloaded_capture_error",
    "ether_source_reloaded_entry_count",
    "ether_source_reloaded_entries_exact",
    "ether_source_reloaded_redstone_value",
    "ether_source_reloaded_diamond_value",
    "ether_source_reloaded_rella_value",
    "ether_source_reloaded_legacy_rela_absent",
    "ether_source_map_changed_after_reload",
    "registry_stable_after_reload",
    "tags_stable_after_reload",
    "loot_condition_registry_and_behavior_stable_after_reload",
    "loot_table_instance_replaced_after_reload",
    "server_stop_requested_after_reload",
    "tags_update_cause",
    "tags_static_data",
    "tags_update_count",
    "tag:vibrations_contains_resonance",
    "tag:vibrations_etherology_entries_exact",
    "tag:warden_can_listen_contains_resonance",
    "tag:warden_can_listen_etherology_entries_exact",
    "tags:etherology_tag_ids_exact",
    "tags_before_server_started",
    "server_started_mods_rechecked",
    "server_started_registry_rechecked",
    "server_started_tags_rechecked",
    "server_started_loot_condition_rechecked",
    "server_stop_requested_without_restart",
    "server_lifecycle_identity",
    "lifecycle",
)
EXPECTED_ASSERTION_VALUES = (
    "DEDICATED_SERVER",
    "loom-userdev",
    *("loaded" for _mod_id in REQUIRED_MOD_IDS),
    *("absent" for _mod_id in FORBIDDEN_MOD_IDS),
    "none",
    "present",
    "etherology:etherology_resonance",
    "etherology_resonance",
    "16",
    "present",
    "present",
    ",".join(ENCHANTMENT_IDS),
    ENCHANTMENTS["peal"]["class"],
    ENCHANTMENTS["reflection"]["class"],
    "3",
    "1",
    "12",
    "23",
    "21",
    "32",
    "43",
    "1",
    "1",
    "21",
    "true",
    "true",
    ",".join(ENCHANTMENT_IDS),
    "true",
    "true",
    "true",
    "true",
    "true",
    *("present" for _particle in PARTICLES.values()),
    ",".join(PARTICLE_IDS),
    "none",
    ",".join(PARTICLE_PAYLOAD_FAMILIES),
    *("true" for _particle_contract_check in range(18)),
    "present",
    "etherology:random_chance_with_fortune",
    "ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer",
    "etherology_e2e_server_probe:registry_foundation",
    "minecraft:gold_ingot,minecraft:stone",
    "minecraft:diamond,minecraft:gold_ingot,minecraft:stone",
    "true",
    ETHER_SOURCE_LISTENER_CLASS,
    "ether_sources",
    "none",
    "23",
    canonical_ether_source_entries(INITIAL_ETHER_SOURCE_ENTRIES),
    "4.0",
    "absent",
    "2.0",
    "true",
    "true",
    RELOAD_PACK_DIRECTORY,
    ",".join(RELOAD_PACK_RESOURCES),
    RELOAD_PACK_ENABLED_NAME,
    "true",
    "none",
    "reload",
    "0",
    "true",
    "SERVER_DATA_LOAD",
    "true",
    "none",
    "24",
    canonical_ether_source_entries(RELOADED_ETHER_SOURCE_ENTRIES),
    "9.5",
    "13.0",
    "4.0",
    "absent",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "SERVER_DATA_LOAD",
    "true",
    "2",
    "true",
    "etherology:etherology_resonance",
    "true",
    "etherology:etherology_resonance",
    "minecraft:vibrations,minecraft:warden_can_listen",
    "true",
    "true",
    "true",
    "true",
    "true",
    "stop(false)",
    "true",
    ">".join(EXPECTED_LIFECYCLE),
)
PROBE_LOG_PHASES = (
    EXPECTED_LIFECYCLE[0],
    "registry_foundation_checked",
    *EXPECTED_LIFECYCLE[1:],
    "report_published",
)
SERVER_LOG_TOKENS = tuple(
    f"[EtherologyServerProbe] {phase}" for phase in PROBE_LOG_PHASES
) + (
    "[EtherologyServerProbe] loom_userdev_exit_scheduled "
    "status=0 server_thread_join_timeout_ms=30000",
)
CLIENT_LOG_MARKERS = (
    "Env=CLIENT",
    "[Render thread/",
    "net/minecraft/client/",
    "Attempted to load class net/minecraft/client",
    "MinecraftClient",
    "ClientModLoader",
    "LWJGL version",
    "OpenAL initialized",
    "Sound engine started",
)
CLIENT_CLASS_PATTERN = r"net\.minecraft\.client\.[A-Za-z0-9_.$]+"
ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES = frozenset(
    {"net.minecraft.client.network.LanServerPinger"}
)


class V8ContractError(RuntimeError):
    """Reports an exact profile-v8 report contract violation."""


def exact_json_value(actual: object, expected: object) -> bool:
    """Compares JSON values without treating booleans as integers."""
    if type(actual) is not type(expected):
        return False
    if isinstance(expected, dict):
        return set(actual) == set(expected) and all(
            exact_json_value(actual[key], expected[key]) for key in expected
        )
    if isinstance(expected, list):
        return len(actual) == len(expected) and all(
            exact_json_value(actual_value, expected_value)
            for actual_value, expected_value in zip(actual, expected, strict=True)
        )
    return actual == expected


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates the exact profile-v8 report and its profile mod alignment."""
    expected_fields = {
        "schema",
        "profile_id",
        "scenario",
        "status",
        "minecraft",
        "loader",
        "loader_version",
        "java",
        "distribution",
        "runtime_kind",
        "loaded_mod_ids",
        "forbidden_mod_ids_loaded",
        "mods",
        "registry",
        "enchantments",
        "particles",
        "loot_condition",
        "ether_sources",
        "reload",
        "tags",
        "lifecycle",
        "assertions",
    }
    if set(report) != expected_fields:
        raise V8ContractError("The server probe report field inventory changed")
    expected_scalars = {
        "schema": 6,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
        "status": "passed",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
    }
    for name, expected in expected_scalars.items():
        if not exact_json_value(report.get(name), expected):
            raise V8ContractError(
                f"The server probe report {name} value changed"
            )

    loaded_mod_ids = report.get("loaded_mod_ids")
    generated_mod_ids = (
        [
            mod_id
            for mod_id in loaded_mod_ids
            if type(mod_id) is str
            and re.fullmatch(r"generated_[0-9a-f]+", mod_id) is not None
        ]
        if isinstance(loaded_mod_ids, list)
        else []
    )
    if (
        not isinstance(loaded_mod_ids, list)
        or not all(
            type(mod_id) is str
            and re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is not None
            for mod_id in loaded_mod_ids
        )
        or loaded_mod_ids != sorted(set(loaded_mod_ids))
        or len(generated_mod_ids) != 1
        or set(loaded_mod_ids)
        != set(STABLE_LOADED_MOD_IDS) | set(generated_mod_ids)
        or not set(REQUIRED_MOD_IDS).issubset(loaded_mod_ids)
        or set(FORBIDDEN_MOD_IDS).intersection(loaded_mod_ids)
    ):
        raise V8ContractError("The full loaded mod id inventory is invalid")
    if not exact_json_value(report.get("forbidden_mod_ids_loaded"), []):
        raise V8ContractError("The loaded forbidden mod intersection is not empty")
    expected_mods = {
        **{mod_id: {"loaded": True} for mod_id in REQUIRED_MOD_IDS},
        **{mod_id: {"loaded": False} for mod_id in FORBIDDEN_MOD_IDS},
    }
    if not exact_json_value(report.get("mods"), expected_mods):
        raise V8ContractError("The server probe mod subset changed")
    if not exact_json_value(report.get("registry"), {
        "registry_id": "minecraft:game_event",
        "event_id": "etherology:etherology_resonance",
        "internal_id": "etherology_resonance",
        "range": 16,
        "etherology_event_ids": ["etherology:etherology_resonance"],
        "same_instance_at_server_started": True,
        "stable_after_reload": True,
    }):
        raise V8ContractError("The server probe registry result changed")
    if not exact_json_value(report.get("enchantments"), {
        "registry_id": ENCHANTMENT_REGISTRY_ID,
        "non_treasure_tag_id": NON_TREASURE_TAG_ID,
        "etherology_enchantment_ids": list(ENCHANTMENT_IDS),
        "peal": ENCHANTMENTS["peal"],
        "reflection": ENCHANTMENTS["reflection"],
        "non_treasure_etherology_enchantment_ids": list(ENCHANTMENT_IDS),
        "same_state_at_server_started": True,
        "registry_stable_after_reload": True,
        "properties_stable_after_reload": True,
        "tag_stable_after_reload": True,
    }):
        raise V8ContractError("The server probe enchantment result changed")
    if not exact_json_value(report.get("particles"), {
        "registry_id": PARTICLE_REGISTRY_ID,
        "capture_error": "",
        "etherology_particle_ids": list(PARTICLE_IDS),
        "payload_families": list(PARTICLE_PAYLOAD_FAMILIES),
        "entries": PARTICLES,
        "seal_types": {
            "order": list(SEAL_TYPE_ORDER),
            "codec_round_trips_exact": True,
            "entries": SEAL_TYPES,
        },
        "same_state_at_server_started": True,
        "registry_stable_after_reload": True,
        "type_contract_stable_after_reload": True,
        "wire_contract_stable_after_reload": True,
    }):
        raise V8ContractError("The server probe particle result changed")
    if not exact_json_value(report.get("loot_condition"), {
        "registry_id": "minecraft:loot_condition_type",
        "condition_id": "etherology:random_chance_with_fortune",
        "etherology_condition_ids": ["etherology:random_chance_with_fortune"],
        "serializer_class": (
            "ru.feytox.etherology.util.misc."
            "RandomChanceWithFortuneConditionSerializer"
        ),
        "probe_table_id": "etherology_e2e_server_probe:registry_foundation",
        "empty_tool_items": ["minecraft:gold_ingot", "minecraft:stone"],
        "fortune_one_items": [
            "minecraft:diamond",
            "minecraft:gold_ingot",
            "minecraft:stone",
        ],
        "same_state_at_server_started": True,
        "registry_and_behavior_stable_after_reload": True,
        "probe_table_instance_replaced_after_reload": True,
    }):
        raise V8ContractError("The server probe loot-condition result changed")

    expected_initial_capture = {
        "capture_error": "",
        "entries": INITIAL_ETHER_SOURCE_ENTRIES,
    }
    if not exact_json_value(report.get("ether_sources"), {
        "listener_class": ETHER_SOURCE_LISTENER_CLASS,
        "resource_directory": "ether_sources",
        "initial": expected_initial_capture,
        "server_started": expected_initial_capture,
        "reloaded": {
            "capture_error": "",
            "entries": RELOADED_ETHER_SOURCE_ENTRIES,
        },
        "same_at_server_started": True,
        "changed_after_reload": True,
    }):
        raise V8ContractError("The server probe Ether-source result changed")

    reload = report.get("reload")
    if not isinstance(reload, dict) or set(reload) != {
        "pack_directory",
        "pack_resources",
        "enabled_pack_name",
        "enabled_data_pack_names",
        "enabled_data_packs_exact",
        "command",
        "command_result",
        "failure",
        "completed",
        "update_cause",
        "should_update_static_data",
        "registry_stable",
        "tags_stable",
        "loot_condition_registry_and_behavior_stable",
        "loot_table_instance_replaced",
        "enchantment_registry_stable",
        "enchantment_properties_stable",
        "enchantment_tag_stable",
        "particle_registry_stable",
        "particle_type_contract_stable",
        "particle_wire_contract_stable",
        "stop_requested_after_completion",
    }:
        raise V8ContractError("The server probe reload result changed")
    enabled_data_pack_names = reload.get("enabled_data_pack_names")
    expected_enabled_data_pack_names = sorted(
        [
            "vanilla" if mod_id == "minecraft" else f"mod:{mod_id}"
            for mod_id in loaded_mod_ids
        ]
        + [RELOAD_PACK_ENABLED_NAME]
    )
    if (
        not isinstance(enabled_data_pack_names, list)
        or not all(
            type(pack_name) is str and pack_name
            for pack_name in enabled_data_pack_names
        )
        or enabled_data_pack_names != expected_enabled_data_pack_names
    ):
        raise V8ContractError(
            "The server probe enabled data-pack inventory changed"
        )
    expected_reload = {
        "pack_directory": RELOAD_PACK_DIRECTORY,
        "pack_resources": list(RELOAD_PACK_RESOURCES),
        "enabled_pack_name": RELOAD_PACK_ENABLED_NAME,
        "enabled_data_pack_names": enabled_data_pack_names,
        "enabled_data_packs_exact": True,
        "command": "reload",
        "command_result": 0,
        "failure": "",
        "completed": True,
        "update_cause": "SERVER_DATA_LOAD",
        "should_update_static_data": True,
        "registry_stable": True,
        "tags_stable": True,
        "loot_condition_registry_and_behavior_stable": True,
        "loot_table_instance_replaced": True,
        "enchantment_registry_stable": True,
        "enchantment_properties_stable": True,
        "enchantment_tag_stable": True,
        "particle_registry_stable": True,
        "particle_type_contract_stable": True,
        "particle_wire_contract_stable": True,
        "stop_requested_after_completion": True,
    }
    if not exact_json_value(reload, expected_reload):
        raise V8ContractError("The server probe reload result changed")

    if not exact_json_value(report.get("tags"), {
        "update_cause": "SERVER_DATA_LOAD",
        "should_update_static_data": True,
        "update_count": 2,
        "reload_update_cause": "SERVER_DATA_LOAD",
        "reload_should_update_static_data": True,
        "vibrations": {
            "id": "minecraft:vibrations",
            "contains_event": True,
            "etherology_event_ids": ["etherology:etherology_resonance"],
        },
        "warden_can_listen": {
            "id": "minecraft:warden_can_listen",
            "contains_event": True,
            "etherology_event_ids": ["etherology:etherology_resonance"],
        },
        "etherology_tag_ids": [
            "minecraft:vibrations",
            "minecraft:warden_can_listen",
        ],
        "same_membership_at_server_started": True,
        "stable_after_reload": True,
    }):
        raise V8ContractError("The server probe tag result changed")
    if not exact_json_value(report.get("lifecycle"), list(EXPECTED_LIFECYCLE)):
        raise V8ContractError("The server probe lifecycle changed")

    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V8ContractError("The server probe assertion inventory changed")
    for index, (name, value) in enumerate(
        zip(EXPECTED_ASSERTION_NAMES, EXPECTED_ASSERTION_VALUES, strict=True)
    ):
        assertion = assertions[index]
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise V8ContractError(
                f"Server probe assertion {index} has invalid fields"
            )
        if not exact_json_value(assertion, {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }):
            raise V8ContractError(
                f"Server probe assertion failed or changed: {name}"
            )

    if (
        required_mod_ids != list(REQUIRED_MOD_IDS)
        or forbidden_mod_ids != list(FORBIDDEN_MOD_IDS)
    ):
        raise V8ContractError(
            "The report mod subset differs from the tracked profile"
        )
