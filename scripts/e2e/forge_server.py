#!/usr/bin/env python3
"""Prepare the bounded Forge 1.20.1 Slitherite block probe in isolated state."""

from __future__ import annotations

import argparse
import base64
import binascii
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
from typing import BinaryIO

import forge_server_contract_v19 as contract_v19
import forge_server_contract_v21 as contract_v21
import forge_server_launch_anchor
import forge_server_launch_watchdog
import macos_guarded_java


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
PROFILE_MANIFEST_RELATIVE_PATH = Path(
    "scripts/e2e/forge-server-1.20.1-profile.json"
)
PROBE_SOURCE_RELATIVE_PATH = Path(
    "e2e-harness/forge-server/1.20.1/src/main/java/"
    "dev/theplumteam/etherology/e2e/server/RegistryFoundationServerProbe.java"
)
MEMORY_HANDOFF_SOURCE_RELATIVE_PATH = Path(
    "e2e-harness/forge-server/1.20.1/src/main/java/"
    "dev/theplumteam/etherology/e2e/server/ServerProbeMemoryHandoff.java"
)
GRADLE_TOPOLOGY_SOURCE_RELATIVE_PATH = Path(
    "e2e-harness/gradle-topology/1.20.1/src/main/java/"
    "dev/theplumteam/etherology/e2e/topology/GradleJavaExecTopologyProbe.java"
)
LAUNCH_WATCHDOG_SOURCE_RELATIVE_PATH = Path(
    "scripts/e2e/forge_server_launch_watchdog.py"
)
LAUNCH_WATCHDOG_SOURCE_SIZE = 102_143
LAUNCH_WATCHDOG_SOURCE_SHA256 = (
    "a0d69fd1a3477fe13e7379d9088273c331e461ece2c6a8e6ebb868309e9c02e5"
)
LAUNCH_ANCHOR_MODULE_RELATIVE_PATH = Path(
    "scripts/e2e/forge_server_launch_anchor.py"
)
LAUNCH_ANCHOR_MODULE_SIZE = 63_659
LAUNCH_ANCHOR_MODULE_SHA256 = (
    "8cb41e0ce36d1fa91fd2472b73e54e7e9b44974e5c13c27b692f35ce404699a5"
)
LAUNCH_ANCHOR_JAVA_SOURCE_RELATIVE_PATH = Path(
    "e2e-harness/launch-anchor/1.20.1/src/ForgeServerLaunchAnchor.java"
)
LAUNCH_ANCHOR_JAVA_SOURCE_SIZE = 41_275
LAUNCH_ANCHOR_JAVA_SOURCE_SHA256 = (
    "baca2862e9df7dd6c3da1f41583cbc4b013c45423ec77914883961dcfd202d2b"
)
MANIFEST_PATH = REPOSITORY_ROOT / PROFILE_MANIFEST_RELATIVE_PATH
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_ID = contract_v21.PROFILE_ID
SCENARIO_ID = contract_v21.SCENARIO_ID
TASK_PATH = contract_v21.TASK_PATH
REPORT_SCHEMA = contract_v21.REPORT_SCHEMA
PROFILE_MARKER_NAME = ".etherology-forge-server-e2e-profile.json"
MANAGED_BY = "scripts/e2e/forge_server.py"
GLOBAL_NATIVE_RUN_LOCK_NAME = "etherology-e2e-forge-server-native.lock"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE = "ETHERLOGY_E2E_GRADLE_JAVA"
GRADLE_TOPOLOGY_TOKEN_ENVIRONMENT_VARIABLE = (
    "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_TOKEN"
)
GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE = (
    "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_HANDOFF"
)
GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE = (
    "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_ACKNOWLEDGEMENT"
)
RUN_TOKEN_ENVIRONMENT_VARIABLE = "ETHERLOGY_E2E_FORGE_SERVER_RUN_TOKEN"
MEMORY_HANDOFF_ENVIRONMENT_VARIABLE = (
    "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_HANDOFF"
)
MEMORY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE = (
    "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_ACKNOWLEDGEMENT"
)
MEMORY_HANDOFF_FILE_NAME = ".forge-server-java-memory-handoff.json"
MEMORY_ACKNOWLEDGEMENT_FILE_NAME = ".forge-server-java-memory-ready"
SERVER_MAXIMUM_MEMORY_MB = 2048
SERVER_MAXIMUM_HEAP_BYTES = SERVER_MAXIMUM_MEMORY_MB * 1024 * 1024
SERVER_MAXIMUM_HEAP_ARGUMENT = "-Xmx2048m"
STRICT_MEMORY_POLICY_NAME = (
    macos_guarded_java.STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME
)
MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT = 3
MAXIMUM_INDIVIDUAL_JAVA_PHYSICAL_MEMORY_BYTES = 5 * 1024 * 1024 * 1024
MAXIMUM_AGGREGATE_JAVA_PHYSICAL_MEMORY_BYTES = 6 * 1024 * 1024 * 1024
LAUNCH_WATCHDOG_HEARTBEAT_TIMEOUT_SECONDS = 10.0
LAUNCH_ANCHOR_CHILD_ACKNOWLEDGEMENT_TIMEOUT_SECONDS = 15.0
SERVER_JAVA_FEATURE = 17
JAVA_VERSION_PROBE_JVM_ARGUMENTS = (
    "-Xms16m",
    "-Xmx64m",
    "-XX:MaxDirectMemorySize=32m",
    "-XX:MaxMetaspaceSize=64m",
    "-XX:ReservedCodeCacheSize=32m",
    "-XX:ActiveProcessorCount=1",
)
GRADLE_LAUNCHER_MAXIMUM_HEAP_ARGUMENT = "-Xmx2G"
GRADLE_LAUNCHER_MINIMUM_HEAP_ARGUMENT = "-Xms64m"
GRADLE_JVM_ARGUMENTS_OVERRIDE = (
    "-Dorg.gradle.jvmargs="
    f"{GRADLE_LAUNCHER_MAXIMUM_HEAP_ARGUMENT} "
    f"{GRADLE_LAUNCHER_MINIMUM_HEAP_ARGUMENT}"
)
GRADLE_INSTRUMENTATION_OVERRIDE = (
    "-Dorg.gradle.internal.instrumentation.agent=false"
)
GRADLE_OFFLINE_ARGUMENT = "--offline"
GRADLE_EXECUTABLE_SEARCH_PATH = (
    "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
)
SHARED_GRADLE_CACHE_HOME = Path.home() / ".gradle"
GRADLE_USER_HOME = STATE_ROOT / "gradle-user-home"
GRADLE_CACHE_BRIDGE_NAMES = ("caches", "jdks", "native", "wrapper")
GRADLE_USER_INIT_SCRIPT_NAMES = ("init.gradle", "init.gradle.kts")
GRADLE_DISTRIBUTION_RELATIVE_PATH = Path(
    "wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764/gradle-9.6.1"
)
GRADLE_DISTRIBUTION_INIT_README_SIZE = 119
GRADLE_DISTRIBUTION_INIT_README_SHA256 = (
    "16cf9450804c97d225bac3e2512583b628a139179fe9c6151d1a23166b66cd23"
)
GRADLE_TOPOLOGY_TASK_PATH = (
    ":forge:1.20.1:runServerProbeGradleTopologyPreflight"
)
GRADLE_TOPOLOGY_HANDOFF_FILE_NAME = (
    ".forge-server-gradle-topology-handoff"
)
GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_FILE_NAME = (
    ".forge-server-gradle-topology-ready"
)
GRADLE_TOPOLOGY_MAXIMUM_HANDOFF_SIZE = 16 * 1024
GRADLE_TOPOLOGY_TIMEOUT_SECONDS = 5 * 60
DETACHED_JAVA_EXIT_TIMEOUT_SECONDS = 45
DETACHED_JAVA_STABLE_ABSENCE_SECONDS = 15
GRADLE_TOPOLOGY_RUNTIME_PREFIX = ".forge-server-gradle-topology."
GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX = ".forge-server-wrapper-guard."
GRADLE_TOPOLOGY_COMPLETED_RUNTIME_PREFIX = (
    ".forge-server-completed-gradle-topology."
)
GRADLE_WRAPPER_GUARD_COMPLETED_RUNTIME_PREFIX = (
    ".forge-server-completed-wrapper-guard."
)
PGREP_PATH = Path("/usr/bin/pgrep")
PS_PATH = Path("/bin/ps")
MAXIMUM_JAVA_PROCESS_INVENTORY_SIZE = 64 * 1024
JAVA_PROCESS_INVENTORY_TIMEOUT_SECONDS = 2.0
MAXIMUM_PROCESS_GROUP_INVENTORY_SIZE = 1024 * 1024
PROCESS_GROUP_INVENTORY_TIMEOUT_SECONDS = 2.0
GRADLE_WRAPPER_SCRIPT_SIZE = 8692
GRADLE_WRAPPER_SCRIPT_SHA256 = (
    "fc977a94723af68aaffa4e5d60496fb4aeed1884b6b19e5e2f2fd7612673313d"
)
GRADLE_WRAPPER_PROPERTIES_RELATIVE_PATH = Path(
    "gradle/wrapper/gradle-wrapper.properties"
)
GRADLE_WRAPPER_PROPERTIES_SIZE = 339
GRADLE_WRAPPER_PROPERTIES_SHA256 = (
    "ef9f8775fd21a165a249ded98afc533818d3f6ac050f0f2f437d5285576b2257"
)
GRADLE_WRAPPER_JAR_RELATIVE_PATH = Path("gradle/wrapper/gradle-wrapper.jar")
GRADLE_WRAPPER_JAR_SIZE = 59821
GRADLE_WRAPPER_JAR_SHA256 = (
    "575098db54a998ff1c6770b352c3b16766c09848bee7555dab09afc34e8cf590"
)
RUN_TIMEOUT_SECONDS = 15 * 60
MEMORY_HANDOFF_TIMEOUT_SECONDS = RUN_TIMEOUT_SECONDS
MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS = 2.0
MAXIMUM_MEMORY_HANDOFF_SIZE = 16 * 1024
PROCESS_STOP_TIMEOUT_SECONDS = 15
PROCESS_POLL_INTERVAL_SECONDS = 0.1
MAXIMUM_PROCESS_LOG_SIZE = 64 * 1024 * 1024
MAXIMUM_SERVER_LOG_SIZE = 48 * 1024 * 1024
COMPLETION_MARKER_CONTENT = b"complete\n"
HISTORICAL_V18_PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v18"
HISTORICAL_V18_RUNTIME_RELATIVE_PATH = (
    Path("scripts/e2e/.state/runtimes") / HISTORICAL_V18_PROFILE_ID
)
HISTORICAL_V18_ATTEMPT_RELATIVE_PATH = (
    Path("scripts/e2e/.state") / f"{HISTORICAL_V18_PROFILE_ID}-run.attempted"
)
HISTORICAL_V18_ARCHIVE_RELATIVE_PATH = Path(
    "docs/evidence/forge-1.20.1/attrahite-block-registry-server-v18"
)
HISTORICAL_V18_FILE_RECORDS = {
    HISTORICAL_V18_ATTEMPT_RELATIVE_PATH: (
        94,
        "79959e5843f34260e657bd19963a8dd98c06f48214ad1cb52bec97ebcc6fdd84",
    ),
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/reports/report.json": (
        188710,
        "ff7ee4ccb6fe62b308be1603a9e9b0a7bc374e72cb3debccbf27004c8fef1030",
    ),
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH / "game/logs/latest.log": (
        18404,
        "92c3132a408437ff631501c6b38a6bea4df93a3d0e095ad22c377ee08418b87c",
    ),
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH / "game/logs/debug.log": (
        3731895,
        "68480bd8fff0f14cff3aa11921c22d73ad5a6f0160ee596e21afab16b405f1f4",
    ),
}
HISTORICAL_V18_ABSENT_RELATIVE_PATHS = (
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/reports/done.marker",
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/reports/launcher-result.json",
    HISTORICAL_V18_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/logs/latest.log",
    HISTORICAL_V18_ARCHIVE_RELATIVE_PATH,
)
HISTORICAL_V18_LIFECYCLE = (
    "tags_updated_initial",
    "server_started",
    "reload_requested",
    "tags_updated_reload",
    "reload_command_returned",
    "stop_requested",
    "server_stopping",
    "server_stopped",
)
HISTORICAL_V18_FAILURES = (
    {
        "name": "forest_lantern_jump_break_exact",
        "passed": False,
        "expected": "true",
        "actual": "false",
    },
    {
        "name": "forest_lantern_jump_break_drop_exact",
        "passed": False,
        "expected": "etherology:forest_lanternx1",
        "actual": "",
    },
    {
        "name": "forest_lantern_server_mechanics_contract_exact",
        "passed": False,
        "expected": "true",
        "actual": "false",
    },
    {
        "name": "forest_lantern_mechanics_stable_after_reload",
        "passed": False,
        "expected": "true",
        "actual": "false",
    },
    {
        "name": "forest_lantern_contract_exact",
        "passed": False,
        "expected": "true",
        "actual": "false",
    },
)
REQUIRED_MOD_IDS = contract_v19.REQUIRED_MOD_IDS
FORBIDDEN_MOD_IDS = contract_v19.FORBIDDEN_MOD_IDS
RELOAD_PACK_DIRECTORY = contract_v19.RELOAD_PACK_DIRECTORY
RELOAD_PACK_ENABLED_NAME = contract_v19.RELOAD_PACK_ENABLED_NAME
RELOAD_PACK_RESOURCES = contract_v19.RELOAD_PACK_RESOURCES
ETHER_SOURCE_LISTENER_CLASS = contract_v19.ETHER_SOURCE_LISTENER_CLASS
ENCHANTMENT_REGISTRY_ID = contract_v19.ENCHANTMENT_REGISTRY_ID
NON_TREASURE_TAG_ID = contract_v19.NON_TREASURE_TAG_ID
ENCHANTMENT_IDS = contract_v19.ENCHANTMENT_IDS
ENCHANTMENTS = contract_v19.ENCHANTMENTS
PARTICLE_REGISTRY_ID = contract_v19.PARTICLE_REGISTRY_ID
FEY_PARTICLE_TYPE_CLASS = contract_v19.FEY_PARTICLE_TYPE_CLASS
PARTICLE_IDS = contract_v19.PARTICLE_IDS
PARTICLE_PAYLOAD_FAMILIES = contract_v19.PARTICLE_PAYLOAD_FAMILIES
PARTICLES = contract_v19.PARTICLES
SEAL_TYPE_ORDER = contract_v19.SEAL_TYPE_ORDER
SEAL_TYPES = contract_v19.SEAL_TYPES
MATERIAL_ITEM_REGISTRY_ID = contract_v19.MATERIAL_ITEM_REGISTRY_ID
MATERIAL_ITEM_CLASS = contract_v19.MATERIAL_ITEM_CLASS
MATERIAL_ITEM_NBT_KEYS = contract_v19.MATERIAL_ITEM_NBT_KEYS
MATERIAL_ITEM_MAX_COUNTS = contract_v19.MATERIAL_ITEM_MAX_COUNTS
MATERIAL_ITEM_IDS = contract_v19.MATERIAL_ITEM_IDS
MATERIAL_ITEMS = contract_v19.MATERIAL_ITEMS
MATERIAL_ITEM_CANONICAL_MAX_COUNTS = (
    contract_v19.MATERIAL_ITEM_CANONICAL_MAX_COUNTS
)
MATERIAL_ITEM_CANONICAL_SAVE_REPRESENTATIONS = (
    contract_v19.MATERIAL_ITEM_CANONICAL_SAVE_REPRESENTATIONS
)
METAL_BLOCK_REGISTRY_ID = contract_v19.METAL_BLOCK_REGISTRY_ID
METAL_BLOCK_ITEM_REGISTRY_ID = contract_v19.METAL_BLOCK_ITEM_REGISTRY_ID
METAL_BLOCK_CLASS = contract_v19.METAL_BLOCK_CLASS
BLOCK_ITEM_CLASS = contract_v19.BLOCK_ITEM_CLASS
METAL_BLOCK_NBT_KEYS = contract_v19.METAL_BLOCK_NBT_KEYS
METAL_BLOCK_SPECS = contract_v19.METAL_BLOCK_SPECS
METAL_BLOCK_IDS = contract_v19.METAL_BLOCK_IDS
METAL_BLOCKS = contract_v19.METAL_BLOCKS
METAL_BLOCK_CANONICAL_PROPERTIES = contract_v19.METAL_BLOCK_CANONICAL_PROPERTIES
METAL_BLOCK_CANONICAL_SAVE_REPRESENTATIONS = (
    contract_v19.METAL_BLOCK_CANONICAL_SAVE_REPRESENTATIONS
)
METAL_BLOCK_PLACEMENT_POSITIONS = contract_v19.METAL_BLOCK_PLACEMENT_POSITIONS
METAL_BLOCK_CANONICAL_PLACEMENT_POSITIONS = (
    contract_v19.METAL_BLOCK_CANONICAL_PLACEMENT_POSITIONS
)
METAL_BLOCK_CANONICAL_PLACED_IDS = contract_v19.METAL_BLOCK_CANONICAL_PLACED_IDS
ATTRAHITE_BLOCK_REGISTRY_ID = contract_v19.ATTRAHITE_BLOCK_REGISTRY_ID
ATTRAHITE_ITEM_REGISTRY_ID = contract_v19.ATTRAHITE_ITEM_REGISTRY_ID
ATTRAHITE_BLOCK_ITEM_CLASS = contract_v19.ATTRAHITE_BLOCK_ITEM_CLASS
ATTRAHITE_BLOCK_IDS = contract_v19.ATTRAHITE_BLOCK_IDS
ATTRAHITE_LOOT_TABLE_IDS = contract_v19.ATTRAHITE_LOOT_TABLE_IDS
ATTRAHITE_RECIPE_IDS = contract_v19.ATTRAHITE_RECIPE_IDS
ATTRAHITE_ADVANCEMENT_IDS = contract_v19.ATTRAHITE_ADVANCEMENT_IDS
ATTRAHITE_PROPERTIES = contract_v19.ATTRAHITE_PROPERTIES
ATTRAHITE_TAGS = contract_v19.ATTRAHITE_TAGS
ATTRAHITE_SAVE_REPRESENTATIONS = contract_v19.ATTRAHITE_SAVE_REPRESENTATIONS
ATTRAHITE_PLACEMENT_POSITIONS = contract_v19.ATTRAHITE_PLACEMENT_POSITIONS
ATTRAHITE_PLACED_STATES = contract_v19.ATTRAHITE_PLACED_STATES
ATTRAHITE_STANDARD_LOOT = contract_v19.ATTRAHITE_STANDARD_LOOT
ATTRAHITE_RAW_FORTUNE_LOOT = contract_v19.ATTRAHITE_RAW_FORTUNE_LOOT
ATTRAHITE_RECIPES = contract_v19.ATTRAHITE_RECIPES
ATTRAHITE_ADVANCEMENTS = contract_v19.ATTRAHITE_ADVANCEMENTS
ATTRAHITE_BLOCKS = contract_v19.ATTRAHITE_BLOCKS
ATTRAHITE_ASSERTION_NAMES = contract_v19.ATTRAHITE_ASSERTION_NAMES
ATTRAHITE_ASSERTION_VALUES = contract_v19.ATTRAHITE_ASSERTION_VALUES
SLITHERITE_BLOCK_REGISTRY_ID = contract_v21.SLITHERITE_BLOCK_REGISTRY_ID
SLITHERITE_ITEM_REGISTRY_ID = contract_v21.SLITHERITE_ITEM_REGISTRY_ID
SLITHERITE_BLOCK_ITEM_CLASS = contract_v21.SLITHERITE_BLOCK_ITEM_CLASS
SLITHERITE_AGGREGATE_STATE_COUNT = contract_v21.SLITHERITE_AGGREGATE_STATE_COUNT
SLITHERITE_BLOCK_IDS = contract_v21.SLITHERITE_BLOCK_IDS
SLITHERITE_LOOT_TABLE_IDS = contract_v21.SLITHERITE_LOOT_TABLE_IDS
SLITHERITE_SELF_DROPS = contract_v21.SLITHERITE_SELF_DROPS
SLITHERITE_DOUBLE_SLAB_DROPS = contract_v21.SLITHERITE_DOUBLE_SLAB_DROPS
SLITHERITE_RECIPE_DESCRIPTIONS = contract_v21.SLITHERITE_RECIPE_DESCRIPTIONS
SLITHERITE_RECIPE_IDS = contract_v21.SLITHERITE_RECIPE_IDS
SLITHERITE_RECIPES = contract_v21.SLITHERITE_RECIPES
SLITHERITE_ADVANCEMENT_IDS = contract_v21.SLITHERITE_ADVANCEMENT_IDS
SLITHERITE_RELATED_RECIPE_DESCRIPTIONS = (
    contract_v21.SLITHERITE_RELATED_RECIPE_DESCRIPTIONS
)
SLITHERITE_RELATED_RECIPE_IDS = contract_v21.SLITHERITE_RELATED_RECIPE_IDS
SLITHERITE_RELATED_RECIPES = contract_v21.SLITHERITE_RELATED_RECIPES
SLITHERITE_PROPERTIES = contract_v21.SLITHERITE_PROPERTIES
SLITHERITE_TAGS = contract_v21.SLITHERITE_TAGS
SLITHERITE_PLACEMENT_POSITIONS = contract_v21.SLITHERITE_PLACEMENT_POSITIONS
SLITHERITE_SUPPORT_POSITIONS = contract_v21.SLITHERITE_SUPPORT_POSITIONS
SLITHERITE_PLACED_IDS = contract_v21.SLITHERITE_PLACED_IDS
SLITHERITE_PLACED_STATES = contract_v21.SLITHERITE_PLACED_STATES
SLITHERITE_SUPPORT_IDS = contract_v21.SLITHERITE_SUPPORT_IDS
SLITHERITE_NATIVE_PLACEMENT_CANONICAL = (
    contract_v21.SLITHERITE_NATIVE_PLACEMENT_CANONICAL
)
SLITHERITE_ASSERTION_NAMES = contract_v21.SLITHERITE_ASSERTION_NAMES
SLITHERITE_ASSERTION_VALUES = contract_v21.SLITHERITE_ASSERTION_VALUES
build_slitherite_blocks = contract_v21.build_slitherite_blocks
NATIVE_RUN_POSTPONED = contract_v21.NATIVE_RUN_POSTPONED
NATIVE_RUN_POSTPONED_REASON = contract_v21.NATIVE_RUN_POSTPONED_REASON
FOOD_ITEM_REGISTRY_ID = contract_v19.FOOD_ITEM_REGISTRY_ID
FOOD_ITEM_ID = contract_v19.FOOD_ITEM_ID
FOOD_ITEM_IDS = contract_v19.FOOD_ITEM_IDS
FOOD_ITEM_CLASS = contract_v19.FOOD_ITEM_CLASS
FOOD_ITEM_NBT_KEYS = contract_v19.FOOD_ITEM_NBT_KEYS
FOOD_ITEM_PROPERTIES = contract_v19.FOOD_ITEM_PROPERTIES
FOOD_ITEM_SAVE_REPRESENTATION = contract_v19.FOOD_ITEM_SAVE_REPRESENTATION
FOOD_ITEM_SAVE_REPRESENTATIONS = contract_v19.FOOD_ITEM_SAVE_REPRESENTATIONS
FOOD_ITEMS = contract_v19.FOOD_ITEMS
FOOD_CONSUMPTION_PLAYER_CLASS = contract_v19.FOOD_CONSUMPTION_PLAYER_CLASS
SERVER_STARTED_FOOD_CONSUMPTION = contract_v19.SERVER_STARTED_FOOD_CONSUMPTION
RELOADED_FOOD_CONSUMPTION = contract_v19.RELOADED_FOOD_CONSUMPTION
FOREST_LANTERN_ID = contract_v19.FOREST_LANTERN_ID
FOREST_LANTERN = contract_v19.FOREST_LANTERN
FOREST_LANTERN_ASSERTION_NAMES = contract_v19.FOREST_LANTERN_ASSERTION_NAMES
FOREST_LANTERN_ASSERTION_VALUES = contract_v19.FOREST_LANTERN_ASSERTION_VALUES
INITIAL_ETHER_SOURCE_ENTRIES = contract_v19.INITIAL_ETHER_SOURCE_ENTRIES
RELOADED_ETHER_SOURCE_ENTRIES = contract_v19.RELOADED_ETHER_SOURCE_ENTRIES
canonical_ether_source_entries = contract_v19.canonical_ether_source_entries
EXPECTED_LIFECYCLE = contract_v19.EXPECTED_LIFECYCLE
EXPECTED_ASSERTION_NAMES = contract_v21.EXPECTED_ASSERTION_NAMES
EXPECTED_ASSERTION_VALUES = contract_v21.EXPECTED_ASSERTION_VALUES
PROBE_LOG_PHASES = contract_v19.PROBE_LOG_PHASES
SERVER_LOG_TOKENS = contract_v19.SERVER_LOG_TOKENS
CLIENT_LOG_MARKERS = contract_v19.CLIENT_LOG_MARKERS
CLIENT_CLASS_PATTERN = contract_v19.CLIENT_CLASS_PATTERN
ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES = (
    contract_v19.ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES
)
FATAL_SERVER_LOG_MARKERS = (
    "A mod crashed on startup!",
    "Encountered an unexpected exception",
    "Exception in server tick loop",
    "Failed to start the minecraft server",
    "Missing or unsupported mandatory dependencies",
    "ModLoadingException",
    "Uncaught exception in thread",
    "Failed to execute reload",
    "dev.theplumteam.etherology.e2e.forge.ForgeE2eHarness",
    "[FATAL]",
    "/ERROR]",
    "/FATAL]",
)
SERVER_PROPERTIES_CONTENT = (
    "allow-flight=false\n"
    "difficulty=peaceful\n"
    "enable-command-block=false\n"
    "enable-query=false\n"
    "enable-rcon=false\n"
    "enable-status=false\n"
    "enforce-secure-profile=false\n"
    "force-gamemode=true\n"
    "gamemode=creative\n"
    "generate-structures=false\n"
    "level-name=world\n"
    "level-type=minecraft:normal\n"
    "max-players=1\n"
    "motd=Etherology dedicated-server E2E\n"
    "online-mode=false\n"
    "pvp=false\n"
    "server-ip=127.0.0.1\n"
    "server-port=0\n"
    "simulation-distance=4\n"
    "spawn-animals=false\n"
    "spawn-monsters=false\n"
    "spawn-npcs=false\n"
    "spawn-protection=0\n"
    "sync-chunk-writes=true\n"
    "view-distance=4\n"
    "white-list=false\n"
)


class E2EError(RuntimeError):
    """Reports a dedicated-server profile, isolation, or lifecycle failure."""


class ServerGuardStartError(E2EError):
    """Carries a partially attached guard so cleanup keeps exact ownership."""

    def __init__(self, message: str, server_guard: ServerJavaGuard) -> None:
        super().__init__(message)
        self.server_guard = server_guard


class WrapperGuardStartError(E2EError):
    """Carries a partially attached wrapper guard for conservative cleanup."""

    def __init__(self, message: str, wrapper_guard: WrapperJavaGuard) -> None:
        super().__init__(message)
        self.wrapper_guard = wrapper_guard


class GradleLaunchAnchorStartError(E2EError):
    """Carries every exact anchor owner retained after preparation failure."""

    def __init__(
        self,
        message: str,
        launch: GradleLaunchAnchor,
    ) -> None:
        super().__init__(message)
        self.launch = launch


@dataclass(frozen=True)
class ResolvedConfiguration:
    """Holds the tracked server profile and its resolved release owners."""

    manifest: dict[str, object]
    properties: dict[str, str]
    artifact_lane: dict[str, object]
    runtime_lane: dict[str, object]
    repository_root: Path
    profile_manifest_path: Path


@dataclass(frozen=True)
class ServerJavaGuard:
    """Carries the actual server identity and its two auxiliary processes."""

    target: macos_guarded_java.OwnedJavaProcess
    monitor: macos_guarded_java.GuardedJavaMonitor | None
    caffeinate_process: subprocess.Popen[bytes] | None
    handoff_path: Path
    acknowledgement_path: Path
    spawned_monitor_process: subprocess.Popen[bytes] | None = None


@dataclass(frozen=True)
class OwnedRuntimeDirectory:
    """Pins one transient runtime and its parent without deleting by pathname."""

    path: Path
    completed_name: str
    descriptor: int
    parent_descriptor: int
    device: int
    inode: int
    parent_device: int
    parent_inode: int


@dataclass(frozen=True)
class GradleLaunchAnchor:
    """Carries the stable launch leader before its direct child is released."""

    handle: forge_server_launch_anchor.LaunchAnchorHandle
    target: macos_guarded_java.OwnedJavaProcess | None
    runtime: OwnedRuntimeDirectory
    launch_watchdog: forge_server_launch_watchdog.LaunchWatchdogHandle | None

    @property
    def process(self) -> subprocess.Popen[bytes]:
        return self.handle.process


@dataclass(frozen=True)
class WrapperJavaGuard:
    """Carries one exact Gradle wrapper identity and its persistent monitor."""

    target: macos_guarded_java.OwnedJavaProcess
    monitor: macos_guarded_java.GuardedJavaMonitor | None
    spawned_monitor_process: subprocess.Popen[bytes] | None
    runtime: OwnedRuntimeDirectory
    launch_watchdog: forge_server_launch_watchdog.LaunchWatchdogHandle | None = None
    launch_anchor: forge_server_launch_anchor.LaunchAnchorHandle | None = None
    anchor_target: macos_guarded_java.OwnedJavaProcess | None = None

    @property
    def runtime_directory(self) -> Path:
        return self.runtime.path


@dataclass(frozen=True)
class GradleTopologyHandoff:
    """Carries the exact wrapper and loader-free JavaExec identities."""

    pid: int
    parent_pid: int
    executable: str
    parent_executable: str
    java_feature: int
    maximum_heap_bytes: int


@dataclass(frozen=True)
class OwnedRunLock:
    """Pins the exact run-lock inode and bytes created by this controller."""

    path: Path
    content: bytes
    descriptor: int
    directory_descriptor: int
    device: int
    inode: int
    directory_device: int
    directory_inode: int


@dataclass(frozen=True)
class OwnedLaunchFile:
    """Pins one controller-created file and its parent directory through launch."""

    path: Path
    initial_content: bytes
    descriptor: int
    directory_descriptor: int
    device: int
    inode: int
    directory_device: int
    directory_inode: int


@dataclass(frozen=True)
class PinnedWatchdogArtifact:
    """Pins one verified terminal watchdog artifact and its exact bytes."""

    name: str
    descriptor: int
    device: int
    inode: int
    content: bytes


@dataclass(frozen=True)
class PinnedWatchdogEvidence:
    """Keeps terminal watchdog and anchor bytes pinned through publication."""

    directory_path: Path
    directory_descriptor: int
    directory_device: int
    directory_inode: int
    artifacts: tuple[PinnedWatchdogArtifact, ...]
    launch_anchor_artifacts: tuple[PinnedWatchdogArtifact, ...] = ()


@dataclass(frozen=True)
class DetachedJavaObservation:
    """Carries an exact rejected Java identity and its observed group/session."""

    target: macos_guarded_java.OwnedJavaProcess | None
    process_group_id: int
    session_id: int


class CleanupUncertainError(E2EError):
    """Retains launch ownership when every possible JVM cannot be proved absent."""


class DetachedJavaLaunchError(E2EError):
    """Reports a bound Java process outside the authorized wrapper topology."""

    def __init__(
        self,
        message: str,
        observation: DetachedJavaObservation,
    ) -> None:
        super().__init__(message)
        self.observation = observation


def load_json_object(path: Path, description: str) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise E2EError(f"Cannot read {description} {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise E2EError(f"The {description} must contain a JSON object: {path}")
    return value


def parse_gradle_properties(path: Path) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exception:
        raise E2EError(f"Cannot read Gradle properties {path}: {exception}") from exception
    properties: dict[str, str] = {}
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        name = name.strip()
        if not separator or not name or name in properties:
            raise E2EError(f"Invalid or duplicate Gradle property in {path}: {raw_line}")
        properties[name] = value.strip()
    return properties


def require_object(container: dict[str, object], name: str) -> dict[str, object]:
    value = container.get(name)
    if not isinstance(value, dict):
        raise E2EError(f"The manifest {name} object is invalid")
    return value


def require_list(container: dict[str, object], name: str) -> list[object]:
    value = container.get(name)
    if not isinstance(value, list):
        raise E2EError(f"The manifest {name} list is invalid")
    return value


def find_unique_row(
    rows: object, key: str, value: str, description: str
) -> dict[str, object]:
    if not isinstance(rows, list):
        raise E2EError(f"The release matrix {description} list is invalid")
    matches = [row for row in rows if isinstance(row, dict) and row.get(key) == value]
    if len(matches) != 1:
        raise E2EError(
            f"Expected one {description} row where {key}={value}, found {len(matches)}"
        )
    return matches[0]


def safe_leaf_name(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str):
        raise E2EError(f"The manifest {field_name} field is invalid")
    path = Path(raw_value)
    if path.parts != (raw_value,) or raw_value in ("", ".", ".."):
        raise E2EError(f"The manifest {field_name} field is unsafe")
    return raw_value


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


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            while chunk := handle.read(1024 * 1024):
                digest.update(chunk)
    except OSError as exception:
        raise E2EError(f"Cannot hash {path}: {exception}") from exception
    return digest.hexdigest()


def validate_manifest_shape(manifest: dict[str, object]) -> None:
    if set(manifest) != {
        "schema",
        "profile",
        "release",
        "launch",
        "evidence",
        "profile_directories",
        "required_mod_ids",
        "forbidden_mod_ids",
    } or type(manifest.get("schema")) is not int or manifest.get("schema") != 1:
        raise E2EError("Unsupported Forge dedicated-server profile schema")

    profile = require_object(manifest, "profile")
    if not exact_json_value(profile, {
        "id": PROFILE_ID,
        "runtime_directory": PROFILE_ID,
        "game_directory": "game",
    }):
        raise E2EError("The dedicated-server profile identity or directory changed")
    for name, value in profile.items():
        safe_leaf_name(value, f"profile.{name}")

    release = require_object(manifest, "release")
    if not exact_json_value(release, {
        "matrix": "release/release-matrix.json",
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
    }):
        raise E2EError("The profile must use the exact Forge 1.20.1 release lane")

    launch = require_object(manifest, "launch")
    if not exact_json_value(launch, {
        "kind": "loom-userdev",
        "task_path": TASK_PATH,
        "scenario": SCENARIO_ID,
        "maximum_memory_mb": SERVER_MAXIMUM_MEMORY_MB,
        "persistent_watchdog": contract_v21.PERSISTENT_WATCHDOG_POLICY,
        "launch_anchor": contract_v21.LAUNCH_ANCHOR_POLICY,
        "pre_acknowledgement": contract_v21.PRE_ACKNOWLEDGEMENT_POLICY,
    }):
        raise E2EError("The dedicated-server launch contract changed")

    evidence = require_object(manifest, "evidence")
    if not exact_json_value(evidence, {
        "directory": "evidence",
        "scenario_directory": SCENARIO_ID,
        "report": "reports/report.json",
        "launcher_result": "reports/launcher-result.json",
        "completion_marker": "reports/done.marker",
        "server_log": "logs/latest.log",
    }):
        raise E2EError("The dedicated-server evidence contract changed")

    directories = require_list(manifest, "profile_directories")
    if directories != ["config", "crash-reports", "evidence", "logs", "mods", "world"]:
        raise E2EError("The dedicated-server directory inventory changed")
    for directory in directories:
        safe_leaf_name(directory, "profile_directories entry")

    if require_list(manifest, "required_mod_ids") != list(REQUIRED_MOD_IDS):
        raise E2EError("The dedicated-server required mod subset changed")
    if require_list(manifest, "forbidden_mod_ids") != list(FORBIDDEN_MOD_IDS):
        raise E2EError("The dedicated-server forbidden mod inventory changed")


def ensure_regular_unlinked_file(path: Path, description: str) -> None:
    if path.is_symlink() or not path.is_file():
        raise E2EError(f"{description} is missing or linked: {path}")


def ensure_no_symlink_components(path: Path, anchor: Path) -> None:
    absolute_anchor = anchor.absolute()
    absolute_path = path.absolute()
    try:
        relative_path = absolute_path.relative_to(absolute_anchor)
    except ValueError as exception:
        raise E2EError(f"Owned path escapes its repository state root: {path}") from exception
    current_path = absolute_anchor
    if current_path.is_symlink():
        raise E2EError(f"Owned path resolves through a symlink: {current_path}")
    for part in relative_path.parts:
        current_path /= part
        if current_path.is_symlink():
            raise E2EError(f"Owned path resolves through a symlink: {current_path}")


def verify_launch_watchdog_source(
    repository_root: Path = REPOSITORY_ROOT,
) -> Path:
    """Requires the exact owner-controlled watchdog bytes before use."""

    root = repository_root.resolve()
    source_path = root / LAUNCH_WATCHDOG_SOURCE_RELATIVE_PATH
    ensure_no_symlink_components(source_path, root)
    ensure_regular_unlinked_file(
        source_path,
        "Persistent launch-watchdog source",
    )
    try:
        metadata = source_path.stat()
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect persistent launch-watchdog source: {exception}"
        ) from exception
    if (
        metadata.st_uid != os.getuid()
        or metadata.st_mode & 0o022
        or metadata.st_size != LAUNCH_WATCHDOG_SOURCE_SIZE
        or sha256_file(source_path) != LAUNCH_WATCHDOG_SOURCE_SHA256
    ):
        raise E2EError(
            "The persistent launch-watchdog source bytes or ownership changed"
        )
    if root == REPOSITORY_ROOT.resolve() and (
        Path(forge_server_launch_watchdog.__file__).resolve() != source_path
    ):
        raise E2EError(
            "The imported persistent launch watchdog is outside this repository"
        )
    return source_path


def verify_launch_anchor_sources(
    repository_root: Path = REPOSITORY_ROOT,
) -> tuple[Path, Path]:
    """Requires both exact owner-controlled launch-anchor sources before use."""

    root = repository_root.resolve()
    specifications = (
        (
            LAUNCH_ANCHOR_MODULE_RELATIVE_PATH,
            LAUNCH_ANCHOR_MODULE_SIZE,
            LAUNCH_ANCHOR_MODULE_SHA256,
            "Launch-anchor controller source",
        ),
        (
            LAUNCH_ANCHOR_JAVA_SOURCE_RELATIVE_PATH,
            LAUNCH_ANCHOR_JAVA_SOURCE_SIZE,
            LAUNCH_ANCHOR_JAVA_SOURCE_SHA256,
            "Launch-anchor Java source",
        ),
    )
    paths: list[Path] = []
    for relative_path, expected_size, expected_sha256, description in specifications:
        source_path = root / relative_path
        ensure_no_symlink_components(source_path, root)
        ensure_regular_unlinked_file(source_path, description)
        try:
            metadata = source_path.stat()
        except OSError as exception:
            raise E2EError(f"Cannot inspect {description}: {exception}") from exception
        if (
            metadata.st_uid != os.getuid()
            or metadata.st_mode & 0o022
            or metadata.st_size != expected_size
            or sha256_file(source_path) != expected_sha256
        ):
            raise E2EError(f"The {description} bytes or ownership changed")
        paths.append(source_path)
    if root == REPOSITORY_ROOT.resolve() and (
        Path(forge_server_launch_anchor.__file__).resolve() != paths[0]
    ):
        raise E2EError("The imported launch-anchor controller is outside this repository")
    return paths[0], paths[1]


def validate_consumed_v18_report(report: dict[str, object]) -> None:
    """Requires the exact failed assertion and shutdown semantics from profile v18."""
    expected_identity = {
        "schema": 11,
        "profile_id": HISTORICAL_V18_PROFILE_ID,
        "scenario": "attrahite-block-registry",
        "status": "failed",
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
    }
    for field_name, expected_value in expected_identity.items():
        if not exact_json_value(report.get(field_name), expected_value):
            raise E2EError(
                f"The consumed v18 report {field_name} value changed"
            )
    if not exact_json_value(report.get("lifecycle"), list(HISTORICAL_V18_LIFECYCLE)):
        raise E2EError("The consumed v18 report clean-shutdown lifecycle changed")

    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != 310:
        raise E2EError("The consumed v18 report assertion inventory changed")
    for assertion in assertions:
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise E2EError("The consumed v18 report contains a malformed assertion")
        if not isinstance(assertion.get("name"), str):
            raise E2EError("The consumed v18 report assertion name is invalid")
        if type(assertion.get("passed")) is not bool:
            raise E2EError("The consumed v18 report assertion result is invalid")
        if not isinstance(assertion.get("expected"), str) or not isinstance(
            assertion.get("actual"),
            str,
        ):
            raise E2EError("The consumed v18 report assertion evidence is invalid")

    passed_assertions = [
        assertion for assertion in assertions if assertion["passed"] is True
    ]
    failed_assertions = [
        assertion for assertion in assertions if assertion["passed"] is False
    ]
    if len(passed_assertions) != 305 or not exact_json_value(
        failed_assertions,
        list(HISTORICAL_V18_FAILURES),
    ):
        raise E2EError(
            "The consumed v18 report must retain exactly 305 passes and five "
            "Forest Lantern BREAK-derived failures"
        )


def validate_consumed_v18_clean_shutdown_log(content: bytes) -> None:
    """Requires the failed v18 probe to have completed a normal server shutdown."""
    text = content.decode("utf-8", errors="replace")
    phases = re.findall(r"\[EtherologyServerProbe\] ([a-z_]+)", text)
    expected_phases = [
        "tags_updated_initial",
        "registry_foundation_checked",
        "server_started",
        "reload_requested",
        "tags_updated_reload",
        "reload_command_returned",
        "stop_requested",
        "server_stopping",
        "server_stopped",
        "report_published",
        "loom_userdev_exit_scheduled",
    ]
    if phases != expected_phases:
        raise E2EError("The consumed v18 server-log lifecycle changed")
    tokens = (
        "[EtherologyServerProbe] tags_updated_initial",
        "[EtherologyServerProbe] registry_foundation_checked",
        "[EtherologyServerProbe] server_started",
        "[EtherologyServerProbe] reload_requested",
        "[EtherologyServerProbe] tags_updated_reload",
        "[EtherologyServerProbe] reload_command_returned",
        "[EtherologyServerProbe] stop_requested",
        "[EtherologyServerProbe] server_stopping",
        "Stopping server",
        "Saving worlds",
        "[EtherologyServerProbe] server_stopped",
        "[EtherologyServerProbe] report_published",
        "[EtherologyServerProbe] loom_userdev_exit_scheduled status=1 "
        "server_thread_join_timeout_ms=30000",
    )
    positions: list[int] = []
    for token in tokens:
        if text.count(token) != 1:
            raise E2EError(
                f"The consumed v18 server-log token count changed: {token}"
            )
        positions.append(text.index(token))
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise E2EError("The consumed v18 server-log tokens are out of order")
    if "All dimensions are saved" not in text:
        raise E2EError(
            "The consumed v18 server log lacks its completed world save"
        )


def validate_consumed_v18_history(repository_root: Path) -> None:
    """Accepts no v18 state or its one exact, complete failed native-run record."""
    attempt_path = repository_root / HISTORICAL_V18_ATTEMPT_RELATIVE_PATH
    runtime_path = repository_root / HISTORICAL_V18_RUNTIME_RELATIVE_PATH
    archive_path = repository_root / HISTORICAL_V18_ARCHIVE_RELATIVE_PATH
    history_present = any(
        path.exists() or path.is_symlink()
        for path in (attempt_path, runtime_path, archive_path)
    )
    if not history_present:
        return

    if not runtime_path.is_dir() or runtime_path.is_symlink():
        raise E2EError("The consumed v18 runtime is missing or linked")
    ensure_no_symlink_components(runtime_path, repository_root)
    for relative_path in HISTORICAL_V18_ABSENT_RELATIVE_PATHS:
        path = repository_root / relative_path
        if path.exists() or path.is_symlink():
            raise E2EError(
                f"The consumed v18 failure must leave this artifact absent: {path}"
            )
    controller_logs = list(runtime_path.glob(".forge-server-gradle.*.log"))
    if controller_logs:
        raise E2EError(
            "The consumed v18 temporary controller output must remain absent"
        )

    for relative_path, (expected_size, expected_sha256) in (
        HISTORICAL_V18_FILE_RECORDS.items()
    ):
        path = repository_root / relative_path
        ensure_regular_unlinked_file(path, "Consumed v18 history artifact")
        if path.stat().st_size != expected_size or sha256_file(path) != expected_sha256:
            raise E2EError(
                f"The consumed v18 history artifact bytes changed: {path}"
            )

    report_path = (
        runtime_path
        / "evidence/attrahite-block-registry/reports/report.json"
    )
    validate_consumed_v18_report(
        load_json_object(report_path, "consumed v18 probe report")
    )
    validate_consumed_v18_clean_shutdown_log(
        (runtime_path / "game/logs/latest.log").read_bytes()
    )


def load_configuration(
    manifest_path: Path = MANIFEST_PATH,
    repository_root: Path = REPOSITORY_ROOT,
) -> ResolvedConfiguration:
    root = repository_root.resolve()
    validate_consumed_v18_history(root)
    expected_manifest_path = root / PROFILE_MANIFEST_RELATIVE_PATH
    if manifest_path.absolute() != expected_manifest_path:
        raise E2EError(
            "The dedicated-server profile must be loaded from its tracked repository path"
        )
    ensure_regular_unlinked_file(expected_manifest_path, "Dedicated-server profile")
    manifest = load_json_object(expected_manifest_path, "dedicated-server profile")
    validate_manifest_shape(manifest)
    verify_launch_watchdog_source(root)
    verify_launch_anchor_sources(root)
    if (
        expected_manifest_path.stat().st_size != contract_v21.PROFILE_MANIFEST_SIZE
        or sha256_file(expected_manifest_path)
        != contract_v21.PROFILE_MANIFEST_SHA256
    ):
        raise E2EError(
            "The dedicated-server profile bytes differ from the immutable v21 contract"
        )
    properties = parse_gradle_properties(root / "gradle.properties")
    if properties.get("minecraft_version_1_20_1") != "1.20.1":
        raise E2EError("The Minecraft 1.20.1 Gradle property changed")
    if properties.get("forge_version_1_20_1") != "1.20.1-47.4.9":
        raise E2EError("The Forge 47.4.9 Gradle property changed")

    matrix_path = root / "release/release-matrix.json"
    ensure_regular_unlinked_file(matrix_path, "Release matrix")
    matrix = load_json_object(matrix_path, "release matrix")
    if matrix.get("schema_version") != 1:
        raise E2EError("Unsupported release matrix schema")
    artifact_lane = find_unique_row(
        matrix.get("artifacts"), "artifact_node", "forge-1.20.1", "artifact"
    )
    runtime_lane = find_unique_row(
        matrix.get("runtimes"), "artifact_node", "forge-1.20.1", "runtime"
    )
    if (
        artifact_lane.get("artifact_version") != "1.20.1"
        or artifact_lane.get("loader") != "forge"
        or artifact_lane.get("java") != 17
        or runtime_lane.get("runtime_version") != "1.20.1"
        or runtime_lane.get("loader") != "forge"
        or runtime_lane.get("loader_version") != "1.20.1-47.4.9"
        or runtime_lane.get("port") != 0
        or runtime_lane.get("java") != 17
    ):
        raise E2EError("The release matrix Forge 1.20.1 server lane changed")
    project = matrix.get("project")
    if not isinstance(project, dict) or project.get("mod_id") != "etherology":
        raise E2EError("The release matrix production mod identity changed")
    return ResolvedConfiguration(
        manifest=manifest,
        properties=properties,
        artifact_lane=artifact_lane,
        runtime_lane=runtime_lane,
        repository_root=root,
        profile_manifest_path=expected_manifest_path,
    )


def profile_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "profile")


def evidence_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "evidence")


def runtime_root(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    return state_root / "runtimes" / str(profile_spec(configuration)["runtime_directory"])


def game_directory(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    return (root or runtime_root(configuration)) / str(
        profile_spec(configuration)["game_directory"]
    )


def evidence_root(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    evidence = evidence_spec(configuration)
    return (
        (root or runtime_root(configuration))
        / str(evidence["directory"])
        / str(evidence["scenario_directory"])
    )


def evidence_path(
    configuration: ResolvedConfiguration,
    field_name: str,
    root: Path | None = None,
) -> Path:
    raw_path = str(evidence_spec(configuration)[field_name])
    relative_path = Path(raw_path)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise E2EError(f"The evidence {field_name} path is unsafe")
    return evidence_root(configuration, root) / relative_path


def profile_marker_path(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    return (root or runtime_root(configuration)) / PROFILE_MARKER_NAME


def run_lock_path(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> Path:
    del configuration
    return state_root / GLOBAL_NATIVE_RUN_LOCK_NAME


def run_attempt_path(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> Path:
    return state_root / f"{profile_spec(configuration)['id']}-run.attempted"


def sealed_archive_path(configuration: ResolvedConfiguration) -> Path:
    """Resolves the immutable archive that permanently consumes this profile."""
    profile_version = PROFILE_ID.rpartition("-")[2]
    if re.fullmatch(r"v[1-9][0-9]*", profile_version) is None:
        raise E2EError("The dedicated-server profile has no safe archive version")
    return (
        configuration.repository_root
        / "docs/evidence/forge-1.20.1"
        / f"{SCENARIO_ID}-server-{profile_version}"
    )


def require_unsealed_profile(configuration: ResolvedConfiguration) -> None:
    """Rejects every lifecycle action after this profile has frozen evidence."""
    archive = sealed_archive_path(configuration)
    ensure_no_symlink_components(archive, configuration.repository_root)
    if archive.exists() or archive.is_symlink():
        raise E2EError(
            "The dedicated-server profile already has sealed evidence and is consumed: "
            f"{archive}"
        )


def require_unattempted_profile(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> None:
    """Permanently rejects a profile after its first launch invocation."""
    attempt = run_attempt_path(configuration, state_root)
    ensure_no_symlink_components(attempt, state_root.parent)
    if attempt.exists() or attempt.is_symlink():
        raise E2EError(
            "The dedicated-server profile already has a launch attempt and is consumed: "
            f"{attempt}"
        )


def profile_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    manifest_path = configuration.profile_manifest_path
    ensure_regular_unlinked_file(manifest_path, "Dedicated-server profile")
    return {
        "schema": 1,
        "profile_id": PROFILE_ID,
        "managed_by": MANAGED_BY,
        "profile_manifest": {
            "relative_path": PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            "size": manifest_path.stat().st_size,
            "sha256": sha256_file(manifest_path),
        },
        "isolation": {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "release": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
        },
        "launch": {
            "task_path": TASK_PATH,
            "scenario": SCENARIO_ID,
        },
    }


def ensure_owned_state_roots(state_root: Path = STATE_ROOT) -> None:
    ensure_no_symlink_components(state_root, state_root.parent)
    ensure_no_symlink_components(state_root / "runtimes", state_root.parent)
    for path, description in (
        (state_root, "Forge server E2E state root"),
        (state_root / "runtimes", "Forge server E2E runtimes root"),
    ):
        if path.exists() and not path.is_dir():
            raise E2EError(f"{description} must be a directory: {path}")
        if path.is_dir():
            try:
                metadata = path.stat()
            except OSError as exception:
                raise E2EError(f"Cannot inspect {description}: {exception}") from exception
            if metadata.st_uid != os.getuid() or metadata.st_mode & 0o022:
                raise E2EError(
                    f"{description} is not exclusively writable by its owner: {path}"
                )


def require_no_retained_launch_runtime(state_root: Path) -> None:
    """Blocks another launch while any prior transient runtime remains."""

    if not state_root.is_dir() or state_root.is_symlink():
        raise E2EError(f"The Forge server E2E state root is unsafe: {state_root}")
    prefixes = (
        GRADLE_TOPOLOGY_RUNTIME_PREFIX,
        GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
    )
    retained = sorted(
        entry.name
        for entry in state_root.iterdir()
        if entry.name.startswith(prefixes)
    )
    if retained:
        raise E2EError(
            "A retained Forge server launch runtime blocks another attempt: "
            + ", ".join(retained)
        )


def verify_active_launch_runtime_inventory(
    state_root: Path,
    owners: tuple[OwnedRuntimeDirectory, ...],
) -> None:
    """Requires the active transient inventory to contain only pinned owners."""

    for owner in owners:
        verify_owned_runtime_directory(owner)
    expected_names = {owner.path.name for owner in owners}
    prefixes = (
        GRADLE_TOPOLOGY_RUNTIME_PREFIX,
        GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
    )
    actual_names = {
        entry.name
        for entry in state_root.iterdir()
        if entry.name.startswith(prefixes)
    }
    if actual_names != expected_names:
        raise CleanupUncertainError(
            "The active Forge server launch-runtime inventory changed: "
            f"expected={sorted(expected_names)}, actual={sorted(actual_names)}"
        )


def create_owned_runtime_directory(
    state_root: Path,
    active_prefix: str,
    completed_prefix: str,
) -> OwnedRuntimeDirectory:
    """Creates and pins one owner-private runtime through directory descriptors."""

    if not active_prefix.startswith(".forge-server-") or not completed_prefix.startswith(
        ".forge-server-completed-"
    ):
        raise E2EError("The transient runtime prefix is not authorized")
    ensure_no_symlink_components(state_root, state_root.parent)
    directory_flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        directory_flags |= os.O_DIRECTORY
    if hasattr(os, "O_CLOEXEC"):
        directory_flags |= os.O_CLOEXEC
    parent_descriptor = os.open(state_root, directory_flags)
    descriptor = -1
    created = False
    suffix = secrets.token_hex(16)
    name = f"{active_prefix}{suffix}"
    completed_name = f"{completed_prefix}{suffix}"
    path = state_root / name
    try:
        os.mkdir(name, mode=0o700, dir_fd=parent_descriptor)
        created = True
        open_flags = directory_flags
        if hasattr(os, "O_NOFOLLOW"):
            open_flags |= os.O_NOFOLLOW
        descriptor = os.open(
            name,
            open_flags,
            dir_fd=parent_descriptor,
        )
        metadata = os.fstat(descriptor)
        parent_metadata = os.fstat(parent_descriptor)
        current_parent_metadata = os.lstat(state_root)
        path_metadata = os.stat(
            name,
            dir_fd=parent_descriptor,
            follow_symlinks=False,
        )
        if (
            not stat.S_ISDIR(metadata.st_mode)
            or stat.S_IMODE(metadata.st_mode) != 0o700
            or metadata.st_uid != os.getuid()
            or metadata.st_dev != path_metadata.st_dev
            or metadata.st_ino != path_metadata.st_ino
            or not stat.S_ISDIR(parent_metadata.st_mode)
            or parent_metadata.st_uid != os.getuid()
            or parent_metadata.st_mode & 0o022
            or parent_metadata.st_dev != current_parent_metadata.st_dev
            or parent_metadata.st_ino != current_parent_metadata.st_ino
        ):
            raise CleanupUncertainError(
                f"The transient runtime has an unsafe identity: {path}"
            )
        os.fsync(parent_descriptor)
        return OwnedRuntimeDirectory(
            path=path,
            completed_name=completed_name,
            descriptor=descriptor,
            parent_descriptor=parent_descriptor,
            device=metadata.st_dev,
            inode=metadata.st_ino,
            parent_device=parent_metadata.st_dev,
            parent_inode=parent_metadata.st_ino,
        )
    except BaseException as exception:
        if descriptor >= 0:
            os.close(descriptor)
        os.close(parent_descriptor)
        if created and not isinstance(exception, CleanupUncertainError):
            raise CleanupUncertainError(
                f"The transient runtime was created but could not be pinned: {path}"
            ) from exception
        raise


def verify_owned_runtime_directory(runtime: OwnedRuntimeDirectory) -> None:
    """Requires the active pathname to still name the held runtime inode."""

    try:
        metadata = os.fstat(runtime.descriptor)
        parent_metadata = os.fstat(runtime.parent_descriptor)
        current_parent_metadata = os.lstat(runtime.path.parent)
        path_metadata = os.stat(
            runtime.path.name,
            dir_fd=runtime.parent_descriptor,
            follow_symlinks=False,
        )
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot revalidate the transient runtime: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(metadata.st_mode)
        or stat.S_IMODE(metadata.st_mode) != 0o700
        or metadata.st_uid != os.getuid()
        or metadata.st_dev != runtime.device
        or metadata.st_ino != runtime.inode
        or path_metadata.st_dev != runtime.device
        or path_metadata.st_ino != runtime.inode
        or not stat.S_ISDIR(parent_metadata.st_mode)
        or parent_metadata.st_uid != os.getuid()
        or parent_metadata.st_mode & 0o022
        or parent_metadata.st_dev != runtime.parent_device
        or parent_metadata.st_ino != runtime.parent_inode
        or current_parent_metadata.st_dev != runtime.parent_device
        or current_parent_metadata.st_ino != runtime.parent_inode
    ):
        raise CleanupUncertainError(
            f"The transient runtime identity changed while owned: {runtime.path}"
        )


def retire_owned_runtime_directory(runtime: OwnedRuntimeDirectory) -> Path:
    """Atomically preserves an exact completed runtime without recursive deletion."""

    verify_owned_runtime_directory(runtime)
    try:
        os.stat(
            runtime.completed_name,
            dir_fd=runtime.parent_descriptor,
            follow_symlinks=False,
        )
    except FileNotFoundError:
        pass
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot inspect the completed runtime destination: {exception}"
        ) from exception
    else:
        raise CleanupUncertainError(
            "The completed transient runtime destination already exists"
        )
    try:
        os.rename(
            runtime.path.name,
            runtime.completed_name,
            src_dir_fd=runtime.parent_descriptor,
            dst_dir_fd=runtime.parent_descriptor,
        )
        retired_metadata = os.stat(
            runtime.completed_name,
            dir_fd=runtime.parent_descriptor,
            follow_symlinks=False,
        )
        os.fsync(runtime.parent_descriptor)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot preserve the completed transient runtime: {exception}"
        ) from exception
    if (
        retired_metadata.st_dev != runtime.device
        or retired_metadata.st_ino != runtime.inode
    ):
        raise CleanupUncertainError(
            "A foreign transient runtime was moved during retirement"
        )
    return runtime.path.parent / runtime.completed_name


def close_owned_runtime_directory(runtime: OwnedRuntimeDirectory) -> None:
    """Closes one runtime and its pinned-parent descriptors."""

    os.close(runtime.descriptor)
    os.close(runtime.parent_descriptor)


def write_bytes_exclusive(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(content)
        handle.flush()
        os.fsync(handle.fileno())
    directory_descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_descriptor)
    finally:
        os.close(directory_descriptor)


def acquire_run_lock(
    configuration: ResolvedConfiguration,
    state_root: Path,
    run_token: str,
) -> OwnedRunLock:
    """Creates and keeps an open descriptor for this controller's exact lock."""

    if re.fullmatch(r"[0-9a-f]{64}", run_token) is None:
        raise E2EError("The dedicated-server run-lock token is malformed")
    path = run_lock_path(configuration, state_root)
    ensure_no_symlink_components(path, state_root)
    content = (
        f"profile_id={profile_spec(configuration)['id']}\n"
        f"scenario={SCENARIO_ID}\n"
        f"pid={os.getpid()}\n"
        f"token={run_token}\n"
    ).encode("ascii")
    directory_flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        directory_flags |= os.O_DIRECTORY
    if hasattr(os, "O_CLOEXEC"):
        directory_flags |= os.O_CLOEXEC
    try:
        directory_descriptor = os.open(state_root, directory_flags)
    except OSError as exception:
        raise E2EError(
            f"Cannot pin the dedicated-server state directory: {exception}"
        ) from exception
    descriptor = -1
    created = False
    try:
        open_flags = os.O_RDWR | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_CLOEXEC"):
            open_flags |= os.O_CLOEXEC
        if hasattr(os, "O_NOFOLLOW"):
            open_flags |= os.O_NOFOLLOW
        descriptor = os.open(
            path.name,
            open_flags,
            0o600,
            dir_fd=directory_descriptor,
        )
        created = True
    except FileExistsError as exception:
        os.close(directory_descriptor)
        raise E2EError(f"A dedicated-server probe run is already owned: {path}") from exception
    except OSError as exception:
        os.close(directory_descriptor)
        raise E2EError(
            f"Cannot acquire dedicated-server probe ownership: {exception}"
        ) from exception
    try:
        remaining = memoryview(content)
        while remaining:
            written = os.write(descriptor, remaining)
            if written <= 0:
                raise OSError("the run-lock write made no progress")
            remaining = remaining[written:]
        os.fsync(descriptor)
        metadata = os.fstat(descriptor)
        directory_metadata = os.fstat(directory_descriptor)
        current_directory_metadata = os.lstat(state_root)
        path_metadata = os.stat(
            path.name,
            dir_fd=directory_descriptor,
            follow_symlinks=False,
        )
        if (
            not stat.S_ISREG(metadata.st_mode)
            or stat.S_IMODE(metadata.st_mode) != 0o600
            or metadata.st_nlink != 1
            or metadata.st_uid != os.getuid()
            or metadata.st_dev != path_metadata.st_dev
            or metadata.st_ino != path_metadata.st_ino
            or metadata.st_size != len(content)
            or not stat.S_ISDIR(directory_metadata.st_mode)
            or directory_metadata.st_uid != os.getuid()
            or directory_metadata.st_mode & 0o022
            or directory_metadata.st_dev != current_directory_metadata.st_dev
            or directory_metadata.st_ino != current_directory_metadata.st_ino
        ):
            raise CleanupUncertainError(
                f"The newly created run lock has an unsafe identity: {path}"
            )
        os.fsync(directory_descriptor)
        return OwnedRunLock(
            path=path,
            content=content,
            descriptor=descriptor,
            directory_descriptor=directory_descriptor,
            device=metadata.st_dev,
            inode=metadata.st_ino,
            directory_device=directory_metadata.st_dev,
            directory_inode=directory_metadata.st_ino,
        )
    except BaseException as exception:
        if descriptor >= 0:
            os.close(descriptor)
        os.close(directory_descriptor)
        if created and not isinstance(exception, CleanupUncertainError):
            raise CleanupUncertainError(
                f"The run lock was created but could not be pinned: {path}"
            ) from exception
        raise


def verify_owned_run_lock(lock: OwnedRunLock) -> None:
    """Revalidates the held descriptor, path inode, mode, owner, and exact bytes."""

    try:
        descriptor_metadata = os.fstat(lock.descriptor)
        directory_metadata = os.fstat(lock.directory_descriptor)
        current_directory_metadata = os.lstat(lock.path.parent)
        path_metadata = os.stat(
            lock.path.name,
            dir_fd=lock.directory_descriptor,
            follow_symlinks=False,
        )
        content = os.pread(lock.descriptor, len(lock.content) + 1, 0)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot revalidate the owned run lock: {exception}"
        ) from exception
    if (
        not stat.S_ISREG(descriptor_metadata.st_mode)
        or stat.S_IMODE(descriptor_metadata.st_mode) != 0o600
        or descriptor_metadata.st_nlink != 1
        or descriptor_metadata.st_uid != os.getuid()
        or descriptor_metadata.st_dev != lock.device
        or descriptor_metadata.st_ino != lock.inode
        or path_metadata.st_dev != lock.device
        or path_metadata.st_ino != lock.inode
        or path_metadata.st_nlink != 1
        or stat.S_IMODE(path_metadata.st_mode) != 0o600
        or not stat.S_ISDIR(directory_metadata.st_mode)
        or directory_metadata.st_uid != os.getuid()
        or directory_metadata.st_mode & 0o022
        or directory_metadata.st_dev != lock.directory_device
        or directory_metadata.st_ino != lock.directory_inode
        or current_directory_metadata.st_dev != lock.directory_device
        or current_directory_metadata.st_ino != lock.directory_inode
        or content != lock.content
    ):
        raise CleanupUncertainError(
            f"The dedicated-server run lock changed while owned: {lock.path}"
        )


def release_owned_run_lock(lock: OwnedRunLock) -> None:
    """Unlinks only the exact lock inode still held by this controller."""

    verify_owned_run_lock(lock)
    try:
        os.unlink(lock.path.name, dir_fd=lock.directory_descriptor)
        os.fsync(lock.directory_descriptor)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot release the exact owned run lock: {exception}"
        ) from exception


def close_owned_run_lock(lock: OwnedRunLock) -> None:
    """Closes the exact lock and pinned-directory descriptors once."""

    os.close(lock.descriptor)
    os.close(lock.directory_descriptor)


def create_owned_launch_file(path: Path, content: bytes) -> OwnedLaunchFile:
    """Creates and pins one owner-only direct child of an owner-controlled directory."""

    if not path.is_absolute() or not isinstance(content, bytes):
        raise E2EError("The owned launch file request is invalid")
    ensure_no_symlink_components(path.parent, path.parent.parent)
    directory_flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        directory_flags |= os.O_DIRECTORY
    if hasattr(os, "O_CLOEXEC"):
        directory_flags |= os.O_CLOEXEC
    try:
        directory_descriptor = os.open(path.parent, directory_flags)
    except OSError as exception:
        raise E2EError(
            f"Cannot pin the owned launch-file directory: {exception}"
        ) from exception
    descriptor = -1
    created = False
    try:
        open_flags = os.O_RDWR | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_CLOEXEC"):
            open_flags |= os.O_CLOEXEC
        if hasattr(os, "O_NOFOLLOW"):
            open_flags |= os.O_NOFOLLOW
        descriptor = os.open(
            path.name,
            open_flags,
            0o600,
            dir_fd=directory_descriptor,
        )
        created = True
        remaining = memoryview(content)
        while remaining:
            written = os.write(descriptor, remaining)
            if written <= 0:
                raise OSError("the owned launch-file write made no progress")
            remaining = remaining[written:]
        os.fsync(descriptor)
        os.fsync(directory_descriptor)
        metadata = os.fstat(descriptor)
        directory_metadata = os.fstat(directory_descriptor)
        current_directory_metadata = os.lstat(path.parent)
        path_metadata = os.stat(
            path.name,
            dir_fd=directory_descriptor,
            follow_symlinks=False,
        )
        if (
            not stat.S_ISREG(metadata.st_mode)
            or stat.S_IMODE(metadata.st_mode) != 0o600
            or metadata.st_nlink != 1
            or metadata.st_uid != os.getuid()
            or metadata.st_dev != path_metadata.st_dev
            or metadata.st_ino != path_metadata.st_ino
            or metadata.st_size != len(content)
            or not stat.S_ISDIR(directory_metadata.st_mode)
            or directory_metadata.st_uid != os.getuid()
            or directory_metadata.st_mode & 0o022
            or directory_metadata.st_dev != current_directory_metadata.st_dev
            or directory_metadata.st_ino != current_directory_metadata.st_ino
        ):
            raise CleanupUncertainError(
                f"The newly created launch file has an unsafe identity: {path}"
            )
        return OwnedLaunchFile(
            path=path,
            initial_content=content,
            descriptor=descriptor,
            directory_descriptor=directory_descriptor,
            device=metadata.st_dev,
            inode=metadata.st_ino,
            directory_device=directory_metadata.st_dev,
            directory_inode=directory_metadata.st_ino,
        )
    except BaseException as exception:
        if descriptor >= 0:
            os.close(descriptor)
        os.close(directory_descriptor)
        if created and not isinstance(exception, CleanupUncertainError):
            raise CleanupUncertainError(
                f"The launch file was created but could not be pinned: {path}"
            ) from exception
        raise


def verify_owned_launch_file(
    owned_file: OwnedLaunchFile,
    *,
    require_initial_content: bool = True,
) -> None:
    """Requires a pathname to still name the held controller-created inode."""

    try:
        metadata = os.fstat(owned_file.descriptor)
        directory_metadata = os.fstat(owned_file.directory_descriptor)
        current_directory_metadata = os.lstat(owned_file.path.parent)
        path_metadata = os.stat(
            owned_file.path.name,
            dir_fd=owned_file.directory_descriptor,
            follow_symlinks=False,
        )
        content = (
            os.pread(
                owned_file.descriptor,
                len(owned_file.initial_content) + 1,
                0,
            )
            if require_initial_content
            else None
        )
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot revalidate the owned launch file: {exception}"
        ) from exception
    if (
        not stat.S_ISREG(metadata.st_mode)
        or stat.S_IMODE(metadata.st_mode) != 0o600
        or metadata.st_nlink != 1
        or metadata.st_uid != os.getuid()
        or metadata.st_dev != owned_file.device
        or metadata.st_ino != owned_file.inode
        or path_metadata.st_dev != owned_file.device
        or path_metadata.st_ino != owned_file.inode
        or path_metadata.st_nlink != 1
        or stat.S_IMODE(path_metadata.st_mode) != 0o600
        or not stat.S_ISDIR(directory_metadata.st_mode)
        or directory_metadata.st_uid != os.getuid()
        or directory_metadata.st_mode & 0o022
        or directory_metadata.st_dev != owned_file.directory_device
        or directory_metadata.st_ino != owned_file.directory_inode
        or current_directory_metadata.st_dev != owned_file.directory_device
        or current_directory_metadata.st_ino != owned_file.directory_inode
        or (require_initial_content and content != owned_file.initial_content)
    ):
        raise CleanupUncertainError(
            f"The owned launch file changed while held: {owned_file.path}"
        )


def unlink_owned_launch_file(owned_file: OwnedLaunchFile) -> None:
    """Unlinks only the exact still-held launch-file inode."""

    verify_owned_launch_file(owned_file, require_initial_content=False)
    try:
        os.unlink(owned_file.path.name, dir_fd=owned_file.directory_descriptor)
        os.fsync(owned_file.directory_descriptor)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot unlink the exact owned launch file: {exception}"
        ) from exception


def close_owned_launch_file(owned_file: OwnedLaunchFile) -> None:
    """Closes one pinned launch file and its pinned parent directory."""

    os.close(owned_file.descriptor)
    os.close(owned_file.directory_descriptor)


def _watchdog_artifact_size_limit(name: str) -> int:
    if name == forge_server_launch_watchdog.READINESS_FILE_NAME:
        return forge_server_launch_watchdog.MAXIMUM_READINESS_SIZE_BYTES
    if name == forge_server_launch_watchdog.TELEMETRY_FILE_NAME:
        return macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES
    raise CleanupUncertainError(f"Unexpected watchdog artifact name: {name}")


def verify_launch_watchdog_runtime_identity(
    handle: forge_server_launch_watchdog.LaunchWatchdogHandle,
    expected_directory_descriptor: int,
) -> None:
    """Binds a spawned watchdog to the controller's pre-owned runtime inode."""

    if (
        type(handle.runtime_directory_descriptor) is not int
        or handle.runtime_directory_descriptor < 3
        or type(expected_directory_descriptor) is not int
        or expected_directory_descriptor < 3
    ):
        raise CleanupUncertainError(
            "The launch watchdog runtime descriptors are invalid"
        )
    try:
        watchdog_directory = os.fstat(handle.runtime_directory_descriptor)
        expected_directory = os.fstat(expected_directory_descriptor)
        current_directory = os.lstat(handle.readiness_path.parent)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot inspect the launch watchdog runtime identity: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(watchdog_directory.st_mode)
        or not stat.S_ISDIR(expected_directory.st_mode)
        or not stat.S_ISDIR(current_directory.st_mode)
        or stat.S_ISLNK(current_directory.st_mode)
        or watchdog_directory.st_uid != os.getuid()
        or expected_directory.st_uid != os.getuid()
        or current_directory.st_uid != os.getuid()
        or stat.S_IMODE(watchdog_directory.st_mode) != 0o700
        or stat.S_IMODE(expected_directory.st_mode) != 0o700
        or stat.S_IMODE(current_directory.st_mode) != 0o700
        or (watchdog_directory.st_dev, watchdog_directory.st_ino)
        != (expected_directory.st_dev, expected_directory.st_ino)
        or (watchdog_directory.st_dev, watchdog_directory.st_ino)
        != (current_directory.st_dev, current_directory.st_ino)
    ):
        raise CleanupUncertainError(
            "The launch watchdog did not pin the owned launch runtime"
        )


def verify_launch_anchor_runtime_identity(
    handle: forge_server_launch_anchor.LaunchAnchorHandle,
    expected_directory_descriptor: int,
) -> None:
    """Binds one Java broker to the controller's pre-owned runtime inode."""

    if (
        type(handle.runtime_directory_descriptor) is not int
        or handle.runtime_directory_descriptor < 3
        or type(expected_directory_descriptor) is not int
        or expected_directory_descriptor < 3
    ):
        raise CleanupUncertainError("The launch-anchor runtime descriptors are invalid")
    try:
        anchor_directory = os.fstat(handle.runtime_directory_descriptor)
        expected_directory = os.fstat(expected_directory_descriptor)
        current_directory = os.lstat(handle.runtime_directory)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot inspect the launch-anchor runtime identity: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(anchor_directory.st_mode)
        or not stat.S_ISDIR(expected_directory.st_mode)
        or not stat.S_ISDIR(current_directory.st_mode)
        or stat.S_ISLNK(current_directory.st_mode)
        or anchor_directory.st_uid != os.getuid()
        or expected_directory.st_uid != os.getuid()
        or current_directory.st_uid != os.getuid()
        or stat.S_IMODE(anchor_directory.st_mode) != 0o700
        or stat.S_IMODE(expected_directory.st_mode) != 0o700
        or stat.S_IMODE(current_directory.st_mode) != 0o700
        or (anchor_directory.st_dev, anchor_directory.st_ino)
        != (expected_directory.st_dev, expected_directory.st_ino)
        or (anchor_directory.st_dev, anchor_directory.st_ino)
        != (current_directory.st_dev, current_directory.st_ino)
    ):
        raise CleanupUncertainError(
            "The launch anchor did not pin the owned launch runtime"
        )


def pin_terminal_watchdog_evidence(
    handle: forge_server_launch_watchdog.LaunchWatchdogHandle,
    expected_directory_descriptor: int,
    launch_anchor: forge_server_launch_anchor.LaunchAnchorHandle | None = None,
) -> PinnedWatchdogEvidence:
    """Pins exact terminal supervision artifacts in the owned runtime inode."""

    verify_launch_watchdog_runtime_identity(
        handle,
        expected_directory_descriptor,
    )
    verified_terminal_contents = handle.verified_terminal_artifact_contents
    expected_artifact_names = {
        forge_server_launch_watchdog.READINESS_FILE_NAME,
        forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
    }
    if (
        not isinstance(verified_terminal_contents, dict)
        or set(verified_terminal_contents) != expected_artifact_names
        or any(
            not isinstance(content, bytes) or not content
            for content in verified_terminal_contents.values()
        )
    ):
        raise CleanupUncertainError(
            "Terminal watchdog bytes were not semantically verified before pinning"
        )
    if launch_anchor is not None:
        try:
            verify_launch_anchor_runtime_identity(
                launch_anchor,
                expected_directory_descriptor,
            )
            launch_anchor.verify_pinned_artifacts()
        except (E2EError, forge_server_launch_anchor.LaunchAnchorError) as exception:
            raise CleanupUncertainError(
                f"Cannot verify terminal launch-anchor evidence: {exception}"
            ) from exception
        if set(launch_anchor.pinned_artifacts) != set(
            forge_server_launch_anchor.ARTIFACT_FILE_NAMES
        ):
            raise CleanupUncertainError(
                "The terminal launch-anchor artifact inventory is incomplete"
            )
    source_directory_descriptor = handle.runtime_directory_descriptor
    directory_path = handle.readiness_path.parent
    try:
        source_directory = os.fstat(source_directory_descriptor)
        expected_directory = os.fstat(expected_directory_descriptor)
        current_directory = os.lstat(directory_path)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot bind terminal watchdog evidence to its runtime: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(source_directory.st_mode)
        or not stat.S_ISDIR(expected_directory.st_mode)
        or not stat.S_ISDIR(current_directory.st_mode)
        or stat.S_ISLNK(current_directory.st_mode)
        or source_directory.st_uid != os.getuid()
        or expected_directory.st_uid != os.getuid()
        or current_directory.st_uid != os.getuid()
        or stat.S_IMODE(source_directory.st_mode) != 0o700
        or stat.S_IMODE(expected_directory.st_mode) != 0o700
        or stat.S_IMODE(current_directory.st_mode) != 0o700
        or (source_directory.st_dev, source_directory.st_ino)
        != (expected_directory.st_dev, expected_directory.st_ino)
        or (source_directory.st_dev, source_directory.st_ino)
        != (current_directory.st_dev, current_directory.st_ino)
    ):
        raise CleanupUncertainError(
            "The launch watchdog runtime differs from the owned launch runtime"
        )

    pinned_directory_descriptor = -1
    artifact_descriptors: list[int] = []
    try:
        pinned_directory_descriptor = os.dup(source_directory_descriptor)
        pinned_directory = os.fstat(pinned_directory_descriptor)
        artifacts: list[PinnedWatchdogArtifact] = []
        launch_anchor_artifacts: list[PinnedWatchdogArtifact] = []
        for name in (
            forge_server_launch_watchdog.READINESS_FILE_NAME,
            forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
        ):
            flags = os.O_RDONLY
            flags |= getattr(os, "O_CLOEXEC", 0)
            flags |= getattr(os, "O_NOFOLLOW", 0)
            descriptor = os.open(
                name,
                flags,
                dir_fd=pinned_directory_descriptor,
            )
            artifact_descriptors.append(descriptor)
            metadata = os.fstat(descriptor)
            path_metadata = os.stat(
                name,
                dir_fd=pinned_directory_descriptor,
                follow_symlinks=False,
            )
            maximum_size = _watchdog_artifact_size_limit(name)
            content = os.pread(descriptor, maximum_size + 1, 0)
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_uid != os.getuid()
                or stat.S_IMODE(metadata.st_mode) != 0o600
                or metadata.st_nlink != 1
                or metadata.st_size <= 0
                or metadata.st_size > maximum_size
                or len(content) != metadata.st_size
                or not content.endswith(b"\n")
                or (metadata.st_dev, metadata.st_ino)
                != (path_metadata.st_dev, path_metadata.st_ino)
                or path_metadata.st_nlink != 1
                or content != verified_terminal_contents.get(name)
            ):
                raise CleanupUncertainError(
                    f"The terminal watchdog artifact identity is unsafe: {name}"
                )
            artifacts.append(
                PinnedWatchdogArtifact(
                    name=name,
                    descriptor=descriptor,
                    device=metadata.st_dev,
                    inode=metadata.st_ino,
                    content=content,
                )
            )
        if launch_anchor is not None:
            for name in forge_server_launch_anchor.ARTIFACT_FILE_NAMES:
                verified_artifact = launch_anchor.pinned_artifacts[name]
                flags = os.O_RDONLY
                flags |= getattr(os, "O_CLOEXEC", 0)
                flags |= getattr(os, "O_NOFOLLOW", 0)
                descriptor = os.open(
                    name,
                    flags,
                    dir_fd=pinned_directory_descriptor,
                )
                artifact_descriptors.append(descriptor)
                metadata = os.fstat(descriptor)
                path_metadata = os.stat(
                    name,
                    dir_fd=pinned_directory_descriptor,
                    follow_symlinks=False,
                )
                content = os.pread(
                    descriptor,
                    forge_server_launch_anchor.MAXIMUM_ARTIFACT_SIZE_BYTES + 1,
                    0,
                )
                identity = verified_artifact.identity
                if (
                    not stat.S_ISREG(metadata.st_mode)
                    or metadata.st_uid != os.getuid()
                    or stat.S_IMODE(metadata.st_mode) != 0o600
                    or metadata.st_nlink != 1
                    or metadata.st_size <= 0
                    or metadata.st_size
                    > forge_server_launch_anchor.MAXIMUM_ARTIFACT_SIZE_BYTES
                    or len(content) != metadata.st_size
                    or not content.endswith(b"\n")
                    or (metadata.st_dev, metadata.st_ino)
                    != (path_metadata.st_dev, path_metadata.st_ino)
                    or path_metadata.st_nlink != 1
                    or metadata.st_dev != identity.device
                    or metadata.st_ino != identity.inode
                    or metadata.st_uid != identity.owner_user_id
                    or stat.S_IMODE(metadata.st_mode) != identity.mode
                    or metadata.st_nlink != identity.link_count
                    or metadata.st_size != identity.size
                    or content != verified_artifact.content
                    or hashlib.sha256(content).hexdigest()
                    != verified_artifact.sha256
                ):
                    raise CleanupUncertainError(
                        f"The terminal launch-anchor artifact identity is unsafe: {name}"
                    )
                launch_anchor_artifacts.append(
                    PinnedWatchdogArtifact(
                        name=name,
                        descriptor=descriptor,
                        device=metadata.st_dev,
                        inode=metadata.st_ino,
                        content=content,
                    )
                )
        return PinnedWatchdogEvidence(
            directory_path=directory_path,
            directory_descriptor=pinned_directory_descriptor,
            directory_device=pinned_directory.st_dev,
            directory_inode=pinned_directory.st_ino,
            artifacts=tuple(artifacts),
            launch_anchor_artifacts=tuple(launch_anchor_artifacts),
        )
    except BaseException:
        for descriptor in artifact_descriptors:
            try:
                os.close(descriptor)
            except OSError:
                pass
        if pinned_directory_descriptor >= 0:
            try:
                os.close(pinned_directory_descriptor)
            except OSError:
                pass
        raise


def verify_pinned_watchdog_evidence(
    evidence: PinnedWatchdogEvidence,
    expected_directory_descriptor: int,
) -> dict[str, dict[str, object]]:
    """Revalidates pinned bytes and returns their publication provenance."""

    try:
        directory = os.fstat(evidence.directory_descriptor)
        expected_directory = os.fstat(expected_directory_descriptor)
        current_directory = os.lstat(evidence.directory_path)
    except OSError as exception:
        raise CleanupUncertainError(
            f"Cannot revalidate pinned watchdog evidence: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(directory.st_mode)
        or directory.st_uid != os.getuid()
        or stat.S_IMODE(directory.st_mode) != 0o700
        or (directory.st_dev, directory.st_ino)
        != (evidence.directory_device, evidence.directory_inode)
        or (directory.st_dev, directory.st_ino)
        != (expected_directory.st_dev, expected_directory.st_ino)
        or (directory.st_dev, directory.st_ino)
        != (current_directory.st_dev, current_directory.st_ino)
    ):
        raise CleanupUncertainError(
            "The pinned watchdog evidence runtime identity changed"
        )

    records: dict[str, dict[str, object]] = {}
    for artifact in evidence.artifacts:
        try:
            metadata = os.fstat(artifact.descriptor)
            path_metadata = os.stat(
                artifact.name,
                dir_fd=evidence.directory_descriptor,
                follow_symlinks=False,
            )
            content = os.pread(
                artifact.descriptor,
                len(artifact.content) + 1,
                0,
            )
        except OSError as exception:
            raise CleanupUncertainError(
                f"Cannot revalidate pinned watchdog artifact: {exception}"
            ) from exception
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != os.getuid()
            or stat.S_IMODE(metadata.st_mode) != 0o600
            or metadata.st_nlink != 1
            or (metadata.st_dev, metadata.st_ino)
            != (artifact.device, artifact.inode)
            or (metadata.st_dev, metadata.st_ino)
            != (path_metadata.st_dev, path_metadata.st_ino)
            or path_metadata.st_nlink != 1
            or content != artifact.content
        ):
            raise CleanupUncertainError(
                f"The pinned watchdog artifact changed: {artifact.name}"
            )
        records[artifact.name] = {
            "relative_path": artifact.name,
            "size": len(artifact.content),
            "sha256": hashlib.sha256(artifact.content).hexdigest(),
        }
    expected_names = {
        forge_server_launch_watchdog.READINESS_FILE_NAME,
        forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
    }
    if set(records) != expected_names:
        raise CleanupUncertainError(
            "The pinned watchdog artifact inventory changed"
        )
    return records


def verify_pinned_launch_anchor_evidence(
    evidence: PinnedWatchdogEvidence,
    expected_directory_descriptor: int,
) -> dict[str, dict[str, object]]:
    """Revalidates exact launch-anchor bytes and returns their provenance."""

    verify_pinned_watchdog_evidence(evidence, expected_directory_descriptor)
    records: dict[str, dict[str, object]] = {}
    for artifact in evidence.launch_anchor_artifacts:
        try:
            metadata = os.fstat(artifact.descriptor)
            path_metadata = os.stat(
                artifact.name,
                dir_fd=evidence.directory_descriptor,
                follow_symlinks=False,
            )
            content = os.pread(
                artifact.descriptor,
                len(artifact.content) + 1,
                0,
            )
        except OSError as exception:
            raise CleanupUncertainError(
                f"Cannot revalidate pinned launch-anchor artifact: {exception}"
            ) from exception
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != os.getuid()
            or stat.S_IMODE(metadata.st_mode) != 0o600
            or metadata.st_nlink != 1
            or (metadata.st_dev, metadata.st_ino)
            != (artifact.device, artifact.inode)
            or (metadata.st_dev, metadata.st_ino)
            != (path_metadata.st_dev, path_metadata.st_ino)
            or path_metadata.st_nlink != 1
            or content != artifact.content
        ):
            raise CleanupUncertainError(
                f"The pinned launch-anchor artifact changed: {artifact.name}"
            )
        records[artifact.name] = {
            "relative_path": artifact.name,
            "size": len(artifact.content),
            "sha256": hashlib.sha256(artifact.content).hexdigest(),
        }
    if tuple(records) != forge_server_launch_anchor.ARTIFACT_FILE_NAMES:
        raise CleanupUncertainError(
            "The pinned launch-anchor artifact inventory changed"
        )
    return records


def close_pinned_watchdog_evidence(evidence: PinnedWatchdogEvidence) -> None:
    """Closes terminal artifact and runtime descriptors exactly once."""

    for artifact in evidence.artifacts + evidence.launch_anchor_artifacts:
        os.close(artifact.descriptor)
    os.close(evidence.directory_descriptor)


def publish_bytes_exclusive(path: Path, content: bytes) -> None:
    if path.exists() or path.is_symlink():
        raise E2EError(f"Refusing to replace existing evidence: {path}")
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.link(temporary_path, path)
    except FileExistsError as exception:
        raise E2EError(f"Refusing to replace existing evidence: {path}") from exception
    finally:
        temporary_path.unlink(missing_ok=True)


def publish_json_exclusive(path: Path, value: dict[str, object]) -> None:
    try:
        content = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
    except (TypeError, ValueError) as exception:
        raise E2EError(f"Cannot encode evidence JSON for {path}: {exception}") from exception
    publish_bytes_exclusive(path, content)


def verify_profile_marker(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> None:
    target_root = root or runtime_root(configuration)
    ensure_no_symlink_components(target_root, target_root.parent.parent)
    if not target_root.is_dir() or target_root.is_symlink():
        raise E2EError(f"Refusing to adopt unmarked server runtime: {target_root}")
    marker_path = profile_marker_path(configuration, target_root)
    if marker_path.is_symlink() or not marker_path.is_file():
        raise E2EError(f"Refusing to adopt unmarked server runtime: {target_root}")
    if not exact_json_value(
        load_json_object(marker_path, "server profile marker"),
        profile_descriptor(configuration),
    ):
        raise E2EError(f"Server profile marker does not match: {marker_path}")


def verify_controlled_server_files(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> None:
    game_root = game_directory(configuration, root)
    eula_path = game_root / "eula.txt"
    properties_path = game_root / "server.properties"
    for path, expected, description in (
        (eula_path, b"eula=true\n", "Dedicated-server EULA"),
        (
            properties_path,
            SERVER_PROPERTIES_CONTENT.encode("utf-8"),
            "Dedicated-server properties",
        ),
    ):
        ensure_regular_unlinked_file(path, description)
        try:
            actual = path.read_bytes()
        except OSError as exception:
            raise E2EError(f"Cannot read {description} {path}: {exception}") from exception
        if actual != expected:
            raise E2EError(f"{description} differs from its deterministic contract: {path}")


def verify_evidence_layout(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
    require_empty: bool = False,
) -> None:
    target_root = root or runtime_root(configuration)
    evidence_directory = target_root / str(evidence_spec(configuration)["directory"])
    if not evidence_directory.is_dir() or evidence_directory.is_symlink():
        raise E2EError(f"Server evidence root is missing or linked: {evidence_directory}")
    expected_scenario_name = str(evidence_spec(configuration)["scenario_directory"])
    if {entry.name for entry in evidence_directory.iterdir()} != {expected_scenario_name}:
        raise E2EError("Server evidence scenario inventory changed")
    scenario_root = evidence_root(configuration, target_root)
    ensure_no_symlink_components(scenario_root, target_root)
    if not scenario_root.is_dir() or scenario_root.is_symlink():
        raise E2EError(f"Server evidence scenario root is missing or linked: {scenario_root}")
    actual_directories = {entry.name for entry in scenario_root.iterdir()}
    if actual_directories != {"reports", "logs"}:
        raise E2EError("Server evidence scenario directory inventory changed")
    for name in ("reports", "logs"):
        directory = scenario_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Server evidence directory is missing or linked: {directory}")
        if require_empty and any(directory.iterdir()):
            raise E2EError(f"Fresh server evidence directory is not empty: {directory}")


def verify_runtime(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
    require_fresh_evidence: bool = True,
    owned_process_log: OwnedLaunchFile | None = None,
) -> None:
    ensure_owned_state_roots(state_root)
    target_root = runtime_root(configuration, state_root)
    verify_profile_marker(configuration, target_root)
    expected_runtime_entries = {PROFILE_MARKER_NAME, "game", "evidence"}
    if owned_process_log is not None:
        if (
            owned_process_log.path.parent != target_root
            or re.fullmatch(
                r"\.forge-server-gradle\.[0-9a-f]{32}\.log",
                owned_process_log.path.name,
            )
            is None
        ):
            raise E2EError("The owned Forge server process log identity is unsafe")
        verify_owned_launch_file(owned_process_log)
        expected_runtime_entries.add(owned_process_log.path.name)
    actual_runtime_entries = {entry.name for entry in target_root.iterdir()}
    if actual_runtime_entries != expected_runtime_entries:
        raise E2EError(
            "The pristine server runtime inventory changed: "
            f"missing={sorted(expected_runtime_entries - actual_runtime_entries)}, "
            f"unexpected={sorted(actual_runtime_entries - expected_runtime_entries)}"
        )
    target_game_root = game_directory(configuration, target_root)
    if not target_game_root.is_dir() or target_game_root.is_symlink():
        raise E2EError(f"Dedicated-server game directory is missing or linked: {target_game_root}")
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Dedicated-server directory is missing or linked: {directory}")
    expected_game_entries = {
        "config",
        "crash-reports",
        "logs",
        "mods",
        "world",
        "eula.txt",
        "server.properties",
    }
    actual_game_entries = {entry.name for entry in target_game_root.iterdir()}
    if actual_game_entries != expected_game_entries:
        raise E2EError(
            "The pristine dedicated-server game inventory changed: "
            f"missing={sorted(expected_game_entries - actual_game_entries)}, "
            f"unexpected={sorted(actual_game_entries - expected_game_entries)}"
        )
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if any(directory.iterdir()):
            raise E2EError(f"The pristine dedicated-server directory is not empty: {directory}")
    verify_controlled_server_files(configuration, target_root)
    verify_evidence_layout(
        configuration,
        target_root,
        require_empty=require_fresh_evidence,
    )


def verify_generated_runtime(
    configuration: ResolvedConfiguration,
    root: Path,
    owned_process_log: OwnedLaunchFile,
    watchdog_evidence: PinnedWatchdogEvidence,
) -> None:
    verify_profile_marker(configuration, root)
    if owned_process_log.path.parent != root:
        raise E2EError("The generated runtime process log is not owned")
    verify_owned_launch_file(owned_process_log, require_initial_content=False)
    verify_pinned_watchdog_evidence(
        watchdog_evidence,
        owned_process_log.directory_descriptor,
    )
    verify_pinned_launch_anchor_evidence(
        watchdog_evidence,
        owned_process_log.directory_descriptor,
    )
    expected_root_entries = {
        PROFILE_MARKER_NAME,
        "game",
        "evidence",
        MEMORY_HANDOFF_FILE_NAME,
        MEMORY_ACKNOWLEDGEMENT_FILE_NAME,
        macos_guarded_java.READINESS_FILE_NAME,
        macos_guarded_java.TELEMETRY_FILE_NAME,
        forge_server_launch_watchdog.READINESS_FILE_NAME,
        forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
        forge_server_launch_anchor.STAGED_SOURCE_FILE_NAME,
        forge_server_launch_anchor.STAGED_WRAPPER_JAR_FILE_NAME,
        forge_server_launch_anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME,
        *forge_server_launch_anchor.ARTIFACT_FILE_NAMES,
        owned_process_log.path.name,
    }
    actual_root_entries = {entry.name for entry in root.iterdir()}
    if actual_root_entries != expected_root_entries:
        raise E2EError(
            "The generated server runtime root inventory changed: "
            f"missing={sorted(expected_root_entries - actual_root_entries)}, "
            f"unexpected={sorted(actual_root_entries - expected_root_entries)}"
        )
    for path, description in (
        (root / MEMORY_HANDOFF_FILE_NAME, "server Java memory handoff"),
        (
            root / MEMORY_ACKNOWLEDGEMENT_FILE_NAME,
            "server Java memory acknowledgement",
        ),
        (
            root / macos_guarded_java.READINESS_FILE_NAME,
            "server memory-guard readiness",
        ),
        (
            root / macos_guarded_java.TELEMETRY_FILE_NAME,
            "server memory-guard telemetry",
        ),
        (
            root / forge_server_launch_watchdog.READINESS_FILE_NAME,
            "launch-watchdog readiness",
        ),
        (
            root / forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
            "launch-watchdog telemetry",
        ),
        *(
            (root / name, "launch-anchor artifact")
            for name in forge_server_launch_anchor.ARTIFACT_FILE_NAMES
        ),
    ):
        ensure_regular_unlinked_file(path, description)
    for name, expected_size, expected_sha256 in (
        (
            forge_server_launch_anchor.STAGED_SOURCE_FILE_NAME,
            LAUNCH_ANCHOR_JAVA_SOURCE_SIZE,
            LAUNCH_ANCHOR_JAVA_SOURCE_SHA256,
        ),
        (
            forge_server_launch_anchor.STAGED_WRAPPER_JAR_FILE_NAME,
            GRADLE_WRAPPER_JAR_SIZE,
            GRADLE_WRAPPER_JAR_SHA256,
        ),
        (
            forge_server_launch_anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME,
            GRADLE_WRAPPER_PROPERTIES_SIZE,
            GRADLE_WRAPPER_PROPERTIES_SHA256,
        ),
    ):
        staged_path = root / name
        ensure_regular_unlinked_file(staged_path, "staged launch-anchor input")
        metadata = staged_path.stat()
        if (
            metadata.st_uid != os.getuid()
            or stat.S_IMODE(metadata.st_mode) != 0o400
            or metadata.st_nlink != 1
            or metadata.st_size != expected_size
            or sha256_file(staged_path) != expected_sha256
        ):
            raise E2EError(f"The staged launch-anchor input changed: {name}")
    target_game_root = game_directory(configuration, root)
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Generated server directory is missing or linked: {directory}")
        ensure_no_symlink_components(directory, root)
        for descendant in directory.rglob("*"):
            if descendant.is_symlink():
                raise E2EError(f"Generated server state contains a symlink: {descendant}")
    mods_directory = target_game_root / "mods"
    if any(mods_directory.iterdir()):
        raise E2EError("The Loom dedicated server must not stage root mod JARs")
    verify_evidence_layout(configuration, root)


def provision_profile(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> None:
    require_native_run_ready()
    require_unsealed_profile(configuration)
    require_unattempted_profile(configuration, state_root)
    ensure_owned_state_roots(state_root)
    state_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    runtimes_root = state_root / "runtimes"
    runtimes_root.mkdir(mode=0o700, exist_ok=True)
    provision_gradle_user_home(state_root)
    target_root = runtime_root(configuration, state_root)
    ensure_no_symlink_components(target_root, state_root)
    if target_root.exists() or target_root.is_symlink():
        raise E2EError(
            "Refusing to reuse an existing dedicated-server runtime: "
            f"{target_root}"
        )

    staging_root = Path(
        tempfile.mkdtemp(prefix=f".{PROFILE_ID}.", dir=runtimes_root)
    )
    try:
        game_root = game_directory(configuration, staging_root)
        game_root.mkdir(mode=0o700)
        for name in ("config", "crash-reports", "logs", "mods", "world"):
            (game_root / name).mkdir(mode=0o700)
        write_bytes_exclusive(game_root / "eula.txt", b"eula=true\n")
        write_bytes_exclusive(
            game_root / "server.properties",
            SERVER_PROPERTIES_CONTENT.encode("utf-8"),
        )
        scenario_root = evidence_root(configuration, staging_root)
        (scenario_root / "reports").mkdir(mode=0o700, parents=True)
        (scenario_root / "logs").mkdir(mode=0o700)
        write_bytes_exclusive(
            profile_marker_path(configuration, staging_root),
            (json.dumps(profile_descriptor(configuration), indent=2, sort_keys=True) + "\n").encode(
                "utf-8"
            ),
        )
        try:
            target_root.mkdir(mode=0o700)
        except FileExistsError as exception:
            raise E2EError(
                "Refusing to reuse an existing dedicated-server runtime: "
                f"{target_root}"
            ) from exception
        for staging_entry in staging_root.iterdir():
            os.replace(staging_entry, target_root / staging_entry.name)
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root)
    verify_runtime(configuration, state_root)


def java_major_version(java_path: Path) -> int | None:
    require_no_java_processes()
    try:
        forge_server_launch_anchor.validate_root_owned_java_executable(
            java_path
        )
    except forge_server_launch_anchor.LaunchAnchorError:
        return None
    try:
        completed = subprocess.run(
            [
                str(java_path),
                *JAVA_VERSION_PROBE_JVM_ARGUMENTS,
                "-XshowSettings:properties",
                "-version",
            ],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            env={
                "LANG": "C.UTF-8",
                "LC_ALL": "C.UTF-8",
                "PATH": GRADLE_EXECUTABLE_SEARCH_PATH,
            },
            text=True,
            timeout=20,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    match = re.search(r"java\.specification\.version\s*=\s*(\d+)", completed.stdout)
    if completed.returncode != 0 or match is None:
        return None
    return int(match.group(1))


def resolve_gradle_java() -> Path:
    candidates: list[Path] = []
    override = os.environ.get(GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE)
    if override:
        candidates.append(Path(override))
    configured_java_home = os.environ.get("JAVA_HOME")
    if configured_java_home:
        candidates.append(Path(configured_java_home) / "bin" / "java")
    java_home_tool = Path("/usr/libexec/java_home")
    if java_home_tool.is_file():
        try:
            completed = subprocess.run(
                [str(java_home_tool), "-v", "21"],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            completed = None
        if completed is not None and completed.returncode == 0 and completed.stdout.strip():
            candidates.append(Path(completed.stdout.strip()) / "bin" / "java")
    gradle_jdks = Path.home() / ".gradle" / "jdks"
    if gradle_jdks.is_dir() and not gradle_jdks.is_symlink():
        candidates.extend(sorted(gradle_jdks.glob("**/Contents/Home/bin/java")))
    path_java = shutil.which("java")
    if path_java is not None:
        candidates.append(Path(path_java))
    checked: set[Path] = set()
    for candidate in candidates:
        resolved = candidate.resolve(strict=False)
        if resolved in checked:
            continue
        checked.add(resolved)
        major_version = java_major_version(resolved)
        if major_version == 21:
            return resolved
    raise E2EError(
        "No exact JDK 21 was found for Gradle; set "
        f"{GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} to its Java executable"
    )


def verify_gradle_probe_definition(configuration: ResolvedConfiguration) -> None:
    build_file = configuration.repository_root / "forge/build.gradle.kts"
    ensure_regular_unlinked_file(build_file, "Forge build configuration")
    try:
        content = build_file.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read Forge build configuration: {exception}") from exception
    required_fragments = (
        "runRegistryFoundationServerProbe",
        "runServerProbeGradleTopologyPreflight",
        "serverProbeGradleTopologyTask.configure {",
        "dependsOn(validateServerProbeRunConfiguration)",
        "e2e-harness/gradle-topology/1.20.1/src/main/java",
        "GradleJavaExecTopologyProbe",
        'jvmArgs("-Xmx2048m")',
        "serverProbeMaximumHeapArguments == listOf(",
        "etherology.serverProbe.profileId",
        "etherology.serverProbe.scenario",
        "etherology.serverProbe.evidenceRoot",
        "serverProbeJavaVersion = javaVersion",
        'tasks.named<JavaExec>("runRegistryFoundationServerProbe")',
        "javaLauncher.set(",
        "languageVersion.set(JavaLanguageVersion.of(serverProbeJavaVersion))",
        "serverProbeRunTask.configure",
        "dependsOn(verifyRegistryFoundationServerProbe)",
        (
            "        dependsOn(\n"
            "            validateForgeSlitheriteStaticMilestone,\n"
            "            validateForgeAcceptedDataSet,\n"
        ),
        (
            "            dependsOn(\n"
            "                validateForgeAlchemyRecipeFoundationStaticMilestone,\n"
            "                validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity,\n"
            "                forgeSlitheriteBlockRegistryServerSafetyTest,\n"
        ),
        RUN_TOKEN_ENVIRONMENT_VARIABLE,
        "serverProbeSealedArchive",
        "serverProbeRunLock",
        "serverProbeRunAttempt",
        "serverProbeProfileMarker",
        'jvmArguments.add("-Xmx${serverProbeLaunch.getValue('
        '"maximum_memory_mb")}m")',
        PROFILE_ID,
        SCENARIO_ID,
    )
    missing = [fragment for fragment in required_fragments if fragment not in content]
    stale_fragments = (
        "blockPostponedSlitheriteV20NativeRun",
        "blockPostponedSlitheriteV21NativeRun",
        "forgeServerNativeRunPostponedReason",
        "            validateForgeSlitheriteMilestone,\n",
    )
    stale = [fragment for fragment in stale_fragments if fragment in content]
    if missing or stale:
        raise E2EError(
            "The named Forge server probe task is incomplete: "
            f"missing={missing}, stale={stale}"
        )
    verify_probe_source_lifecycle(configuration)
    verify_memory_handoff_source(configuration)
    verify_gradle_topology_source(configuration)


def verify_probe_source_lifecycle(configuration: ResolvedConfiguration) -> None:
    source_path = configuration.repository_root / PROBE_SOURCE_RELATIVE_PATH
    ensure_regular_unlinked_file(source_path, "Forge server probe source")
    try:
        content = source_path.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read Forge server probe source: {exception}") from exception
    match = re.search(
        r"private static final List<String> EXPECTED_LIFECYCLE = List\.of\((.*?)\n    \);",
        content,
        flags=re.DOTALL,
    )
    if match is None:
        raise E2EError("The Forge server probe lifecycle declaration is missing")
    source_lifecycle = tuple(re.findall(r'"([a-z_]+)"', match.group(1)))
    if source_lifecycle != EXPECTED_LIFECYCLE:
        raise E2EError(
            "The Forge server probe and runner lifecycle contracts differ: "
            f"source={source_lifecycle}, runner={EXPECTED_LIFECYCLE}"
        )


def verify_memory_handoff_source(configuration: ResolvedConfiguration) -> None:
    """Pins the in-JVM identity handoff and its exact 2048-MiB contract."""

    probe_path = configuration.repository_root / PROBE_SOURCE_RELATIVE_PATH
    handoff_path = (
        configuration.repository_root / MEMORY_HANDOFF_SOURCE_RELATIVE_PATH
    )
    ensure_regular_unlinked_file(probe_path, "Forge server probe source")
    ensure_regular_unlinked_file(handoff_path, "Forge server memory handoff source")
    try:
        probe_content = probe_path.read_text(encoding="utf-8")
        handoff_content = handoff_path.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(
            f"Cannot read Forge server memory handoff sources: {exception}"
        ) from exception
    required_probe_fragments = (
        "ServerProbeMemoryHandoff.publishAndAwaitAcknowledgement();",
    )
    required_handoff_fragments = (
        f'"{MEMORY_HANDOFF_ENVIRONMENT_VARIABLE}"',
        f'"{MEMORY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE}"',
        f'"{RUN_TOKEN_ENVIRONMENT_VARIABLE}"',
        f'EXACT_MAXIMUM_HEAP_ARGUMENT = "{SERVER_MAXIMUM_HEAP_ARGUMENT}"',
        "EXACT_MAXIMUM_HEAP_BYTES = 2L * 1024L * 1024L * 1024L",
        "ProcessHandle.current()",
        "Runtime.getRuntime().maxMemory()",
        "getRuntimeMXBean()",
        '"JAVA_TOOL_OPTIONS"',
        '"JDK_JAVA_OPTIONS"',
        '"_JAVA_OPTIONS"',
        "Files.createLink(target, temporaryPath)",
        "awaitAcknowledgement(acknowledgementPath, runToken)",
    )
    if any(fragment not in probe_content for fragment in required_probe_fragments):
        raise E2EError("The Forge server probe memory handoff call is incomplete")
    missing = [
        fragment
        for fragment in required_handoff_fragments
        if fragment not in handoff_content
    ]
    if missing:
        raise E2EError(
            f"The Forge server memory handoff implementation is incomplete: {missing}"
        )


def verify_gradle_topology_source(configuration: ResolvedConfiguration) -> None:
    """Pins the loader-free JavaExec topology handshake used before one-shot runs."""

    source_path = configuration.repository_root / GRADLE_TOPOLOGY_SOURCE_RELATIVE_PATH
    ensure_regular_unlinked_file(source_path, "Gradle topology probe source")
    try:
        content = source_path.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(
            f"Cannot read Gradle topology probe source: {exception}"
        ) from exception
    required_fragments = (
        f'"{GRADLE_TOPOLOGY_TOKEN_ENVIRONMENT_VARIABLE}"',
        f'"{GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE}"',
        f'"{GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE}"',
        f'EXACT_MAXIMUM_HEAP_ARGUMENT = "{SERVER_MAXIMUM_HEAP_ARGUMENT}"',
        "EXACT_MAXIMUM_HEAP_BYTES =",
        "2L * 1024L * 1024L * 1024L",
        "ProcessHandle.current()",
        "currentProcess.parent().orElseThrow(",
        "Runtime.version().feature() != 17",
        "maximumHeapBytes != EXACT_MAXIMUM_HEAP_BYTES",
        "Files.createLink(target, temporaryPath)",
        "Files.size(acknowledgementPath)",
        "awaitAcknowledgement(acknowledgementPath, token)",
    )
    missing = [fragment for fragment in required_fragments if fragment not in content]
    if missing:
        raise E2EError(
            f"The Gradle topology probe implementation is incomplete: {missing}"
        )


def verify_gradle_launcher_contract(configuration: ResolvedConfiguration) -> None:
    """Pins the wrapper inputs needed for one in-process Gradle 9.6.1 JVM."""

    gradle_wrapper = configuration.repository_root / "gradlew"
    wrapper_properties = (
        configuration.repository_root / GRADLE_WRAPPER_PROPERTIES_RELATIVE_PATH
    )
    wrapper_jar = configuration.repository_root / GRADLE_WRAPPER_JAR_RELATIVE_PATH
    ensure_regular_unlinked_file(gradle_wrapper, "Gradle wrapper")
    ensure_regular_unlinked_file(wrapper_properties, "Gradle wrapper properties")
    ensure_regular_unlinked_file(wrapper_jar, "Gradle wrapper JAR")
    if (
        gradle_wrapper.stat().st_size != GRADLE_WRAPPER_SCRIPT_SIZE
        or sha256_file(gradle_wrapper) != GRADLE_WRAPPER_SCRIPT_SHA256
    ):
        raise E2EError("The pinned Gradle launcher script changed")
    if (
        wrapper_properties.stat().st_size != GRADLE_WRAPPER_PROPERTIES_SIZE
        or sha256_file(wrapper_properties) != GRADLE_WRAPPER_PROPERTIES_SHA256
    ):
        raise E2EError("The pinned Gradle wrapper properties changed")
    if (
        wrapper_jar.stat().st_size != GRADLE_WRAPPER_JAR_SIZE
        or sha256_file(wrapper_jar) != GRADLE_WRAPPER_JAR_SHA256
    ):
        raise E2EError("The pinned Gradle wrapper JAR changed")
    try:
        wrapper_content = gradle_wrapper.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read the Gradle wrapper: {exception}") from exception
    required_wrapper_fragments = (
        'DEFAULT_JVM_OPTS=\'"-Xmx64m" "-Xms64m"\'',
        'printf \'%s\\n\' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS"',
        'exec "$JAVACMD" "$@"',
    )
    if any(
        fragment not in wrapper_content for fragment in required_wrapper_fragments
    ):
        raise E2EError("The Gradle launcher JVM option order changed")
    expected_wrapper_properties = {
        "distributionBase": "GRADLE_USER_HOME",
        "distributionPath": "wrapper/dists",
        "distributionUrl": (
            "https\\://services.gradle.org/distributions/gradle-9.6.1-bin.zip"
        ),
        "distributionSha256Sum": (
            "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
        ),
        "networkTimeout": "10000",
        "validateDistributionUrl": "true",
        "zipStoreBase": "GRADLE_USER_HOME",
        "zipStorePath": "wrapper/dists",
    }
    if parse_gradle_properties(wrapper_properties) != expected_wrapper_properties:
        raise E2EError("The pinned Gradle 9.6.1 wrapper contract changed")
    for name, expected in {
        "org.gradle.jvmargs": GRADLE_LAUNCHER_MAXIMUM_HEAP_ARGUMENT,
        "org.gradle.daemon": "false",
        "org.gradle.parallel": "false",
        "org.gradle.workers.max": "2",
    }.items():
        if configuration.properties.get(name) != expected:
            raise E2EError(f"The Gradle launcher property changed: {name}")


def gradle_user_home(state_root: Path = STATE_ROOT) -> Path:
    """Returns the repository-owned Gradle home for one controller state root."""

    return state_root / GRADLE_USER_HOME.name


def verify_shared_gradle_cache_home(
    path: Path | None = None,
) -> None:
    """Pins the existing host caches without importing user initialization."""

    selected_path = SHARED_GRADLE_CACHE_HOME if path is None else path
    if (
        not selected_path.is_absolute()
        or selected_path.is_symlink()
        or not selected_path.is_dir()
    ):
        raise E2EError(
            f"The shared Gradle cache home is missing or linked: {selected_path}"
        )
    try:
        shared_metadata = selected_path.stat()
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect the shared Gradle cache home: {exception}"
        ) from exception
    if shared_metadata.st_uid != os.getuid() or shared_metadata.st_mode & 0o022:
        raise E2EError(
            "The shared Gradle cache home is not exclusively owner-controlled: "
            f"{selected_path}"
        )
    for name in GRADLE_CACHE_BRIDGE_NAMES:
        target = selected_path / name
        if not target.is_dir() or target.is_symlink():
            raise E2EError(f"A required shared Gradle cache is unsafe: {target}")
        metadata = target.stat()
        if metadata.st_uid != os.getuid() or metadata.st_mode & 0o022:
            raise E2EError(
                f"A required shared Gradle cache is not owner-controlled: {target}"
            )


def verify_gradle_distribution_initialization(
    shared_cache_home: Path | None = None,
) -> None:
    """Rejects executable initialization injected into the pinned Gradle runtime."""

    selected_shared_cache_home = (
        SHARED_GRADLE_CACHE_HOME
        if shared_cache_home is None
        else shared_cache_home
    )
    verify_shared_gradle_cache_home(selected_shared_cache_home)
    distribution = selected_shared_cache_home / GRADLE_DISTRIBUTION_RELATIVE_PATH
    ensure_no_symlink_components(distribution, selected_shared_cache_home)
    if not distribution.is_dir() or distribution.is_symlink():
        raise E2EError(
            f"The pinned extracted Gradle distribution is missing or linked: {distribution}"
        )
    initialization_directory = distribution / "init.d"
    ensure_no_symlink_components(initialization_directory, selected_shared_cache_home)
    if (
        not initialization_directory.is_dir()
        or initialization_directory.is_symlink()
    ):
        raise E2EError(
            "The pinned Gradle distribution initialization directory is unsafe: "
            f"{initialization_directory}"
        )
    for directory in (distribution, initialization_directory):
        metadata = directory.stat()
        if metadata.st_uid != os.getuid() or metadata.st_mode & 0o022:
            raise E2EError(
                f"A pinned Gradle distribution directory is not owner-controlled: {directory}"
            )
    expected_readme = initialization_directory / "readme.txt"
    if {entry.name for entry in initialization_directory.iterdir()} != {
        expected_readme.name
    }:
        raise E2EError(
            "The pinned Gradle distribution initialization inventory changed"
        )
    ensure_regular_unlinked_file(
        expected_readme,
        "Pinned Gradle distribution initialization readme",
    )
    readme_metadata = expected_readme.stat()
    if (
        readme_metadata.st_uid != os.getuid()
        or readme_metadata.st_mode & 0o022
        or readme_metadata.st_size != GRADLE_DISTRIBUTION_INIT_README_SIZE
        or sha256_file(expected_readme)
        != GRADLE_DISTRIBUTION_INIT_README_SHA256
    ):
        raise E2EError(
            "The pinned Gradle distribution initialization readme changed"
        )


def provision_gradle_user_home(
    state_root: Path = STATE_ROOT,
    shared_cache_home: Path | None = None,
) -> Path:
    """Creates an isolated Gradle home with cache-only host bridges."""

    selected_shared_cache_home = (
        SHARED_GRADLE_CACHE_HOME
        if shared_cache_home is None
        else shared_cache_home
    )
    verify_shared_gradle_cache_home(selected_shared_cache_home)
    path = gradle_user_home(state_root)
    ensure_no_symlink_components(path, state_root)
    if path.exists() or path.is_symlink():
        verify_gradle_user_home(path, selected_shared_cache_home)
        return path
    path.mkdir(mode=0o700)
    try:
        for name in GRADLE_CACHE_BRIDGE_NAMES:
            (path / name).symlink_to(
                selected_shared_cache_home / name,
                target_is_directory=True,
            )
    except BaseException:
        shutil.rmtree(path)
        raise
    verify_gradle_user_home(path, selected_shared_cache_home)
    return path


def verify_gradle_user_home(
    path: Path = GRADLE_USER_HOME,
    shared_cache_home: Path | None = None,
) -> None:
    """Rejects user Gradle code while allowing only pinned cache bridges."""

    selected_shared_cache_home = (
        SHARED_GRADLE_CACHE_HOME
        if shared_cache_home is None
        else shared_cache_home
    )
    verify_shared_gradle_cache_home(selected_shared_cache_home)
    if not path.is_absolute() or path.is_symlink() or not path.is_dir():
        raise E2EError(f"The isolated Gradle user home is missing or linked: {path}")
    try:
        metadata = path.stat()
    except OSError as exception:
        raise E2EError(f"Cannot inspect the fixed Gradle user home: {exception}") from exception
    if metadata.st_uid != os.getuid() or metadata.st_mode & 0o022:
        raise E2EError(
            "The isolated Gradle user home is not exclusively writable by its owner"
        )
    for name in GRADLE_CACHE_BRIDGE_NAMES:
        bridge = path / name
        if (
            not bridge.is_symlink()
            or Path(os.readlink(bridge)) != selected_shared_cache_home / name
            or bridge.resolve(strict=False)
            != (selected_shared_cache_home / name).resolve(strict=False)
        ):
            raise E2EError(f"The isolated Gradle cache bridge changed: {bridge}")
    init_directory = path / "init.d"
    for init_script_name in GRADLE_USER_INIT_SCRIPT_NAMES:
        init_script = path / init_script_name
        if init_script.exists() or init_script.is_symlink():
            raise E2EError(f"A user Gradle initialization script is active: {init_script}")
    if init_directory.exists() or init_directory.is_symlink():
        if init_directory.is_symlink() or not init_directory.is_dir():
            raise E2EError(
                f"The user Gradle initialization directory is unsafe: {init_directory}"
            )
        if any(init_directory.iterdir()):
            raise E2EError(
                f"The user Gradle initialization directory is not empty: {init_directory}"
            )
    forbidden_user_files = (path / "gradle.properties", path / "gradle.properties.kts")
    for forbidden_file in forbidden_user_files:
        if forbidden_file.exists() or forbidden_file.is_symlink():
            raise E2EError(
                f"A user Gradle properties file is active: {forbidden_file}"
            )
    for entry in path.iterdir():
        if entry.name in GRADLE_CACHE_BRIDGE_NAMES:
            continue
        if entry.name in GRADLE_USER_INIT_SCRIPT_NAMES or entry in forbidden_user_files:
            raise E2EError(f"A forbidden Gradle user file is active: {entry}")
        if entry.is_symlink() or not entry.is_dir():
            raise E2EError(f"An unexpected Gradle user-home entry is unsafe: {entry}")
        entry_metadata = entry.stat()
        if entry_metadata.st_uid != os.getuid() or entry_metadata.st_mode & 0o022:
            raise E2EError(
                f"A Gradle user-home directory is not owner-controlled: {entry}"
            )


def build_gradle_arguments(task_path: str = TASK_PATH) -> tuple[str, ...]:
    """Returns the exact wrapper-main arguments accepted by the launch anchor."""

    if task_path not in {TASK_PATH, GRADLE_TOPOLOGY_TASK_PATH}:
        raise E2EError(f"The named Gradle task is not authorized: {task_path}")
    return (
        "--no-daemon",
        "--no-parallel",
        "--max-workers=2",
        "--console=plain",
        GRADLE_OFFLINE_ARGUMENT,
        GRADLE_JVM_ARGUMENTS_OVERRIDE,
        GRADLE_INSTRUMENTATION_OVERRIDE,
        task_path,
    )


def build_gradle_command(
    configuration: ResolvedConfiguration,
    java_path: Path,
    caffeinate_path: Path = CAFFEINATE_PATH,
    task_path: str = TASK_PATH,
) -> list[str]:
    """Builds the exact direct Java wrapper command the broker will release."""

    verify_gradle_launcher_contract(configuration)
    gradle_wrapper = configuration.repository_root / "gradlew"
    if not os.access(gradle_wrapper, os.X_OK):
        raise E2EError(f"Gradle wrapper is not executable: {gradle_wrapper}")
    ensure_regular_unlinked_file(caffeinate_path, "macOS caffeinate")
    java_version = java_major_version(java_path)
    if java_version != 21:
        raise E2EError(
            f"The selected Gradle Java executable is not exactly JDK 21: {java_path}"
        )
    wrapper_jar = (
        configuration.repository_root / GRADLE_WRAPPER_JAR_RELATIVE_PATH
    ).resolve()
    try:
        command = forge_server_launch_anchor.build_direct_gradle_wrapper_command(
            java_path.resolve(),
            wrapper_jar,
            build_gradle_arguments(task_path),
        )
    except forge_server_launch_anchor.LaunchAnchorError as exception:
        raise E2EError(f"Cannot build the direct Gradle wrapper command: {exception}") from exception
    return list(command)


def gradle_launch_environment(
    java_path: Path,
    inherited_environment: dict[str, str] | None = None,
    state_root: Path = STATE_ROOT,
) -> dict[str, str]:
    """Creates the scoped environment that keeps Gradle in its wrapper JVM."""

    inherited = os.environ if inherited_environment is None else inherited_environment
    try:
        macos_guarded_java.verify_java_option_environment(inherited)
    except macos_guarded_java.GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    isolated_gradle_user_home = gradle_user_home(state_root)
    verify_gradle_user_home(isolated_gradle_user_home)
    return {
        "GRADLE_OPTS": GRADLE_LAUNCHER_MAXIMUM_HEAP_ARGUMENT,
        "GRADLE_USER_HOME": str(isolated_gradle_user_home),
        "HOME": str(Path.home()),
        "JAVA_HOME": str(java_path.parent.parent),
        "JAVA_OPTS": "",
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "PATH": GRADLE_EXECUTABLE_SEARCH_PATH,
        "TMPDIR": tempfile.gettempdir(),
    }


def verify_environment(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
    run_lock: OwnedRunLock | None = None,
) -> tuple[Path, list[str]]:
    try:
        macos_guarded_java.verify_java_option_environment(os.environ)
    except macos_guarded_java.GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    lock_path = run_lock_path(configuration, state_root)
    if run_lock is None or run_lock.path != lock_path:
        raise E2EError(
            "The dedicated-server environment probe requires its global run lock"
        )
    verify_owned_run_lock(run_lock)
    require_native_run_ready()
    require_unsealed_profile(configuration)
    require_unattempted_profile(configuration, state_root)
    verify_runtime(configuration, state_root)
    verify_gradle_user_home(gradle_user_home(state_root))
    verify_gradle_distribution_initialization()
    require_no_retained_launch_runtime(state_root)
    verify_owned_run_lock(run_lock)
    verify_gradle_probe_definition(configuration)
    require_no_java_processes()
    java_path = resolve_gradle_java()
    command = build_gradle_command(configuration, java_path)
    if any(argument.startswith("-P") for argument in command):
        raise E2EError("The server probe task must own its run directory without overrides")
    require_no_java_processes()
    return java_path, command


def revalidate_after_gradle_topology(
    configuration: ResolvedConfiguration,
    java_path: Path,
    command: list[str],
    run_lock: OwnedRunLock,
    state_root: Path,
) -> tuple[ResolvedConfiguration, Path, list[str]]:
    """Closes the preflight-to-launch window before consuming the profile."""

    verify_owned_run_lock(run_lock)
    try:
        macos_guarded_java.verify_java_option_environment(os.environ)
    except macos_guarded_java.GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    refreshed = load_configuration(
        configuration.profile_manifest_path,
        configuration.repository_root,
    )
    if refreshed != configuration:
        raise E2EError("The dedicated-server configuration changed after topology")
    require_native_run_ready()
    require_unsealed_profile(refreshed)
    require_unattempted_profile(refreshed, state_root)
    verify_runtime(refreshed, state_root)
    verify_gradle_user_home(gradle_user_home(state_root))
    verify_gradle_distribution_initialization()
    require_no_retained_launch_runtime(state_root)
    verify_gradle_probe_definition(refreshed)
    require_no_java_processes()
    refreshed_java_path = resolve_gradle_java()
    refreshed_command = build_gradle_command(refreshed, refreshed_java_path)
    if refreshed_java_path != java_path or refreshed_command != command:
        raise E2EError("The Gradle launch identity changed after topology")
    require_no_java_processes()
    verify_owned_run_lock(run_lock)
    return refreshed, refreshed_java_path, refreshed_command


def final_pre_spawn_revalidation(
    configuration: ResolvedConfiguration,
    java_path: Path,
    command: list[str],
    run_lock: OwnedRunLock,
    attempt_marker: OwnedLaunchFile,
    process_log: OwnedLaunchFile,
    environment: dict[str, str],
    state_root: Path,
    run_token: str,
) -> None:
    """Performs the complete fail-closed gate immediately adjacent to ``Popen``."""

    expected_attempt_content = (
        f"profile_id={PROFILE_ID}\nscenario={SCENARIO_ID}\npid={os.getpid()}\n"
    ).encode("utf-8")
    if (
        attempt_marker.path != run_attempt_path(configuration, state_root)
        or attempt_marker.initial_content != expected_attempt_content
        or process_log.path.parent != runtime_root(configuration, state_root)
        or re.fullmatch(r"[0-9a-f]{64}", run_token) is None
    ):
        raise E2EError("The final launch-control identity is invalid")
    verify_owned_run_lock(run_lock)
    verify_owned_launch_file(attempt_marker)
    verify_owned_launch_file(process_log)
    try:
        macos_guarded_java.verify_java_option_environment(os.environ)
    except macos_guarded_java.GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    refreshed = load_configuration(
        configuration.profile_manifest_path,
        configuration.repository_root,
    )
    if refreshed != configuration:
        raise E2EError("The dedicated-server configuration changed before spawn")
    require_native_run_ready()
    require_unsealed_profile(refreshed)
    verify_runtime(
        refreshed,
        state_root,
        owned_process_log=process_log,
    )
    verify_gradle_user_home(gradle_user_home(state_root))
    verify_gradle_distribution_initialization()
    require_no_retained_launch_runtime(state_root)
    verify_gradle_probe_definition(refreshed)
    require_no_java_processes()
    refreshed_java_path = resolve_gradle_java()
    refreshed_command = build_gradle_command(refreshed, refreshed_java_path)
    if refreshed_java_path != java_path or refreshed_command != command:
        raise E2EError("The Gradle launch identity changed immediately before spawn")
    expected_environment = gradle_launch_environment(
        refreshed_java_path,
        state_root=state_root,
    )
    target_root = runtime_root(refreshed, state_root)
    handoff_path, acknowledgement_path = server_memory_handoff_paths(target_root)
    expected_environment[RUN_TOKEN_ENVIRONMENT_VARIABLE] = run_token
    expected_environment[MEMORY_HANDOFF_ENVIRONMENT_VARIABLE] = str(handoff_path)
    expected_environment[MEMORY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE] = str(
        acknowledgement_path
    )
    if environment != expected_environment:
        raise E2EError("The final Gradle launch environment changed")
    verify_owned_run_lock(run_lock)
    verify_owned_launch_file(attempt_marker)
    verify_owned_launch_file(process_log)
    require_no_java_processes()


def require_native_run_ready() -> None:
    if NATIVE_RUN_POSTPONED:
        raise E2EError(NATIVE_RUN_POSTPONED_REASON)


def validate_probe_report(
    report: dict[str, object],
    configuration: ResolvedConfiguration,
) -> None:
    """Validates a probe report through the immutable profile-v21 contract."""
    required_mod_ids = require_list(configuration.manifest, "required_mod_ids")
    forbidden_mod_ids = require_list(configuration.manifest, "forbidden_mod_ids")
    try:
        contract_v21.validate_probe_report(
            report,
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v21.V21ContractError as exception:
        raise E2EError(str(exception)) from exception


def validate_server_log(path: Path) -> bytes:
    ensure_regular_unlinked_file(path, "Dedicated-server latest log")
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAXIMUM_SERVER_LOG_SIZE:
            raise E2EError(f"Dedicated-server latest log has invalid size: {size}")
        content = path.read_bytes()
    except OSError as exception:
        raise E2EError(f"Cannot read dedicated-server latest log: {exception}") from exception
    text = content.decode("utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_SERVER_LOG_MARKERS if marker in text), None
    )
    if fatal_marker is not None:
        raise E2EError(f"Dedicated-server latest log contains fatal marker: {fatal_marker}")
    client_marker = next(
        (marker for marker in CLIENT_LOG_MARKERS if marker in text), None
    )
    if client_marker is not None:
        raise E2EError(
            f"Dedicated-server latest log contains client marker: {client_marker}"
        )
    unexpected_client_class = next(
        (
            class_name
            for class_name in re.findall(CLIENT_CLASS_PATTERN, text)
            if class_name not in ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES
        ),
        None,
    )
    if unexpected_client_class is not None:
        raise E2EError(
            "Dedicated-server latest log contains unexpected client class marker: "
            f"{unexpected_client_class}"
        )
    phases = re.findall(r"\[EtherologyServerProbe\] ([a-z_]+)", text)
    expected_phases = [*PROBE_LOG_PHASES, "loom_userdev_exit_scheduled"]
    if phases != expected_phases:
        raise E2EError(
            "Dedicated-server probe lifecycle changed: "
            f"expected={expected_phases}, actual={phases}"
        )
    positions: list[int] = []
    for token in SERVER_LOG_TOKENS:
        if text.count(token) != 1:
            raise E2EError(f"Dedicated-server lifecycle token count changed: {token}")
        positions.append(text.index(token))
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise E2EError("Dedicated-server lifecycle tokens are out of order")
    for marker in ("Done (", "Stopping server", "Saving worlds", "All dimensions are saved"):
        if marker not in text:
            raise E2EError(f"Dedicated-server normal lifecycle marker is missing: {marker}")
    return content


def validate_world_save(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    level_data = game_directory(configuration, root) / "world" / "level.dat"
    ensure_regular_unlinked_file(level_data, "Dedicated-server saved world level.dat")
    if level_data.stat().st_size <= 0:
        raise E2EError("Dedicated-server saved world level.dat is empty")
    crash_reports = game_directory(configuration, root) / "crash-reports"
    if any(crash_reports.iterdir()):
        raise E2EError("Dedicated-server runtime contains a crash report")
    return level_data


def launcher_result(
    configuration: ResolvedConfiguration,
    copied_server_log: Path,
    watchdog_evidence: PinnedWatchdogEvidence,
    runtime_directory_descriptor: int,
) -> dict[str, object]:
    profile_manifest = configuration.profile_manifest_path
    watchdog_records = verify_pinned_watchdog_evidence(
        watchdog_evidence,
        runtime_directory_descriptor,
    )
    launch_anchor_records = verify_pinned_launch_anchor_evidence(
        watchdog_evidence,
        runtime_directory_descriptor,
    )
    return {
        "schema": 1,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
        "task_path": TASK_PATH,
        "exit_code": 0,
        "timed_out": False,
        "profile_manifest": {
            "relative_path": PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            "size": profile_manifest.stat().st_size,
            "sha256": sha256_file(profile_manifest),
        },
        "server_log": {
            "relative_path": "logs/latest.log",
            "size": copied_server_log.stat().st_size,
            "sha256": sha256_file(copied_server_log),
        },
        "launch_watchdog": watchdog_records,
        "launch_anchor": launch_anchor_records,
    }


def server_memory_handoff_paths(runtime_directory: Path) -> tuple[Path, Path]:
    """Returns the two fixed in-JVM handoff paths in one owned runtime."""

    if (
        not runtime_directory.is_absolute()
        or not runtime_directory.is_dir()
        or runtime_directory.is_symlink()
    ):
        raise E2EError(
            "The dedicated-server memory handoff runtime is missing or linked: "
            f"{runtime_directory}"
        )
    return (
        runtime_directory / MEMORY_HANDOFF_FILE_NAME,
        runtime_directory / MEMORY_ACKNOWLEDGEMENT_FILE_NAME,
    )


def validate_server_java_handoff(
    handoff_path: Path,
    runtime_directory: Path,
    run_token: str,
    launch_process_id: int,
) -> tuple[int, str]:
    """Validates the exact identity and heap record emitted inside the server JVM."""

    if re.fullmatch(r"[0-9a-f]{64}", run_token) is None:
        raise E2EError("The server Java memory handoff run token is malformed")
    if type(launch_process_id) is not int or launch_process_id <= 0:
        raise E2EError("The server Java memory handoff launch PID is invalid")
    expected_handoff, _acknowledgement = server_memory_handoff_paths(
        runtime_directory
    )
    if handoff_path != expected_handoff:
        raise E2EError("The server Java memory handoff path is not owned")
    ensure_no_symlink_components(handoff_path, runtime_directory)
    ensure_regular_unlinked_file(handoff_path, "Server Java memory handoff")
    try:
        handoff_size = handoff_path.stat().st_size
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect the server Java memory handoff: {exception}"
        ) from exception
    if handoff_size <= 0 or handoff_size > MAXIMUM_MEMORY_HANDOFF_SIZE:
        raise E2EError(
            f"The server Java memory handoff has an invalid size: {handoff_size}"
        )
    handoff = load_json_object(handoff_path, "server Java memory handoff")
    if set(handoff) != {
        "schema",
        "run_token",
        "pid",
        "executable",
        "java_feature",
        "maximum_heap_bytes",
        "maximum_heap_arguments",
    }:
        raise E2EError("The server Java memory handoff field inventory changed")
    pid = handoff.get("pid")
    executable = handoff.get("executable")
    if (
        type(handoff.get("schema")) is not int
        or handoff.get("schema") != 1
        or handoff.get("run_token") != run_token
        or type(pid) is not int
        or pid <= 0
        or pid > (1 << 31) - 1
        or pid == launch_process_id
        or pid == os.getpid()
        or not isinstance(executable, str)
        or not Path(executable).is_absolute()
        or Path(executable).name != "java"
        or ".." in Path(executable).parts
        or "\x00" in executable
        or "\n" in executable
        or "\r" in executable
        or len(executable.encode("utf-8")) > 4096
        or type(handoff.get("java_feature")) is not int
        or handoff.get("java_feature") != SERVER_JAVA_FEATURE
        or type(handoff.get("maximum_heap_bytes")) is not int
        or handoff.get("maximum_heap_bytes") != SERVER_MAXIMUM_HEAP_BYTES
    ):
        raise E2EError(
            "The server Java memory handoff identity or heap contract changed"
        )
    maximum_heap_arguments = handoff.get("maximum_heap_arguments")
    if not isinstance(maximum_heap_arguments, list):
        raise E2EError("The server Java maximum-heap argument inventory is invalid")
    try:
        macos_guarded_java.verify_exact_java_heap_arguments(
            maximum_heap_arguments,
            SERVER_MAXIMUM_MEMORY_MB,
            SERVER_MAXIMUM_HEAP_ARGUMENT,
        )
    except macos_guarded_java.GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    return pid, executable


def wait_for_server_java_handoff(
    process: subprocess.Popen[bytes],
    output_path: Path,
    runtime_directory: Path,
    run_token: str,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    run_deadline: float,
    wrapper_guard: WrapperJavaGuard,
    output_descriptor: int | None = None,
) -> macos_guarded_java.OwnedJavaProcess:
    """Binds the actual JavaExec child before the server probe can continue."""

    handoff_path, _acknowledgement_path = server_memory_handoff_paths(
        runtime_directory
    )
    handoff_deadline = min(
        run_deadline,
        time.monotonic() + MEMORY_HANDOFF_TIMEOUT_SECONDS,
    )
    while time.monotonic() < handoff_deadline:
        verify_process_output_bound(output_path, output_descriptor)
        verify_active_launch_runtime_inventory(
            wrapper_guard.runtime.path.parent,
            (wrapper_guard.runtime,),
        )
        handoff_available = handoff_path.exists() or handoff_path.is_symlink()
        verify_wrapper_launch_supervision(
            wrapper_guard,
            sampler,
            "Gradle wrapper JVM",
        )
        verify_exact_java_memory_envelope(
            sampler,
            wrapper_launch_java_targets(wrapper_guard),
        )
        if not handoff_available and wrapper_guard.launch_anchor is None:
            verify_java_process_inventory((wrapper_guard.target,))
        if handoff_available:
            pid, executable = validate_server_java_handoff(
                handoff_path,
                runtime_directory,
                run_token,
                process.pid,
            )
            bind_deadline = min(
                run_deadline,
                time.monotonic() + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
            )
            observed_process_group_id = -1
            observed_session_id = -1
            while time.monotonic() < bind_deadline:
                if poll_gradle_child_exit_code(process, wrapper_guard) is not None:
                    raise E2EError(
                        "The Gradle launch exited before the server Java identity "
                        "could be bound"
                    )
                verify_wrapper_launch_supervision(
                    wrapper_guard,
                    sampler,
                    "Gradle wrapper JVM",
                )
                verify_exact_java_memory_envelope(
                    sampler,
                    wrapper_launch_java_targets(wrapper_guard),
                )
                if wrapper_guard.launch_anchor is None:
                    verify_java_process_inventory(
                        (wrapper_guard.target,),
                        (pid,),
                    )
                try:
                    observed_process_group_id = os.getpgid(pid)
                    observed_session_id = os.getsid(pid)
                except ProcessLookupError:
                    observed_process_group_id = -1
                    observed_session_id = -1
                except OSError as exception:
                    raise E2EError(
                        "Cannot inspect the server Java process group and session"
                    ) from exception
                if observed_process_group_id <= 0 or observed_session_id <= 0:
                    time.sleep(0.01)
                    continue
                target = bind_exact_java_identity(
                    sampler,
                    pid,
                    observed_process_group_id,
                    executable,
                )
                if target is not None:
                    if (
                        observed_process_group_id != process.pid
                        or observed_session_id != process.pid
                    ):
                        raise DetachedJavaLaunchError(
                            "The server Java process is outside the owned Gradle "
                            "launch process group or session",
                            DetachedJavaObservation(
                                target=target,
                                process_group_id=observed_process_group_id,
                                session_id=observed_session_id,
                            ),
                        )
                    verify_exact_java_memory_envelope(
                        sampler,
                        wrapper_launch_java_targets(wrapper_guard, target),
                    )
                    if wrapper_guard.launch_anchor is None:
                        verify_java_process_inventory(
                            (wrapper_guard.target, target),
                        )
                    return target
                time.sleep(0.01)
            if (
                observed_process_group_id > 0
                and observed_session_id > 0
                and (
                    observed_process_group_id != process.pid
                    or observed_session_id != process.pid
                )
            ):
                raise DetachedJavaLaunchError(
                    "The detached server Java handoff could not be bound exactly",
                    DetachedJavaObservation(
                        target=None,
                        process_group_id=observed_process_group_id,
                        session_id=observed_session_id,
                    ),
                )
            raise E2EError(
                "The server Java handoff did not expose an authoritative exact identity"
            )
        if poll_gradle_child_exit_code(process, wrapper_guard) is not None:
            raise E2EError(
                "The Gradle launch exited before the actual server JVM handoff"
            )
        time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
    raise E2EError("Timed out waiting for the actual server JVM memory handoff")


def verify_gradle_launch_anchor_guard(
    launch: GradleLaunchAnchor,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
) -> None:
    """Requires the exact broker and its independent watchdog before release."""

    target = launch.target
    watchdog = launch.launch_watchdog
    if target is None or watchdog is None:
        raise E2EError("The Gradle launch anchor is not fully supervised")
    if (
        launch.handle.process.poll() is not None
        or watchdog.anchor != target
        or watchdog.session_id != launch.handle.anchor_pid
    ):
        raise E2EError("The Gradle launch anchor identity or watchdog changed")
    try:
        launch.handle.verify_pinned_artifacts()
        forge_server_launch_watchdog.send_launch_watchdog_heartbeat(watchdog)
        forge_server_launch_watchdog.verify_launch_watchdog(watchdog)
    except (
        forge_server_launch_anchor.LaunchAnchorError,
        forge_server_launch_watchdog.LaunchWatchdogError,
    ) as exception:
        raise E2EError(
            f"The Gradle launch anchor lost persistent supervision: {exception}"
        ) from exception
    verify_exact_java_memory_envelope(sampler, (target,))


def prepare_gradle_launch_anchor(
    configuration: ResolvedConfiguration,
    java_path: Path,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    state_root: Path,
    run_deadline: float,
    output_handle: BinaryIO,
    environment: dict[str, str],
    *,
    task_path: str = TASK_PATH,
    watchdog_runtime_directory: Path | None = None,
    watchdog_runtime_directory_descriptor: int | None = None,
) -> GradleLaunchAnchor:
    """Starts, binds, and watches a capped broker without releasing Gradle."""

    runtime = create_owned_runtime_directory(
        state_root,
        GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
        GRADLE_WRAPPER_GUARD_COMPLETED_RUNTIME_PREFIX,
    )
    handle: forge_server_launch_anchor.LaunchAnchorHandle | None = None
    target: macos_guarded_java.OwnedJavaProcess | None = None
    launch_watchdog: forge_server_launch_watchdog.LaunchWatchdogHandle | None = None
    watchdog_holder: dict[
        str,
        forge_server_launch_watchdog.LaunchWatchdogHandle,
    ] = {}
    target_holder: dict[str, macos_guarded_java.OwnedJavaProcess] = {}

    if watchdog_runtime_directory is None:
        if watchdog_runtime_directory_descriptor is not None:
            close_owned_runtime_directory(runtime)
            raise E2EError(
                "A watchdog runtime descriptor has no explicit runtime path"
            )
        selected_runtime = runtime.path
        selected_runtime_descriptor = runtime.descriptor
    else:
        if watchdog_runtime_directory_descriptor is None:
            close_owned_runtime_directory(runtime)
            raise E2EError(
                "The explicit watchdog runtime has no owned directory descriptor"
            )
        selected_runtime = watchdog_runtime_directory
        selected_runtime_descriptor = watchdog_runtime_directory_descriptor

    def child_release_guard(
        guarded_handle: forge_server_launch_anchor.LaunchAnchorHandle,
    ) -> None:
        selected_watchdog = watchdog_holder.get("watchdog")
        selected_target = target_holder.get("target")
        if (
            handle is None
            or guarded_handle is not handle
            or selected_watchdog is None
            or selected_target is None
        ):
            raise forge_server_launch_anchor.LaunchAnchorError(
                "Persistent launch supervision is not bound to the broker"
            )
        guarded_launch = GradleLaunchAnchor(
            guarded_handle,
            selected_target,
            runtime,
            selected_watchdog,
        )
        try:
            verify_gradle_launch_anchor_guard(guarded_launch, sampler)
        except E2EError as exception:
            raise forge_server_launch_anchor.LaunchAnchorError(str(exception)) from exception

    try:
        if run_deadline <= time.monotonic():
            raise E2EError("The Gradle launch deadline elapsed before anchor spawn")
        _controller_source, java_source = verify_launch_anchor_sources(
            configuration.repository_root
        )
        verify_launch_watchdog_source(configuration.repository_root)
        gradle_arguments = build_gradle_arguments(task_path)
        expected_command = build_gradle_command(
            configuration,
            java_path,
            task_path=task_path,
        )
        handle = forge_server_launch_anchor.start_launch_anchor(
            java_path.resolve(),
            21,
            java_source,
            LAUNCH_ANCHOR_JAVA_SOURCE_SIZE,
            LAUNCH_ANCHOR_JAVA_SOURCE_SHA256,
            selected_runtime,
            configuration.repository_root,
            (configuration.repository_root / GRADLE_WRAPPER_JAR_RELATIVE_PATH).resolve(),
            GRADLE_WRAPPER_JAR_SIZE,
            GRADLE_WRAPPER_JAR_SHA256,
            (
                configuration.repository_root
                / GRADLE_WRAPPER_PROPERTIES_RELATIVE_PATH
            ).resolve(),
            GRADLE_WRAPPER_PROPERTIES_SIZE,
            GRADLE_WRAPPER_PROPERTIES_SHA256,
            gradle_arguments,
            child_release_guard,
            environment=environment,
            stdin=subprocess.DEVNULL,
            stdout=output_handle,
            stderr=subprocess.STDOUT,
            readiness_timeout_seconds=min(
                forge_server_launch_anchor.DEFAULT_READINESS_TIMEOUT_SECONDS,
                run_deadline - time.monotonic(),
            ),
        )
        if (
            tuple(expected_command[:5]) != handle.child_command[:5]
            or tuple(expected_command[6:]) != handle.child_command[6:]
            or handle.child_command[5] != str(handle.staged_wrapper_jar.path)
        ):
            raise E2EError("The staged Gradle child differs from its logical command")
        verify_launch_anchor_runtime_identity(handle, selected_runtime_descriptor)
        bind_deadline = min(
            run_deadline,
            time.monotonic() + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
        )
        while time.monotonic() < bind_deadline:
            verify_java_process_inventory(
                allowed_unbound_process_ids=(handle.anchor_pid,),
            )
            if handle.process.poll() is not None:
                raise E2EError("The launch anchor exited before exact identity binding")
            target = bind_exact_java_identity(
                sampler,
                handle.anchor_pid,
                handle.anchor_pid,
                str(java_path.resolve()),
            )
            if target is not None:
                break
            time.sleep(0.01)
        if target is None:
            raise E2EError("The launch anchor did not expose an exact Java identity")
        verify_exact_java_memory_envelope(sampler, (target,))
        verify_java_process_inventory((target,))
        try:
            launch_watchdog = forge_server_launch_watchdog.start_launch_watchdog(
                target,
                handle.anchor_pid,
                selected_runtime,
                heartbeat_timeout_seconds=(
                    LAUNCH_WATCHDOG_HEARTBEAT_TIMEOUT_SECONDS
                ),
            )
        except forge_server_launch_watchdog.LaunchWatchdogStartError as exception:
            launch_watchdog = exception.handle
            raise
        verify_launch_watchdog_runtime_identity(
            launch_watchdog,
            selected_runtime_descriptor,
        )
        watchdog_holder["watchdog"] = launch_watchdog
        target_holder["target"] = target
        launch = GradleLaunchAnchor(
            handle,
            target,
            runtime,
            launch_watchdog,
        )
        verify_gradle_launch_anchor_guard(launch, sampler)
        verify_java_process_inventory((target,))
        return launch
    except forge_server_launch_anchor.LaunchAnchorStartError as exception:
        handle = exception.handle
        partial = GradleLaunchAnchor(
            handle,
            target,
            runtime,
            launch_watchdog,
        )
        raise GradleLaunchAnchorStartError(str(exception), partial) from exception
    except BaseException as exception:
        if handle is None:
            try:
                retire_owned_runtime_directory(runtime)
            finally:
                close_owned_runtime_directory(runtime)
            raise
        partial = GradleLaunchAnchor(
            handle,
            target,
            runtime,
            launch_watchdog,
        )
        raise GradleLaunchAnchorStartError(
            f"The stable Gradle launch anchor failed: {exception}",
            partial,
        ) from exception


def start_anchored_wrapper_java_guard(
    launch: GradleLaunchAnchor,
    java_path: Path,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    run_deadline: float,
    output_handle: BinaryIO,
) -> WrapperJavaGuard:
    """Releases, binds, and monitors the direct wrapper under a live broker."""

    anchor_target = launch.target
    launch_watchdog = launch.launch_watchdog
    if anchor_target is None or launch_watchdog is None:
        raise E2EError("The direct Gradle child cannot start without its anchor guards")
    target: macos_guarded_java.OwnedJavaProcess | None = None
    monitor: macos_guarded_java.GuardedJavaMonitor | None = None
    spawned_monitor_process: subprocess.Popen[bytes] | None = None

    def capture_monitor_process(process_handle: subprocess.Popen[bytes]) -> None:
        nonlocal spawned_monitor_process
        spawned_monitor_process = process_handle

    try:
        verify_gradle_launch_anchor_guard(launch, sampler)
        verify_java_process_inventory((anchor_target,))
        launch.handle.start_child()
        verify_gradle_launch_anchor_guard(launch, sampler)
        child_deadline = min(
            run_deadline,
            time.monotonic()
            + LAUNCH_ANCHOR_CHILD_ACKNOWLEDGEMENT_TIMEOUT_SECONDS,
        )
        child_started: forge_server_launch_anchor.ChildStarted | None = None
        while time.monotonic() < child_deadline:
            child_started = launch.handle.poll_child_started()
            verify_gradle_launch_anchor_guard(launch, sampler)
            if child_started is not None:
                break
            time.sleep(forge_server_launch_anchor.POLL_INTERVAL_SECONDS)
        if child_started is None:
            raise E2EError("Timed out waiting for the direct Gradle child identity")
        try:
            process_group_id = os.getpgid(child_started.pid)
            session_id = os.getsid(child_started.pid)
        except OSError as exception:
            raise E2EError("Cannot inspect the direct Gradle child topology") from exception
        if (
            child_started.executable != str(java_path.resolve())
            or process_group_id != launch.handle.anchor_pid
            or session_id != launch.handle.anchor_pid
        ):
            raise E2EError("The direct Gradle child escaped its anchor group or session")
        bind_deadline = min(
            run_deadline,
            time.monotonic() + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
        )
        while time.monotonic() < bind_deadline:
            verify_gradle_launch_anchor_guard(launch, sampler)
            target = bind_exact_java_identity(
                sampler,
                child_started.pid,
                launch.handle.anchor_pid,
                child_started.executable,
            )
            if target is not None:
                break
            time.sleep(0.01)
        if target is None:
            raise E2EError("The direct Gradle child could not be bound exactly")
        verify_exact_java_memory_envelope(sampler, (anchor_target, target))
        verify_gradle_launch_anchor_guard(launch, sampler)
        monitor = macos_guarded_java.start_guarded_java_monitor(
            target,
            SERVER_MAXIMUM_MEMORY_MB,
            launch.runtime.path,
            output_handle,
            policy_name=STRICT_MEMORY_POLICY_NAME,
            process_started=capture_monitor_process,
        )
        guard = WrapperJavaGuard(
            target=target,
            monitor=monitor,
            spawned_monitor_process=spawned_monitor_process,
            runtime=launch.runtime,
            launch_watchdog=launch_watchdog,
            launch_anchor=launch.handle,
            anchor_target=anchor_target,
        )
        verify_wrapper_launch_supervision(
            guard,
            sampler,
            "Gradle wrapper JVM",
        )
        verify_exact_java_memory_envelope(sampler, (anchor_target, target))
        return guard
    except BaseException as exception:
        if target is None:
            raise
        partial_guard = WrapperJavaGuard(
            target=target,
            monitor=monitor,
            spawned_monitor_process=spawned_monitor_process,
            runtime=launch.runtime,
            launch_watchdog=launch_watchdog,
            launch_anchor=launch.handle,
            anchor_target=anchor_target,
        )
        raise WrapperGuardStartError(
            f"The anchored Gradle wrapper guard failed: {exception}",
            partial_guard,
        ) from exception


def start_wrapper_java_guard(
    process: subprocess.Popen[bytes],
    java_path: Path,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    state_root: Path,
    run_deadline: float,
    output_handle: BinaryIO,
    watchdog_runtime_directory: Path | None = None,
    watchdog_runtime_directory_descriptor: int | None = None,
) -> WrapperJavaGuard:
    """Binds and persistently monitors one isolated Gradle wrapper JVM."""

    runtime = create_owned_runtime_directory(
        state_root,
        GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
        GRADLE_WRAPPER_GUARD_COMPLETED_RUNTIME_PREFIX,
    )
    bind_deadline = min(
        run_deadline,
        time.monotonic() + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
    )
    target: macos_guarded_java.OwnedJavaProcess | None = None
    runtime_directory = runtime.path
    monitor: macos_guarded_java.GuardedJavaMonitor | None = None
    spawned_monitor_process: subprocess.Popen[bytes] | None = None
    launch_watchdog: (
        forge_server_launch_watchdog.LaunchWatchdogHandle | None
    ) = None

    def capture_monitor_process(process_handle: subprocess.Popen[bytes]) -> None:
        nonlocal spawned_monitor_process
        spawned_monitor_process = process_handle

    try:
        while time.monotonic() < bind_deadline:
            verify_java_process_inventory(allowed_unbound_process_ids=(process.pid,))
            if process.poll() is not None:
                raise E2EError(
                    "The Gradle wrapper exited before its Java identity could be bound"
                )
            try:
                process_group_id = os.getpgid(process.pid)
                session_id = os.getsid(process.pid)
            except ProcessLookupError:
                process_group_id = -1
                session_id = -1
            except OSError as exception:
                raise E2EError(
                    "Cannot inspect the Gradle wrapper process group and session"
                ) from exception
            if process_group_id == process.pid and session_id == process.pid:
                target = bind_exact_java_identity(
                    sampler,
                    process.pid,
                    process.pid,
                    str(java_path),
                )
                if target is not None:
                    break
            time.sleep(0.01)
        if target is None:
            raise E2EError(
                "The Gradle wrapper did not expose an authoritative exact Java identity"
            )
        if watchdog_runtime_directory is None:
            if watchdog_runtime_directory_descriptor is not None:
                raise E2EError(
                    "A watchdog runtime descriptor has no explicit runtime path"
                )
            selected_watchdog_runtime = runtime_directory
            selected_watchdog_runtime_descriptor = runtime.descriptor
        else:
            if watchdog_runtime_directory_descriptor is None:
                raise E2EError(
                    "The explicit watchdog runtime has no owned directory descriptor"
                )
            selected_watchdog_runtime = watchdog_runtime_directory
            selected_watchdog_runtime_descriptor = (
                watchdog_runtime_directory_descriptor
            )
        try:
            verify_launch_watchdog_source()
            launch_watchdog = forge_server_launch_watchdog.start_launch_watchdog(
                target,
                process.pid,
                selected_watchdog_runtime,
                heartbeat_timeout_seconds=(
                    LAUNCH_WATCHDOG_HEARTBEAT_TIMEOUT_SECONDS
                ),
            )
        except forge_server_launch_watchdog.LaunchWatchdogStartError as exception:
            launch_watchdog = exception.handle
            raise
        verify_launch_watchdog_runtime_identity(
            launch_watchdog,
            selected_watchdog_runtime_descriptor,
        )
        verify_exact_java_memory_envelope(sampler, (target,))
        verify_java_process_inventory((target,))
        monitor = macos_guarded_java.start_guarded_java_monitor(
            target,
            SERVER_MAXIMUM_MEMORY_MB,
            runtime_directory,
            output_handle,
            policy_name=STRICT_MEMORY_POLICY_NAME,
            process_started=capture_monitor_process,
        )
        guard = WrapperJavaGuard(
            target=target,
            monitor=monitor,
            spawned_monitor_process=spawned_monitor_process,
            runtime=runtime,
            launch_watchdog=launch_watchdog,
        )
        verify_wrapper_launch_supervision(
            guard,
            sampler,
            "Gradle wrapper JVM",
        )
        verify_exact_java_memory_envelope(sampler, (target,))
        verify_java_process_inventory((target,))
        return guard
    except BaseException as exception:
        if target is None:
            retirement_error: BaseException | None = None
            try:
                retire_owned_runtime_directory(runtime)
            except BaseException as caught_exception:
                retirement_error = caught_exception
            finally:
                close_owned_runtime_directory(runtime)
            if retirement_error is not None:
                raise CleanupUncertainError(
                    "The unbound Gradle wrapper guard runtime could not be preserved"
                ) from retirement_error
            raise
        partial_guard = WrapperJavaGuard(
            target=target,
            monitor=monitor,
            spawned_monitor_process=spawned_monitor_process,
            runtime=runtime,
            launch_watchdog=launch_watchdog,
        )
        raise WrapperGuardStartError(
            f"The strict Gradle wrapper memory guard failed: {exception}",
            partial_guard,
        ) from exception


def start_server_java_guard(
    process: subprocess.Popen[bytes],
    output_path: Path,
    runtime_directory: Path,
    run_token: str,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    run_deadline: float,
    working_directory: Path,
    output_handle: BinaryIO,
    wrapper_guard: WrapperJavaGuard,
    caffeinate_path: Path = CAFFEINATE_PATH,
    *,
    output_descriptor: int | None = None,
) -> ServerJavaGuard:
    """Starts monitoring and then releases the blocked in-JVM probe constructor."""

    handoff_path, acknowledgement_path = server_memory_handoff_paths(
        runtime_directory
    )
    target = wait_for_server_java_handoff(
        process,
        output_path,
        runtime_directory,
        run_token,
        sampler,
        run_deadline,
        wrapper_guard,
        output_descriptor,
    )
    monitor: macos_guarded_java.GuardedJavaMonitor | None = None
    spawned_monitor_process: subprocess.Popen[bytes] | None = None
    caffeinate_process: subprocess.Popen[bytes] | None = None

    def capture_monitor_process(process_handle: subprocess.Popen[bytes]) -> None:
        nonlocal spawned_monitor_process
        spawned_monitor_process = process_handle

    try:
        ensure_regular_unlinked_file(caffeinate_path, "macOS caffeinate")
        monitor = macos_guarded_java.start_guarded_java_monitor(
            target,
            SERVER_MAXIMUM_MEMORY_MB,
            runtime_directory,
            output_handle,
            policy_name=STRICT_MEMORY_POLICY_NAME,
            process_started=capture_monitor_process,
        )
        caffeinate_process = subprocess.Popen(
            [str(caffeinate_path), "-dimsu", "-w", str(target.pid)],
            cwd=working_directory,
            stdin=subprocess.DEVNULL,
            stdout=output_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        if poll_gradle_child_exit_code(process, wrapper_guard) is not None:
            raise E2EError(
                "The Gradle launch exited before the guarded server handoff completed"
            )
        if monitor.process.poll() is not None:
            raise E2EError(
                "The server Java memory guard exited before handoff completed"
            )
        if caffeinate_process.poll() is not None:
            raise E2EError("macOS caffeinate exited before server handoff completed")
        verify_wrapper_launch_supervision(
            wrapper_guard,
            sampler,
            "Gradle wrapper JVM",
        )
        verify_java_guard_is_enforcing(
            target,
            monitor,
            sampler,
            "server JVM",
        )
        verify_exact_java_memory_envelope(
            sampler,
            wrapper_launch_java_targets(wrapper_guard, target),
        )
        if wrapper_guard.launch_anchor is None:
            verify_java_process_inventory((wrapper_guard.target, target))
        write_bytes_exclusive(
            acknowledgement_path,
            f"token={run_token}\n".encode("ascii"),
        )
        return ServerJavaGuard(
            target=target,
            monitor=monitor,
            caffeinate_process=caffeinate_process,
            handoff_path=handoff_path,
            acknowledgement_path=acknowledgement_path,
            spawned_monitor_process=spawned_monitor_process,
        )
    except BaseException as exception:
        partial_guard = ServerJavaGuard(
            target=target,
            monitor=monitor,
            caffeinate_process=caffeinate_process,
            handoff_path=handoff_path,
            acknowledgement_path=acknowledgement_path,
            spawned_monitor_process=spawned_monitor_process,
        )
        raise ServerGuardStartError(
            f"The guarded server Java handoff failed: {exception}",
            partial_guard,
        ) from exception


def verify_process_output_bound(
    output_path: Path,
    output_descriptor: int | None = None,
) -> None:
    """Bounds the exact held output inode and rejects pathname replacement."""

    descriptor = output_descriptor
    close_descriptor = False
    try:
        if descriptor is None:
            flags = os.O_RDONLY
            if hasattr(os, "O_CLOEXEC"):
                flags |= os.O_CLOEXEC
            if hasattr(os, "O_NOFOLLOW"):
                flags |= os.O_NOFOLLOW
            descriptor = os.open(output_path, flags)
            close_descriptor = True
        descriptor_metadata = os.fstat(descriptor)
        path_metadata = os.lstat(output_path)
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect named Forge server probe output: {exception}"
        ) from exception
    finally:
        if close_descriptor and descriptor is not None:
            os.close(descriptor)
    if (
        not stat.S_ISREG(descriptor_metadata.st_mode)
        or (
            output_descriptor is not None
            and stat.S_IMODE(descriptor_metadata.st_mode) != 0o600
        )
        or descriptor_metadata.st_nlink != 1
        or descriptor_metadata.st_uid != os.getuid()
        or not stat.S_ISREG(path_metadata.st_mode)
        or path_metadata.st_dev != descriptor_metadata.st_dev
        or path_metadata.st_ino != descriptor_metadata.st_ino
    ):
        raise CleanupUncertainError(
            "The named Forge server probe output inode changed during execution"
        )
    if descriptor_metadata.st_size > MAXIMUM_PROCESS_LOG_SIZE:
        raise E2EError(
            "Named Forge server probe output exceeded "
            f"{MAXIMUM_PROCESS_LOG_SIZE} bytes during execution"
        )


def verify_owned_gradle_process_group(process: subprocess.Popen[bytes]) -> None:
    """Requires the just-spawned Gradle wrapper to lead an isolated PGID."""

    if process.poll() is not None:
        raise E2EError(
            "The Gradle launch exited before its dedicated PGID was verified"
        )
    try:
        process_group_id = os.getpgid(process.pid)
        session_id = os.getsid(process.pid)
    except ProcessLookupError as exception:
        raise E2EError(
            "The Gradle launch disappeared before its dedicated PGID was verified"
        ) from exception
    except OSError as exception:
        raise E2EError(
            "Cannot inspect the Gradle launch process group and session"
        ) from exception
    if (
        process.pid == os.getpid()
        or process_group_id != process.pid
        or session_id != process.pid
        or process_group_id == os.getpgrp()
    ):
        raise E2EError(
            "The Gradle launch does not own an isolated dedicated process group"
        )


def stop_process_group(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        if process_group_exists(process.pid):
            raise E2EError(
                "The Gradle leader exited while its owned process group remained"
            )
        return
    try:
        if (
            os.getpgid(process.pid) != process.pid
            or os.getsid(process.pid) != process.pid
            or process.pid == os.getpgrp()
        ):
            raise E2EError(
                "The Gradle launch no longer owns its dedicated process group "
                "and session"
            )
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        if process_group_exists(process.pid):
            raise E2EError("The owned Gradle process group identity is uncertain")
        process.poll()
        if process.poll() is None:
            raise CleanupUncertainError(
                "The direct Gradle child remained unreaped after its group vanished"
            )
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        if (
            os.getpgid(process.pid) != process.pid
            or os.getsid(process.pid) != process.pid
            or process.pid == os.getpgrp()
        ):
            raise E2EError(
                "The Gradle launch lost its dedicated process group or session "
                "before escalation"
            )
    else:
        if wait_for_process_group_absence(
            process.pid,
            PROCESS_STOP_TIMEOUT_SECONDS,
        ):
            return
        raise E2EError(
            "The Gradle leader exited while its owned process group remained"
        )
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        if process_group_exists(process.pid):
            raise E2EError("The owned Gradle process group identity is uncertain")
        process.poll()
        if process.poll() is None:
            raise CleanupUncertainError(
                "The direct Gradle child remained unreaped after escalation"
            )
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as exception:
        raise E2EError("The timed-out Gradle process group did not stop") from exception
    if not wait_for_process_group_absence(
        process.pid,
        PROCESS_STOP_TIMEOUT_SECONDS,
    ):
        raise E2EError("The killed Gradle process group did not disappear")


def stop_owned_launch_group(
    process: subprocess.Popen[bytes],
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    exact_members: tuple[macos_guarded_java.OwnedJavaProcess, ...],
) -> None:
    """Stops a launch group through its live leader or one exact live member."""

    if process.poll() is None:
        stop_process_group(process)
        return
    if not process_group_exists(process.pid):
        return
    for target in dict.fromkeys(exact_members):
        if (
            target.process_group_id == process.pid
            and sampler.revalidate(target) == target
        ):
            try:
                macos_guarded_java.stop_owned_java_process(
                    target,
                    owned_process_group_id=process.pid,
                    timeout_seconds=PROCESS_STOP_TIMEOUT_SECONDS,
                    sampler=sampler,
                )
            except (
                macos_guarded_java.GuardedJavaError,
                macos_guarded_java.MemorySamplingError,
                OSError,
            ) as exception:
                raise CleanupUncertainError(
                    "The exact Java member could not stop the owned launch group"
                ) from exception
            return
    raise CleanupUncertainError(
        "The Gradle leader exited while its launch group remained without an "
        "exact live Java anchor"
    )


def stop_exact_java_process(
    target: macos_guarded_java.OwnedJavaProcess,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    timeout_seconds: float = PROCESS_STOP_TIMEOUT_SECONDS,
) -> bool:
    """Stops one exact Java PID without signaling an unowned process group."""

    if timeout_seconds < 0 or target.pid == os.getpid():
        raise CleanupUncertainError("The exact detached Java stop request is unsafe")
    sample = sampler.sample(target, time.monotonic_ns())
    if sample.status is macos_guarded_java.SampleStatus.MISSING:
        return False
    if (
        sample.status is not macos_guarded_java.SampleStatus.AVAILABLE
        or sample.observed_identity != target
        or sampler.revalidate(target) != target
    ):
        raise CleanupUncertainError(
            "The detached Java identity cannot be revalidated before SIGTERM"
        )
    try:
        os.kill(target.pid, signal.SIGTERM)
    except ProcessLookupError:
        if (
            sampler.sample(target, time.monotonic_ns()).status
            is macos_guarded_java.SampleStatus.MISSING
        ):
            return False
        raise CleanupUncertainError(
            "The detached Java PID vanished without exact absence proof"
        )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if status is macos_guarded_java.SampleStatus.MISSING:
            return False
        if status is not macos_guarded_java.SampleStatus.AVAILABLE:
            raise CleanupUncertainError(
                "The detached Java identity became uncertain after SIGTERM"
            )
        time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
    if sampler.revalidate(target) != target:
        if (
            sampler.sample(target, time.monotonic_ns()).status
            is macos_guarded_java.SampleStatus.MISSING
        ):
            return False
        raise CleanupUncertainError(
            "The detached Java identity changed before SIGKILL"
        )
    try:
        os.kill(target.pid, signal.SIGKILL)
    except ProcessLookupError:
        if (
            sampler.sample(target, time.monotonic_ns()).status
            is macos_guarded_java.SampleStatus.MISSING
        ):
            return True
        raise CleanupUncertainError(
            "The detached Java PID vanished without post-escalation proof"
        )
    kill_deadline = time.monotonic() + 2.0
    while time.monotonic() < kill_deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if status is macos_guarded_java.SampleStatus.MISSING:
            return True
        if status is not macos_guarded_java.SampleStatus.AVAILABLE:
            raise CleanupUncertainError(
                "The detached Java identity became uncertain after SIGKILL"
            )
        time.sleep(0.05)
    raise CleanupUncertainError("The detached Java PID remained live after SIGKILL")


def stop_detached_java_observations(
    observations: tuple[DetachedJavaObservation, ...],
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
) -> None:
    """Actively stops every exact Java identity rejected from launch topology."""

    targets: list[macos_guarded_java.OwnedJavaProcess] = []
    for observation in observations:
        target = observation.target
        if (
            target is None
            or observation.process_group_id <= 0
            or observation.session_id <= 0
            or target.process_group_id != observation.process_group_id
        ):
            raise CleanupUncertainError(
                "A detached Java observation has no exact stoppable identity"
            )
        if target not in targets:
            targets.append(target)
    for target in targets:
        sample = sampler.sample(target, time.monotonic_ns())
        if sample.status is macos_guarded_java.SampleStatus.MISSING:
            continue
        if sample.status is not macos_guarded_java.SampleStatus.AVAILABLE:
            raise CleanupUncertainError(
                "A detached Java identity is uncertain before active cleanup"
            )
        matching_observation = next(
            observation
            for observation in observations
            if observation.target == target
        )
        try:
            process_group_id = os.getpgid(target.pid)
            session_id = os.getsid(target.pid)
        except ProcessLookupError:
            if (
                sampler.sample(target, time.monotonic_ns()).status
                is macos_guarded_java.SampleStatus.MISSING
            ):
                continue
            raise CleanupUncertainError(
                "A detached Java PID vanished without absence proof"
            )
        except OSError as exception:
            raise CleanupUncertainError(
                "Cannot inspect a detached Java group and session before stopping"
            ) from exception
        if (
            process_group_id != matching_observation.process_group_id
            or session_id != matching_observation.session_id
        ):
            raise CleanupUncertainError(
                "A detached Java group or session changed before stopping"
            )
        if target.pid == process_group_id and target.pid == session_id:
            try:
                macos_guarded_java.stop_owned_java_process(
                    target,
                    owned_process_group_id=process_group_id,
                    timeout_seconds=PROCESS_STOP_TIMEOUT_SECONDS,
                    sampler=sampler,
                )
            except (
                macos_guarded_java.GuardedJavaError,
                macos_guarded_java.MemorySamplingError,
                OSError,
            ) as exception:
                raise CleanupUncertainError(
                    "The detached Java dedicated group could not be stopped"
                ) from exception
        else:
            stop_exact_java_process(target, sampler)


def stop_direct_spawned_process(process: subprocess.Popen[bytes]) -> None:
    """Stops only the unreaped direct child when no exact group owner was bound."""

    if process.poll() is not None:
        return
    try:
        process.terminate()
    except ProcessLookupError:
        process.poll()
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
        return
    except subprocess.TimeoutExpired:
        pass
    try:
        process.kill()
    except ProcessLookupError:
        process.poll()
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as exception:
        raise CleanupUncertainError(
            "The unbound direct Gradle child did not stop"
        ) from exception


def process_group_exists(process_group_id: int) -> bool:
    """Checks one positive PGID without signaling any member."""

    if type(process_group_id) is not int or process_group_id <= 0:
        raise E2EError("The owned Gradle process group ID is invalid")
    try:
        os.killpg(process_group_id, 0)
    except ProcessLookupError:
        return False
    except PermissionError as exception:
        raise E2EError(
            "Cannot inspect the owned Gradle process group"
        ) from exception
    return True


def read_process_group_inventory(
    process_group_id: int,
    ps_path: Path = PS_PATH,
) -> tuple[int, ...]:
    """Returns every process currently attached to one positive macOS PGID."""

    if type(process_group_id) is not int or process_group_id <= 0:
        raise E2EError("The owned process-group inventory ID is invalid")
    try:
        metadata = ps_path.lstat()
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect the process-group inventory tool: {exception}"
        ) from exception
    if (
        not ps_path.is_absolute()
        or not stat.S_ISREG(metadata.st_mode)
        or ps_path.is_symlink()
        or metadata.st_uid != 0
        or metadata.st_mode & 0o022
        or not os.access(ps_path, os.X_OK)
    ):
        raise E2EError(f"The process-group inventory tool is unsafe: {ps_path}")
    try:
        completed = subprocess.run(
            [str(ps_path), "-axo", "pid=,pgid="],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env={"LANG": "C", "LC_ALL": "C", "PATH": "/usr/bin:/bin"},
            timeout=PROCESS_GROUP_INVENTORY_TIMEOUT_SECONDS,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exception:
        raise E2EError(
            f"Cannot inventory the owned process group: {exception}"
        ) from exception
    output = completed.stdout
    error_output = completed.stderr
    if not isinstance(output, bytes) or not isinstance(error_output, bytes):
        raise E2EError("The process-group inventory returned a non-binary payload")
    if (
        len(output) > MAXIMUM_PROCESS_GROUP_INVENTORY_SIZE
        or len(error_output) > MAXIMUM_PROCESS_GROUP_INVENTORY_SIZE
    ):
        raise E2EError("The process-group inventory exceeded its byte bound")
    if completed.returncode != 0 or error_output:
        raise E2EError("The process-group inventory command returned an invalid result")
    process_groups: dict[int, int] = {}
    for line in output.splitlines():
        match = re.fullmatch(rb"\s*([1-9][0-9]*)\s+([1-9][0-9]*)\s*", line)
        if match is None:
            raise E2EError("The process-group inventory contains malformed output")
        process_id = int(match.group(1))
        observed_group_id = int(match.group(2))
        if (
            process_id > (1 << 31) - 1
            or observed_group_id > (1 << 31) - 1
            or process_id in process_groups
        ):
            raise E2EError("The process-group inventory contains an invalid identity")
        process_groups[process_id] = observed_group_id
    return tuple(
        sorted(
            process_id
            for process_id, observed_group_id in process_groups.items()
            if observed_group_id == process_group_id
        )
    )


def verify_launch_group_contains_only_anchor(
    launch: GradleLaunchAnchor,
) -> None:
    """Proves no Java or native child survives before releasing the broker."""

    target = launch.target
    if target is None or launch.process is not launch.handle.process:
        raise CleanupUncertainError("The launch anchor identity is incomplete")
    verify_owned_gradle_process_group(launch.process)
    members = read_process_group_inventory(launch.handle.anchor_pid)
    if members != (launch.handle.anchor_pid,):
        raise CleanupUncertainError(
            "The owned launch group was not quiescent before anchor release"
        )
    verify_owned_gradle_process_group(launch.process)


def wait_for_process_group_absence(
    process_group_id: int,
    timeout_seconds: float,
) -> bool:
    """Waits briefly for every member of one already-owned PGID to disappear."""

    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if not process_group_exists(process_group_id):
            return True
        time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
    return not process_group_exists(process_group_id)


def read_process_tail(path: Path) -> str:
    try:
        size = path.stat().st_size
        if size > MAXIMUM_PROCESS_LOG_SIZE:
            return f"process output exceeded {MAXIMUM_PROCESS_LOG_SIZE} bytes"
        with path.open("rb") as handle:
            if size > 8192:
                handle.seek(size - 8192)
            return handle.read().decode("utf-8", errors="replace").strip()
    except OSError as exception:
        return f"cannot read process output: {exception}"


def wait_for_bounded_process(
    process: subprocess.Popen[bytes],
    output_path: Path,
    *,
    run_deadline: float | None = None,
    wrapper_guard: WrapperJavaGuard | None = None,
    server_guard: ServerJavaGuard | None = None,
    optional_java_targets: tuple[
        macos_guarded_java.OwnedJavaProcess, ...
    ] = (),
    sampler: macos_guarded_java.MacOsProcessMemorySampler | None = None,
    additional_runtime_owners: tuple[OwnedRuntimeDirectory, ...] = (),
    output_descriptor: int | None = None,
) -> int:
    deadline = (
        time.monotonic() + RUN_TIMEOUT_SECONDS
        if run_deadline is None
        else run_deadline
    )
    while True:
        verify_process_output_bound(output_path, output_descriptor)
        exit_code = poll_gradle_child_exit_code(process, wrapper_guard)
        if (
            wrapper_guard is not None
            or server_guard is not None
            or optional_java_targets
        ):
            if sampler is None:
                raise E2EError("The guarded Java wait has no native sampler")
        if wrapper_guard is not None:
            verify_active_launch_runtime_inventory(
                wrapper_guard.runtime.path.parent,
                (wrapper_guard.runtime,) + additional_runtime_owners,
            )
            anchored_child_result_pending = (
                wrapper_guard.launch_anchor is not None and exit_code is None
            )
            verify_wrapper_launch_supervision(
                wrapper_guard,
                sampler,
                "Gradle wrapper JVM",
                allow_missing=(
                    exit_code is not None or anchored_child_result_pending
                ),
            )
        if server_guard is not None:
            verify_server_guard_is_enforcing(server_guard, sampler)
        additional_targets = (
            ((server_guard.target,) if server_guard is not None else ())
            + optional_java_targets
        )
        known_targets = (
            wrapper_launch_java_targets(wrapper_guard, *additional_targets)
            if wrapper_guard is not None
            else additional_targets
        )
        if known_targets:
            required_targets = tuple(
                target
                for target in (
                    (
                        wrapper_guard.anchor_target
                        if wrapper_guard is not None
                        else None
                    ),
                    (
                        wrapper_guard.target
                        if (
                            wrapper_guard is not None
                            and exit_code is None
                            and wrapper_guard.launch_anchor is None
                        )
                        else None
                    ),
                )
                if target is not None
            )
            optional_targets = tuple(
                target
                for target in dict.fromkeys(known_targets)
                if target not in required_targets
            )
            verify_exact_java_memory_envelope(
                sampler,
                required_targets,
                optional_targets,
            )
            if wrapper_guard is None or wrapper_guard.launch_anchor is None:
                verify_java_process_inventory(tuple(dict.fromkeys(known_targets)))
        if exit_code is not None:
            verify_process_output_bound(output_path, output_descriptor)
            return exit_code
        remaining_seconds = deadline - time.monotonic()
        if remaining_seconds <= 0:
            raise E2EError(
                "The named Forge server probe timed out after "
                f"{RUN_TIMEOUT_SECONDS} seconds"
            )
        try:
            if wrapper_guard is not None and wrapper_guard.launch_anchor is not None:
                time.sleep(min(PROCESS_POLL_INTERVAL_SECONDS, remaining_seconds))
                continue
            exit_code = process.wait(
                timeout=min(PROCESS_POLL_INTERVAL_SECONDS, remaining_seconds)
            )
        except subprocess.TimeoutExpired:
            continue
        except KeyboardInterrupt as exception:
            raise E2EError("The named Forge server probe was interrupted") from exception
        if exit_code is None:
            raise E2EError("The Gradle launch returned no exit code after waiting")
        continue


def server_guard_state(server_guard: ServerJavaGuard) -> dict[str, object]:
    """Returns the exact state needed for the shared monitor health check."""

    target = server_guard.target
    if server_guard.monitor is None:
        raise E2EError("The server Java guard has no monitor")
    return java_guard_state(target, server_guard.monitor)


def verify_server_guard_is_enforcing(
    server_guard: ServerJavaGuard,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
) -> None:
    """Fails closed if a still-live server loses authoritative monitoring."""

    verify_java_guard_is_enforcing(
        server_guard.target,
        server_guard.monitor,
        sampler,
        "server JVM",
        allow_missing=True,
    )


def require_guarded_server_stopped(
    process: subprocess.Popen[bytes],
    server_guard: ServerJavaGuard,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    wrapper_guard: WrapperJavaGuard | None = None,
) -> None:
    """Requires server and Gradle completion while retaining an anchored PGID."""

    sample = sampler.sample(server_guard.target, time.monotonic_ns())
    if sample.status is not macos_guarded_java.SampleStatus.MISSING:
        raise E2EError(
            "The Gradle task ended without proving the guarded server JVM stopped: "
            f"{sample.status.value}"
        )
    if wrapper_guard is not None and wrapper_guard.launch_anchor is not None:
        if wrapper_guard.anchor_target is None:
            raise E2EError("The completed Gradle launch lost its anchor identity")
        wrapper_sample = sampler.sample(
            wrapper_guard.target,
            time.monotonic_ns(),
        )
        if wrapper_sample.status is not macos_guarded_java.SampleStatus.MISSING:
            raise E2EError(
                "The Gradle task ended without proving its wrapper JVM stopped: "
                f"{wrapper_sample.status.value}"
            )
        exit_code = poll_gradle_child_exit_code(process, wrapper_guard)
        if exit_code is None:
            raise E2EError("The Gradle task has no authenticated child result")
        launch = GradleLaunchAnchor(
            wrapper_guard.launch_anchor,
            wrapper_guard.anchor_target,
            wrapper_guard.runtime,
            wrapper_guard.launch_watchdog,
        )
        verify_gradle_launch_anchor_guard(launch, sampler)
        verify_java_process_inventory((wrapper_guard.anchor_target,))
        verify_launch_group_contains_only_anchor(launch)
        return
    if process_group_exists(process.pid):
        raise E2EError(
            "The Gradle task ended while its dedicated launch PGID remained"
        )


def stop_java_guard_auxiliary(
    monitor: macos_guarded_java.GuardedJavaMonitor | None,
    spawned_monitor_process: subprocess.Popen[bytes] | None,
) -> None:
    """Reaps a complete monitor only after it attests the target's absence."""

    if monitor is not None:
        try:
            exit_code = monitor.process.wait(timeout=5.0)
        except subprocess.TimeoutExpired as exception:
            raise CleanupUncertainError(
                "The Java memory guard did not publish terminal state"
            ) from exception
        if exit_code != 0:
            raise CleanupUncertainError(
                f"The Java memory guard exited with status {exit_code}"
            )
        try:
            telemetry_size = monitor.telemetry_path.stat().st_size
            telemetry = load_json_object(
                monitor.telemetry_path,
                "Java memory guard telemetry",
            )
        except (E2EError, OSError) as exception:
            raise CleanupUncertainError(
                f"Cannot read terminal Java memory telemetry: {exception}"
            ) from exception
        records = telemetry.get("records")
        if (
            telemetry_size <= 0
            or telemetry_size > macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES
            or not isinstance(records, list)
            or not records
            or not isinstance(records[-1], dict)
            or records[-1].get("source") != "proc-pid-rusage-v4"
            or records[-1].get("status") != "missing"
        ):
            raise CleanupUncertainError(
                "The Java memory guard did not attest terminal exact-process absence"
            )
        return
    macos_guarded_java.stop_spawned_auxiliary(spawned_monitor_process)


def cleanup_wrapper_java_guard(
    process: subprocess.Popen[bytes],
    wrapper_guard: WrapperJavaGuard,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    state_root: Path,
    *,
    require_normal_watchdog_exit: bool,
    watchdog_runtime_directory_descriptor: int | None = None,
    retain_terminal_evidence: bool = False,
) -> PinnedWatchdogEvidence | None:
    """Reaps wrapper supervision after exact child and anchor quiescence."""

    sample = sampler.sample(wrapper_guard.target, time.monotonic_ns())
    if sample.status is not macos_guarded_java.SampleStatus.MISSING:
        raise CleanupUncertainError(
            "The exact Gradle wrapper JVM was not proved absent before guard cleanup"
        )
    runtime = wrapper_guard.runtime
    if runtime.path.parent != state_root:
        raise CleanupUncertainError(
            "The Gradle wrapper guard runtime identity is unsafe"
        )
    launch_anchor = wrapper_guard.launch_anchor
    anchor_target = wrapper_guard.anchor_target
    anchored_launch: GradleLaunchAnchor | None = None
    if launch_anchor is None:
        if anchor_target is not None:
            raise CleanupUncertainError(
                "The wrapper retained an anchor identity without its broker"
            )
        if process_group_exists(process.pid):
            raise CleanupUncertainError(
                "The Gradle wrapper process group remained before guard cleanup"
            )
    else:
        if (
            anchor_target is None
            or process is not launch_anchor.process
            or process.pid != launch_anchor.anchor_pid
            or wrapper_guard.launch_watchdog is None
        ):
            raise CleanupUncertainError(
                "The Gradle launch-anchor cleanup identity is incomplete"
            )
        anchored_launch = GradleLaunchAnchor(
            launch_anchor,
            anchor_target,
            runtime,
            wrapper_guard.launch_watchdog,
        )
        if require_normal_watchdog_exit:
            result_code = poll_gradle_child_exit_code(process, wrapper_guard)
            if result_code is None:
                raise CleanupUncertainError(
                    "The Gradle child has no authenticated terminal result"
                )
            verify_gradle_launch_anchor_guard(anchored_launch, sampler)
            verify_java_process_inventory((anchor_target,))
            verify_launch_group_contains_only_anchor(anchored_launch)
        else:
            anchor_sample = sampler.sample(anchor_target, time.monotonic_ns())
            if (
                anchor_sample.status is not macos_guarded_java.SampleStatus.MISSING
                or process.poll() is None
                or process_group_exists(process.pid)
            ):
                raise CleanupUncertainError(
                    "The failed launch anchor was not proved absent before cleanup"
                )
    pinned_evidence: PinnedWatchdogEvidence | None = None
    watchdog_finished = False
    try:
        if wrapper_guard.launch_watchdog is None:
            raise CleanupUncertainError(
                "The Gradle wrapper launch watchdog was never retained"
            )
        stop_java_guard_auxiliary(
            wrapper_guard.monitor,
            wrapper_guard.spawned_monitor_process,
        )
        if anchored_launch is not None and require_normal_watchdog_exit:
            try:
                anchored_launch.handle.finish_normally()
            except forge_server_launch_anchor.LaunchAnchorError as exception:
                raise CleanupUncertainError(
                    f"The Gradle launch anchor could not finish normally: {exception}"
                ) from exception
            anchor_sample = sampler.sample(anchor_target, time.monotonic_ns())
            if (
                anchor_sample.status is not macos_guarded_java.SampleStatus.MISSING
                or not wait_for_process_group_absence(
                    process.pid,
                    PROCESS_STOP_TIMEOUT_SECONDS,
                )
                or verify_java_process_inventory()
            ):
                raise CleanupUncertainError(
                    "The launch anchor did not leave exact global absence"
                )
        forge_server_launch_watchdog.finish_launch_watchdog(
            wrapper_guard.launch_watchdog,
            require_normal_exit=require_normal_watchdog_exit,
        )
        watchdog_finished = True
        expected_watchdog_runtime_descriptor = (
            runtime.descriptor
            if watchdog_runtime_directory_descriptor is None
            else watchdog_runtime_directory_descriptor
        )
        pinned_evidence = pin_terminal_watchdog_evidence(
            wrapper_guard.launch_watchdog,
            expected_watchdog_runtime_descriptor,
            (
                anchored_launch.handle
                if anchored_launch is not None and require_normal_watchdog_exit
                else None
            ),
        )
        wrapper_guard.launch_watchdog.close_runtime_directory()
        if anchored_launch is not None:
            try:
                anchored_launch.handle.close()
            except forge_server_launch_anchor.LaunchAnchorError as exception:
                raise CleanupUncertainError(
                    f"The completed launch-anchor handle could not close: {exception}"
                ) from exception
        retire_owned_runtime_directory(runtime)
        if not retain_terminal_evidence:
            completed_evidence = pinned_evidence
            pinned_evidence = None
            close_pinned_watchdog_evidence(completed_evidence)
            return None
        return pinned_evidence
    except BaseException as exception:
        if pinned_evidence is not None:
            try:
                close_pinned_watchdog_evidence(pinned_evidence)
            except OSError:
                pass
        if watchdog_finished and wrapper_guard.launch_watchdog is not None:
            wrapper_guard.launch_watchdog.close_runtime_directory()
        if isinstance(exception, (KeyboardInterrupt, SystemExit)):
            raise
        raise CleanupUncertainError(
            f"The Gradle wrapper guard could not be reaped safely: {exception}"
        ) from exception


def stop_server_guard_auxiliaries(server_guard: ServerJavaGuard) -> None:
    """Reaps server auxiliaries before releasing persistent launch ownership."""

    try:
        macos_guarded_java.stop_spawned_auxiliary(
            server_guard.caffeinate_process
        )
        stop_java_guard_auxiliary(
            server_guard.monitor,
            server_guard.spawned_monitor_process,
        )
    except (KeyboardInterrupt, SystemExit):
        raise
    except BaseException as exception:
        raise CleanupUncertainError(
            f"The owned server guard auxiliaries did not stop: {exception}"
        ) from exception


def cleanup_gradle_launch_anchor_without_wrapper(
    launch: GradleLaunchAnchor,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    state_root: Path,
) -> None:
    """Contains a broker whose direct child was never bound to a wrapper guard."""

    process = launch.process
    runtime = launch.runtime
    if runtime.path.parent != state_root:
        raise CleanupUncertainError("The unbound anchor runtime identity is unsafe")
    try:
        if process.poll() is None:
            stop_process_group(process)
        elif process_group_exists(process.pid):
            if launch.target is None:
                raise CleanupUncertainError(
                    "The exited unbound anchor left a group without an exact identity"
                )
            stop_owned_launch_group(process, sampler, (launch.target,))
        if launch.target is not None:
            absent = wait_for_detached_java_absence(
                (
                    DetachedJavaObservation(
                        target=launch.target,
                        process_group_id=process.pid,
                        session_id=process.pid,
                    ),
                ),
                sampler,
            )
        else:
            absent = (
                not process_group_exists(process.pid)
                and wait_for_global_java_absence()
            )
        if not absent:
            raise CleanupUncertainError(
                "The unbound launch anchor was not proved globally absent"
            )
        if launch.launch_watchdog is not None:
            forge_server_launch_watchdog.finish_launch_watchdog(
                launch.launch_watchdog,
                require_normal_exit=False,
            )
            launch.launch_watchdog.close_runtime_directory()
        launch.handle.close()
        retire_owned_runtime_directory(runtime)
    except (KeyboardInterrupt, SystemExit):
        raise
    except BaseException as exception:
        raise CleanupUncertainError(
            f"The unbound Gradle launch anchor could not be reaped: {exception}"
        ) from exception


def cleanup_server_launch(
    process: subprocess.Popen[bytes] | None,
    server_guard: ServerJavaGuard | None,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    *,
    wrapper_guard: WrapperJavaGuard | None = None,
    launch_anchor: GradleLaunchAnchor | None = None,
    state_root: Path = STATE_ROOT,
    detached_observation: DetachedJavaObservation | None = None,
    unbound_handoff_seen: bool = False,
    require_normal_watchdog_exit: bool = False,
    watchdog_runtime_directory_descriptor: int | None = None,
) -> PinnedWatchdogEvidence | None:
    """Stops only a proven-owned launch and preserves retryable supervision."""

    if process is None and (
        wrapper_guard is not None
        or launch_anchor is not None
        or server_guard is not None
        or detached_observation is not None
        or unbound_handoff_seen
    ):
        raise CleanupUncertainError(
            "Guarded launch state exists without its direct process handle"
        )
    if wrapper_guard is not None and wrapper_guard.launch_anchor is not None:
        if wrapper_guard.anchor_target is None:
            raise CleanupUncertainError("The wrapper lost its exact anchor identity")
        guarded_anchor = GradleLaunchAnchor(
            wrapper_guard.launch_anchor,
            wrapper_guard.anchor_target,
            wrapper_guard.runtime,
            wrapper_guard.launch_watchdog,
        )
        if launch_anchor is not None and launch_anchor != guarded_anchor:
            raise CleanupUncertainError("The retained launch anchors disagree")
        launch_anchor = guarded_anchor
    if process is not None and launch_anchor is not None:
        if process is not launch_anchor.process:
            raise CleanupUncertainError("The launch process is not its retained anchor")
    if process is not None and wrapper_guard is None:
        if launch_anchor is not None:
            cleanup_gradle_launch_anchor_without_wrapper(
                launch_anchor,
                sampler,
                state_root,
            )
            return None
        try:
            if process.poll() is None:
                stop_process_group(process)
            else:
                stop_direct_spawned_process(process)
            globally_absent = wait_for_global_java_absence()
        except BaseException as exception:
            raise CleanupUncertainError(
                "The spawned Gradle process was never bound and could not be "
                f"reaped safely: {exception}"
            ) from exception
        absence_detail = (
            "global Java absence was observed"
            if globally_absent
            else "global Java absence was not proved"
        )
        raise CleanupUncertainError(
            "The spawned Gradle process was never bound to an exact guarded "
            f"Java identity; {absence_detail}"
        )

    if process is not None and wrapper_guard is not None:
        anchored_normal_exit = (
            launch_anchor is not None and require_normal_watchdog_exit
        )
        if anchored_normal_exit:
            if (
                server_guard is None
                or detached_observation is not None
                or unbound_handoff_seen
            ):
                raise CleanupUncertainError(
                    "A normal anchored launch has incomplete server ownership"
                )
            require_guarded_server_stopped(
                process,
                server_guard,
                sampler,
                wrapper_guard,
            )
        else:
            exact_targets = list(wrapper_launch_java_targets(wrapper_guard))
            if server_guard is not None:
                exact_targets.append(server_guard.target)
            stop_owned_launch_group(
                process,
                sampler,
                tuple(dict.fromkeys(exact_targets)),
            )
            if unbound_handoff_seen and server_guard is None:
                wait_for_global_java_absence()
                raise CleanupUncertainError(
                    "An unbound server Java handoff prevents cleanup confirmation"
                )
            observations = [
                DetachedJavaObservation(
                    target=target,
                    process_group_id=process.pid,
                    session_id=process.pid,
                )
                for target in dict.fromkeys(exact_targets)
            ]
            if detached_observation is not None:
                stop_detached_java_observations(
                    (detached_observation,),
                    sampler,
                )
                observations.append(detached_observation)
            if not observations or not wait_for_detached_java_absence(
                tuple(observations),
                sampler,
            ):
                if detached_observation is not None:
                    raise CleanupUncertainError(
                        "The detached server Java identity or process group "
                        "remains uncertain"
                    )
                raise CleanupUncertainError(
                    "The guarded Java launch remains uncertain because global "
                    "absence was not proved"
                )
            if server_guard is not None:
                try:
                    require_guarded_server_stopped(process, server_guard, sampler)
                except E2EError as exception:
                    raise CleanupUncertainError(
                        "The guarded server identity remained uncertain after stop"
                    ) from exception

        if server_guard is not None:
            stop_server_guard_auxiliaries(server_guard)
        pinned_evidence = cleanup_wrapper_java_guard(
            process,
            wrapper_guard,
            sampler,
            state_root,
            require_normal_watchdog_exit=require_normal_watchdog_exit,
            watchdog_runtime_directory_descriptor=(
                watchdog_runtime_directory_descriptor
            ),
            retain_terminal_evidence=True,
        )
        if pinned_evidence is None:
            raise CleanupUncertainError(
                "The launch watchdog terminal evidence was not retained"
            )
        return pinned_evidence
    if server_guard is not None:
        stop_server_guard_auxiliaries(server_guard)
    return None


def gradle_topology_paths(runtime_directory: Path) -> tuple[Path, Path]:
    """Returns the fixed handoff paths in one owner-private preflight runtime."""

    if (
        not runtime_directory.is_absolute()
        or not runtime_directory.is_dir()
        or runtime_directory.is_symlink()
    ):
        raise E2EError(
            "The Gradle topology preflight runtime is missing or linked: "
            f"{runtime_directory}"
        )
    return (
        runtime_directory / GRADLE_TOPOLOGY_HANDOFF_FILE_NAME,
        runtime_directory / GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_FILE_NAME,
    )


def _decode_topology_executable(value: str, description: str) -> str:
    try:
        decoded = base64.b64decode(value, validate=True).decode("utf-8")
    except (binascii.Error, UnicodeDecodeError) as exception:
        raise E2EError(
            f"The Gradle topology {description} executable is malformed"
        ) from exception
    path = Path(decoded)
    if (
        not path.is_absolute()
        or path.name != "java"
        or ".." in path.parts
        or "\x00" in decoded
        or "\n" in decoded
        or "\r" in decoded
        or len(decoded.encode("utf-8")) > 4096
    ):
        raise E2EError(
            f"The Gradle topology {description} executable is unsafe"
        )
    return decoded


def read_gradle_topology_handoff(
    handoff_path: Path,
    runtime_directory: Path,
    run_token: str,
) -> GradleTopologyHandoff:
    """Parses an intrinsic handoff before accepting or rejecting its topology."""

    if re.fullmatch(r"[0-9a-f]{64}", run_token) is None:
        raise E2EError("The Gradle topology run token is malformed")
    expected_handoff, _acknowledgement = gradle_topology_paths(
        runtime_directory
    )
    if handoff_path != expected_handoff:
        raise E2EError("The Gradle topology handoff path is not owned")
    ensure_no_symlink_components(handoff_path, runtime_directory)
    ensure_regular_unlinked_file(handoff_path, "Gradle topology handoff")
    try:
        handoff_size = handoff_path.stat().st_size
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect the Gradle topology handoff: {exception}"
        ) from exception
    if handoff_size <= 0 or handoff_size > GRADLE_TOPOLOGY_MAXIMUM_HANDOFF_SIZE:
        raise E2EError(
            f"The Gradle topology handoff has an invalid size: {handoff_size}"
        )
    try:
        content = handoff_path.read_bytes()
    except OSError as exception:
        raise E2EError(
            f"Cannot read the Gradle topology handoff: {exception}"
        ) from exception
    if len(content) != handoff_size or not content.endswith(b"\n"):
        raise E2EError("The Gradle topology handoff has an invalid size")
    try:
        lines = content.decode("ascii").splitlines()
    except UnicodeDecodeError as exception:
        raise E2EError("The Gradle topology handoff is not ASCII") from exception
    pairs = [line.partition("=") for line in lines]
    if any(not name or separator != "=" for name, separator, _value in pairs):
        raise E2EError("The Gradle topology handoff syntax changed")
    handoff = {name: value for name, _separator, value in pairs}
    expected_fields = {
        "schema",
        "run_token",
        "pid",
        "parent_pid",
        "executable_base64",
        "parent_executable_base64",
        "java_feature",
        "maximum_heap_bytes",
        "maximum_heap_argument",
    }
    if len(handoff) != len(pairs) or set(handoff) != expected_fields:
        raise E2EError("The Gradle topology handoff field inventory changed")

    def positive_integer(name: str) -> int:
        value = handoff[name]
        if re.fullmatch(r"[1-9][0-9]*", value) is None:
            raise E2EError(
                f"The Gradle topology handoff {name} is invalid"
            )
        result = int(value)
        if result > (1 << 63) - 1:
            raise E2EError(
                f"The Gradle topology handoff {name} is out of range"
            )
        return result

    pid = positive_integer("pid")
    parent_pid = positive_integer("parent_pid")
    java_feature = positive_integer("java_feature")
    maximum_heap_bytes = positive_integer("maximum_heap_bytes")
    executable = _decode_topology_executable(
        handoff["executable_base64"],
        "JavaExec",
    )
    parent_executable = _decode_topology_executable(
        handoff["parent_executable_base64"],
        "parent",
    )
    if (
        handoff["schema"] != "1"
        or handoff["run_token"] != run_token
        or pid == parent_pid
        or pid == os.getpid()
        or parent_pid == os.getpid()
        or java_feature != SERVER_JAVA_FEATURE
        or maximum_heap_bytes != SERVER_MAXIMUM_HEAP_BYTES
        or handoff["maximum_heap_argument"] != SERVER_MAXIMUM_HEAP_ARGUMENT
    ):
        raise E2EError(
            "The Gradle topology JavaExec identity or heap contract changed"
        )
    return GradleTopologyHandoff(
        pid=pid,
        parent_pid=parent_pid,
        executable=executable,
        parent_executable=parent_executable,
        java_feature=java_feature,
        maximum_heap_bytes=maximum_heap_bytes,
    )


def validate_gradle_topology_handoff(
    handoff_path: Path,
    runtime_directory: Path,
    run_token: str,
    launch_process_id: int,
    expected_parent_executable: str,
) -> GradleTopologyHandoff:
    """Requires the handoff to name the exact direct wrapper parent."""

    if type(launch_process_id) is not int or launch_process_id <= 0:
        raise E2EError("The Gradle topology launch PID is invalid")
    handoff = read_gradle_topology_handoff(
        handoff_path,
        runtime_directory,
        run_token,
    )
    if (
        handoff.pid == launch_process_id
        or handoff.parent_pid != launch_process_id
        or handoff.parent_executable != expected_parent_executable
    ):
        raise E2EError(
            "The Gradle topology JavaExec identity or heap contract changed"
        )
    return handoff


def bind_exact_java_identity(
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    pid: int,
    process_group_id: int,
    executable: str,
) -> macos_guarded_java.OwnedJavaProcess | None:
    """Rejects a sampler that returns anything except the requested identity."""

    target = sampler.bind(pid, process_group_id, executable)
    if target is not None and (
        target.pid != pid
        or target.process_group_id != process_group_id
        or target.expected_executable != executable
    ):
        raise E2EError("The native sampler returned a foreign Java identity")
    return target


def read_java_process_inventory(
    pgrep_path: Path = PGREP_PATH,
) -> tuple[int, ...]:
    """Returns the bounded global inventory of processes named exactly ``java``."""

    try:
        metadata = pgrep_path.lstat()
    except OSError as exception:
        raise E2EError(
            f"Cannot inspect the Java process inventory tool: {exception}"
        ) from exception
    if (
        not pgrep_path.is_absolute()
        or not stat.S_ISREG(metadata.st_mode)
        or pgrep_path.is_symlink()
        or metadata.st_uid != 0
        or metadata.st_mode & 0o022
        or not os.access(pgrep_path, os.X_OK)
    ):
        raise E2EError(
            f"The Java process inventory tool is unsafe: {pgrep_path}"
        )
    try:
        completed = subprocess.run(
            [str(pgrep_path), "-x", "java"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env={"LANG": "C", "LC_ALL": "C", "PATH": GRADLE_EXECUTABLE_SEARCH_PATH},
            timeout=JAVA_PROCESS_INVENTORY_TIMEOUT_SECONDS,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exception:
        raise E2EError(
            f"Cannot inventory global Java processes: {exception}"
        ) from exception
    output = completed.stdout
    error_output = completed.stderr
    if not isinstance(output, bytes) or not isinstance(error_output, bytes):
        raise E2EError("The Java process inventory returned a non-binary payload")
    if (
        len(output) > MAXIMUM_JAVA_PROCESS_INVENTORY_SIZE
        or len(error_output) > MAXIMUM_JAVA_PROCESS_INVENTORY_SIZE
    ):
        raise E2EError("The Java process inventory exceeded its byte bound")
    if completed.returncode == 1 and not output and not error_output:
        return ()
    if completed.returncode != 0 or error_output or not output.endswith(b"\n"):
        raise E2EError(
            "The Java process inventory command returned an invalid result"
        )
    try:
        lines = output.decode("ascii").splitlines()
    except UnicodeDecodeError as exception:
        raise E2EError("The Java process inventory is not ASCII") from exception
    if not lines or any(re.fullmatch(r"[1-9][0-9]*", line) is None for line in lines):
        raise E2EError("The Java process inventory contains an invalid PID")
    process_ids = tuple(int(line) for line in lines)
    if (
        any(process_id > (1 << 31) - 1 for process_id in process_ids)
        or len(set(process_ids)) != len(process_ids)
    ):
        raise E2EError("The Java process inventory contains an invalid PID set")
    return tuple(sorted(process_ids))


def verify_java_process_inventory(
    known_targets: tuple[macos_guarded_java.OwnedJavaProcess, ...] = (),
    allowed_unbound_process_ids: tuple[int, ...] = (),
) -> tuple[int, ...]:
    """Rejects every globally visible Java PID not attributable to this launch."""

    known = tuple(dict.fromkeys(known_targets))
    if len(known) > MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT:
        raise E2EError("The Java process inventory exceeds its identity bound")
    known_by_pid: dict[int, macos_guarded_java.OwnedJavaProcess] = {}
    for target in known:
        prior = known_by_pid.setdefault(target.pid, target)
        if prior != target:
            raise E2EError("The Java process inventory contains conflicting identities")
    if len(allowed_unbound_process_ids) > 1:
        raise E2EError(
            "The Java process inventory permits at most one unbound launch PID"
        )
    unbound = set(allowed_unbound_process_ids)
    if len(unbound) != len(allowed_unbound_process_ids) or any(
        type(process_id) is not int or process_id <= 0 for process_id in unbound
    ):
        raise E2EError("The Java process inventory has an invalid unbound PID")
    if len(set(known_by_pid) | unbound) > MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT:
        raise E2EError("The Java process inventory exceeds its process bound")
    inventory = read_java_process_inventory()
    unknown = sorted(set(inventory) - set(known_by_pid) - unbound)
    if unknown:
        preview = ", ".join(str(process_id) for process_id in unknown[:8])
        suffix = "" if len(unknown) <= 8 else f" (+{len(unknown) - 8} more)"
        raise E2EError(
            "An unowned Java process is active; refusing concurrent native "
            f"execution: {preview}{suffix}"
        )
    return inventory


def require_no_java_processes() -> None:
    """Requires a zero-Java baseline before a repository-owned native launch."""

    inventory = verify_java_process_inventory()
    if inventory:
        raise E2EError(
            "A Java process is already active; refusing to overlap the bounded "
            "Etherology native launch"
        )


def verify_exact_java_memory_envelope(
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    required_targets: tuple[macos_guarded_java.OwnedJavaProcess, ...],
    optional_targets: tuple[macos_guarded_java.OwnedJavaProcess, ...] = (),
) -> int:
    """Enforces individual and aggregate current physical-memory ceilings."""

    required = tuple(dict.fromkeys(required_targets))
    optional = tuple(
        target for target in dict.fromkeys(optional_targets) if target not in required
    )
    targets = required + optional
    if not targets:
        raise E2EError("The Java memory envelope has no exact identity")
    if len(targets) > MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT:
        raise E2EError("The Java memory envelope exceeds its identity bound")
    identities_by_pid: dict[int, macos_guarded_java.OwnedJavaProcess] = {}
    for target in targets:
        prior_target = identities_by_pid.setdefault(target.pid, target)
        if prior_target != target:
            raise E2EError("The Java memory envelope contains conflicting identities")

    aggregate_bytes = 0
    required_set = set(required)
    for target in targets:
        sample = sampler.sample(target, time.monotonic_ns())
        if (
            target not in required_set
            and sample.status is macos_guarded_java.SampleStatus.MISSING
        ):
            continue
        if (
            sample.status is not macos_guarded_java.SampleStatus.AVAILABLE
            or sample.source.value != "proc-pid-rusage-v4"
            or sample.observed_identity != target
            or type(sample.current_phys_footprint_bytes) is not int
            or sample.current_phys_footprint_bytes < 0
        ):
            raise E2EError(
                "The Java memory envelope lost an authoritative current "
                f"physical-memory sample for PID {target.pid}: "
                f"{sample.status.value}"
            )
        current_bytes = sample.current_phys_footprint_bytes
        if current_bytes > MAXIMUM_INDIVIDUAL_JAVA_PHYSICAL_MEMORY_BYTES:
            raise E2EError(
                "An exact Java process exceeded the five-GiB emergency "
                f"physical-memory ceiling: PID {target.pid}"
            )
        aggregate_bytes += current_bytes
        if aggregate_bytes > MAXIMUM_AGGREGATE_JAVA_PHYSICAL_MEMORY_BYTES:
            raise E2EError(
                "The exact Java launch exceeded the six-GiB aggregate "
                "physical-memory ceiling"
            )
    return aggregate_bytes


def java_guard_state(
    target: macos_guarded_java.OwnedJavaProcess,
    monitor: macos_guarded_java.GuardedJavaMonitor,
) -> dict[str, object]:
    """Returns the authenticated strict-policy state for one live monitor."""

    if monitor.target != target or monitor.policy_name != STRICT_MEMORY_POLICY_NAME:
        raise E2EError("The Java memory guard is not bound to the strict policy")
    return {
        "pid": target.pid,
        "process_group_id": target.process_group_id,
        "proc_start_abstime": target.proc_start_abstime,
        "expected_executable": target.expected_executable,
        "memory_guard_pid": monitor.process.pid,
        "memory_guard_telemetry": str(monitor.telemetry_path),
        "memory_guard_readiness": str(monitor.readiness_path),
        "memory_guard_maximum_memory_mb": SERVER_MAXIMUM_MEMORY_MB,
        macos_guarded_java.MEMORY_POLICY_STATE_FIELD: monitor.policy_name,
    }


def verify_java_guard_is_enforcing(
    target: macos_guarded_java.OwnedJavaProcess,
    monitor: macos_guarded_java.GuardedJavaMonitor | None,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    description: str,
    *,
    allow_missing: bool = False,
) -> None:
    """Fails closed when a live exact JVM loses its strict memory monitor."""

    if monitor is not None and monitor.process.poll() is None:
        state = java_guard_state(target, monitor)
        try:
            macos_guarded_java.verify_guard_state_paths(
                state,
                monitor.telemetry_path.parent,
            )
        except macos_guarded_java.GuardedJavaError:
            pass
        else:
            if macos_guarded_java.memory_guard_is_enforcing(state):
                return
    sample = sampler.sample(target, time.monotonic_ns())
    if allow_missing and sample.status is macos_guarded_java.SampleStatus.MISSING:
        return
    if sample.status is macos_guarded_java.SampleStatus.AVAILABLE:
        raise E2EError(
            f"The live {description} lost authoritative strict memory monitoring"
        )
    raise E2EError(
        f"The {description} identity cannot be verified while its memory guard "
        f"is unhealthy: {sample.status.value}"
    )


def verify_wrapper_launch_supervision(
    wrapper_guard: WrapperJavaGuard,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    description: str,
    *,
    allow_missing: bool = False,
) -> None:
    """Requires both independent wrapper supervisors throughout a live launch."""

    launch_watchdog = wrapper_guard.launch_watchdog
    if launch_watchdog is None:
        raise E2EError(f"The {description} has no persistent launch watchdog")
    if wrapper_guard.launch_anchor is not None:
        if wrapper_guard.anchor_target is None:
            raise E2EError(f"The {description} has no exact launch-anchor identity")
        launch = GradleLaunchAnchor(
            wrapper_guard.launch_anchor,
            wrapper_guard.anchor_target,
            wrapper_guard.runtime,
            launch_watchdog,
        )
        verify_gradle_launch_anchor_guard(launch, sampler)
    if allow_missing:
        heartbeat_error: (
            forge_server_launch_watchdog.LaunchWatchdogError | None
        ) = None
        if launch_watchdog.process.poll() is None:
            try:
                forge_server_launch_watchdog.send_launch_watchdog_heartbeat(
                    launch_watchdog
                )
            except forge_server_launch_watchdog.LaunchWatchdogError as exception:
                heartbeat_error = exception
        try:
            transition = (
                forge_server_launch_watchdog.verify_launch_watchdog_transition(
                    launch_watchdog
                )
            )
        except forge_server_launch_watchdog.LaunchWatchdogError as exception:
            raise E2EError(
                f"The {description} lost persistent launch supervision: "
                f"{exception}"
            ) from exception
        if heartbeat_error is not None and transition.get("status") == "running":
            raise E2EError(
                f"The {description} lost its launch-watchdog heartbeat: "
                f"{heartbeat_error}"
            ) from heartbeat_error
        verify_java_guard_is_enforcing(
            wrapper_guard.target,
            wrapper_guard.monitor,
            sampler,
            description,
            allow_missing=True,
        )
        return
    try:
        forge_server_launch_watchdog.send_launch_watchdog_heartbeat(
            launch_watchdog
        )
        forge_server_launch_watchdog.verify_launch_watchdog(launch_watchdog)
    except forge_server_launch_watchdog.LaunchWatchdogError as exception:
        raise E2EError(
            f"The {description} lost persistent launch supervision: {exception}"
        ) from exception
    verify_java_guard_is_enforcing(
        wrapper_guard.target,
        wrapper_guard.monitor,
        sampler,
        description,
    )


def wrapper_launch_java_targets(
    wrapper_guard: WrapperJavaGuard,
    *additional_targets: macos_guarded_java.OwnedJavaProcess,
) -> tuple[macos_guarded_java.OwnedJavaProcess, ...]:
    """Returns the deduplicated exact identities in one supervised launch."""

    candidates = (
        (() if wrapper_guard.anchor_target is None else (wrapper_guard.anchor_target,))
        + (wrapper_guard.target,)
        + additional_targets
    )
    targets = tuple(dict.fromkeys(candidates))
    if len(targets) > MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT:
        raise E2EError("The supervised launch exceeds its exact Java identity bound")
    return targets


def poll_gradle_child_exit_code(
    process: subprocess.Popen[bytes],
    wrapper_guard: WrapperJavaGuard | None,
) -> int | None:
    """Polls Gradle itself even when a stable launch anchor remains alive."""

    if wrapper_guard is None or wrapper_guard.launch_anchor is None:
        return process.poll()
    handle = wrapper_guard.launch_anchor
    if process is not handle.process or process.pid != handle.anchor_pid:
        raise E2EError("The Gradle child is not bound to its launch anchor")
    try:
        result = handle.poll_child_result()
    except forge_server_launch_anchor.LaunchAnchorError as exception:
        raise E2EError(
            f"The Gradle launch anchor lost its child result: {exception}"
        ) from exception
    if result is None:
        return None
    if (
        not result.started
        or result.pid != wrapper_guard.target.pid
        or result.arguments_sha256 != handle.arguments_sha256
        or handle.process.poll() is not None
    ):
        raise E2EError("The Gradle launch-anchor child result changed identity")
    return result.exit_code


def wait_for_detached_java_absence(
    observations: tuple[DetachedJavaObservation, ...],
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    timeout_seconds: float = DETACHED_JAVA_EXIT_TIMEOUT_SECONDS,
    stable_absence_seconds: float = DETACHED_JAVA_STABLE_ABSENCE_SECONDS,
) -> bool:
    """Observes rejected identities until every global Java PID is stably absent."""

    if (
        not observations
        or timeout_seconds < 0
        or stable_absence_seconds < 0
        or any(
            observation.target is None
            or observation.process_group_id <= 0
            or observation.session_id <= 0
            or observation.target.process_group_id
            != observation.process_group_id
            for observation in observations
        )
    ):
        return False
    groups = {
        observation.process_group_id
        for observation in observations
        if observation.process_group_id > 0
    }
    targets = {
        observation.target
        for observation in observations
        if observation.target is not None
    }
    if len(targets) > MAXIMUM_GUARDED_JAVA_IDENTITY_COUNT:
        return False
    deadline = time.monotonic() + timeout_seconds
    absence_started_at: float | None = None
    while time.monotonic() < deadline:
        samples = {
            target: sampler.sample(target, time.monotonic_ns())
            for target in targets
        }
        if any(
            sample.status
            not in {
                macos_guarded_java.SampleStatus.AVAILABLE,
                macos_guarded_java.SampleStatus.MISSING,
            }
            for sample in samples.values()
        ):
            return False
        available_targets = tuple(
            target
            for target, sample in samples.items()
            if sample.status is macos_guarded_java.SampleStatus.AVAILABLE
        )
        if available_targets:
            try:
                verify_exact_java_memory_envelope(sampler, available_targets)
                verify_java_process_inventory(tuple(targets))
                for observation in observations:
                    if observation.target not in available_targets:
                        continue
                    if (
                        os.getpgid(observation.target.pid)
                        != observation.process_group_id
                        or os.getsid(observation.target.pid)
                        != observation.session_id
                    ):
                        return False
            except (E2EError, OSError, ProcessLookupError):
                return False
            absence_started_at = None
            time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
            continue
        try:
            inventory = verify_java_process_inventory(tuple(targets))
            groups_absent = all(not process_group_exists(group) for group in groups)
        except E2EError:
            return False
        if not inventory and groups_absent:
            now = time.monotonic()
            if absence_started_at is None:
                absence_started_at = now
            if now - absence_started_at >= stable_absence_seconds:
                return True
        else:
            absence_started_at = None
        time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
    return False


def wait_for_global_java_absence(
    timeout_seconds: float = DETACHED_JAVA_EXIT_TIMEOUT_SECONDS,
    stable_absence_seconds: float = DETACHED_JAVA_STABLE_ABSENCE_SECONDS,
) -> bool:
    """Requires a continuous zero-Java interval after an incompletely bound launch."""

    if timeout_seconds < 0 or stable_absence_seconds < 0:
        return False
    deadline = time.monotonic() + timeout_seconds
    absence_started_at: float | None = None
    while time.monotonic() < deadline:
        try:
            inventory = read_java_process_inventory()
        except E2EError:
            return False
        if inventory:
            absence_started_at = None
        else:
            now = time.monotonic()
            if absence_started_at is None:
                absence_started_at = now
            if now - absence_started_at >= stable_absence_seconds:
                return True
        time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
    return False


def verify_gradle_launcher_topology(
    configuration: ResolvedConfiguration,
    java_path: Path,
    sampler: macos_guarded_java.MacOsProcessMemorySampler,
    state_root: Path,
    run_token: str,
    run_lock: OwnedRunLock,
) -> None:
    """Proves a loader-free JavaExec child stays inside the wrapper PGID."""

    ensure_no_symlink_components(state_root, state_root.parent)
    if not state_root.is_dir() or state_root.is_symlink():
        raise E2EError("The Gradle topology preflight state root is unsafe")
    verify_owned_run_lock(run_lock)
    topology_runtime_owner = create_owned_runtime_directory(
        state_root,
        GRADLE_TOPOLOGY_RUNTIME_PREFIX,
        GRADLE_TOPOLOGY_COMPLETED_RUNTIME_PREFIX,
    )
    topology_runtime = topology_runtime_owner.path
    output_path = topology_runtime / "gradle.log"
    output_descriptor: int | None = None
    process: subprocess.Popen[bytes] | None = None
    launch_anchor: GradleLaunchAnchor | None = None
    wrapper_guard: WrapperJavaGuard | None = None
    wrapper_target: macos_guarded_java.OwnedJavaProcess | None = None
    java_target: macos_guarded_java.OwnedJavaProcess | None = None
    parent_target: macos_guarded_java.OwnedJavaProcess | None = None
    handoff: GradleTopologyHandoff | None = None
    handoff_seen = False
    required_observation_count = 0
    detached_observations: list[DetachedJavaObservation] = []
    cleanup_confirmed = False
    try:
        output_flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_CLOEXEC"):
            output_flags |= os.O_CLOEXEC
        if hasattr(os, "O_NOFOLLOW"):
            output_flags |= os.O_NOFOLLOW
        output_descriptor = os.open(
            output_path.name,
            output_flags,
            0o600,
            dir_fd=topology_runtime_owner.descriptor,
        )
        handoff_path, acknowledgement_path = gradle_topology_paths(
            topology_runtime
        )
        environment = gradle_launch_environment(
            java_path,
            state_root=state_root,
        )
        environment[GRADLE_TOPOLOGY_TOKEN_ENVIRONMENT_VARIABLE] = run_token
        environment[GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE] = str(
            handoff_path
        )
        environment[GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE] = str(
            acknowledgement_path
        )
        command = build_gradle_command(
            configuration,
            java_path,
            task_path=GRADLE_TOPOLOGY_TASK_PATH,
        )
        output_handle = os.fdopen(output_descriptor, "wb", buffering=0)
        output_descriptor = None
        with output_handle:
            try:
                verify_owned_run_lock(run_lock)
                verify_active_launch_runtime_inventory(
                    state_root,
                    (topology_runtime_owner,),
                )
                verify_gradle_user_home(gradle_user_home(state_root))
                verify_gradle_distribution_initialization()
                refreshed_configuration = load_configuration(
                    configuration.profile_manifest_path,
                    configuration.repository_root,
                )
                if refreshed_configuration != configuration:
                    raise E2EError(
                        "The dedicated-server configuration changed before topology"
                    )
                verify_gradle_probe_definition(refreshed_configuration)
                refreshed_command = build_gradle_command(
                    refreshed_configuration,
                    java_path,
                    task_path=GRADLE_TOPOLOGY_TASK_PATH,
                )
                refreshed_environment = gradle_launch_environment(
                    java_path,
                    state_root=state_root,
                )
                refreshed_environment[
                    GRADLE_TOPOLOGY_TOKEN_ENVIRONMENT_VARIABLE
                ] = run_token
                refreshed_environment[
                    GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE
                ] = str(handoff_path)
                refreshed_environment[
                    GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE
                ] = str(acknowledgement_path)
                if refreshed_command != command or refreshed_environment != environment:
                    raise E2EError(
                        "The Gradle topology launch identity changed before spawn"
                    )
                require_no_java_processes()
                verify_owned_run_lock(run_lock)
                deadline = time.monotonic() + GRADLE_TOPOLOGY_TIMEOUT_SECONDS
                launch_anchor = prepare_gradle_launch_anchor(
                    configuration,
                    java_path,
                    sampler,
                    state_root,
                    deadline,
                    output_handle,
                    environment,
                    task_path=GRADLE_TOPOLOGY_TASK_PATH,
                )
                process = launch_anchor.process
            except GradleLaunchAnchorStartError as exception:
                launch_anchor = exception.launch
                process = launch_anchor.process
                raise
            except OSError as exception:
                raise E2EError(
                    f"Cannot start the Gradle topology preflight: {exception}"
                ) from exception
            verify_owned_gradle_process_group(process)
            if launch_anchor is None:
                raise E2EError("The Gradle topology launch anchor was not retained")
            try:
                wrapper_guard = start_anchored_wrapper_java_guard(
                    launch_anchor,
                    java_path,
                    sampler,
                    deadline,
                    output_handle,
                )
            except WrapperGuardStartError as exception:
                wrapper_guard = exception.wrapper_guard
                wrapper_target = wrapper_guard.target
                raise
            wrapper_target = wrapper_guard.target
            while time.monotonic() < deadline:
                verify_process_output_bound(output_path, output_handle.fileno())
                if poll_gradle_child_exit_code(process, wrapper_guard) is not None:
                    raise E2EError(
                        "The Gradle topology preflight exited before its "
                        f"JavaExec handoff: {read_process_tail(output_path)}"
                    )
                verify_active_launch_runtime_inventory(
                    state_root,
                    (topology_runtime_owner, wrapper_guard.runtime),
                )
                verify_wrapper_launch_supervision(
                    wrapper_guard,
                    sampler,
                    "Gradle topology wrapper JVM",
                )
                verify_exact_java_memory_envelope(
                    sampler,
                    wrapper_launch_java_targets(wrapper_guard),
                )
                handoff_available = handoff_path.exists() or handoff_path.is_symlink()
                if handoff_available:
                    handoff_seen = True
                    handoff = read_gradle_topology_handoff(
                        handoff_path,
                        topology_runtime,
                        run_token,
                    )
                    if wrapper_target is None:
                        raise E2EError(
                            "The Gradle topology wrapper never exposed its "
                            "exact Java identity"
                        )
                    try:
                        java_process_group_id = os.getpgid(handoff.pid)
                        java_session_id = os.getsid(handoff.pid)
                    except (ProcessLookupError, OSError) as exception:
                        raise E2EError(
                            "Cannot inspect the Gradle topology JavaExec identity"
                        ) from exception
                    bind_deadline = min(
                        deadline,
                        time.monotonic() + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
                    )
                    while time.monotonic() < bind_deadline:
                        if poll_gradle_child_exit_code(
                            process,
                            wrapper_guard,
                        ) is not None:
                            raise E2EError(
                                "The Gradle topology wrapper exited before the "
                                "JavaExec identity could be bound"
                            )
                        verify_wrapper_launch_supervision(
                            wrapper_guard,
                            sampler,
                            "Gradle topology wrapper JVM",
                        )
                        verify_exact_java_memory_envelope(
                            sampler,
                            wrapper_launch_java_targets(wrapper_guard),
                        )
                        java_target = bind_exact_java_identity(
                            sampler,
                            handoff.pid,
                            java_process_group_id,
                            handoff.executable,
                        )
                        if java_target is not None:
                            break
                        time.sleep(0.01)
                    if java_target is None:
                        detached_observations.append(
                            DetachedJavaObservation(
                                target=None,
                                process_group_id=java_process_group_id,
                                session_id=java_session_id,
                            )
                        )
                    else:
                        detached_observations.append(
                            DetachedJavaObservation(
                                target=java_target,
                                process_group_id=java_process_group_id,
                                session_id=java_session_id,
                            )
                        )
                    required_observation_count = 1
                    if java_target is None:
                        raise E2EError(
                            "The Gradle topology JavaExec identity could not be bound"
                        )

                    parent_process_group_id = process.pid
                    parent_session_id = process.pid
                    parent_target = wrapper_target
                    if handoff.parent_pid != wrapper_target.pid:
                        required_observation_count = 2
                        try:
                            parent_process_group_id = os.getpgid(handoff.parent_pid)
                            parent_session_id = os.getsid(handoff.parent_pid)
                        except (ProcessLookupError, OSError) as exception:
                            raise E2EError(
                                "Cannot inspect the Gradle topology JavaExec parent"
                            ) from exception
                        parent_target = None
                        parent_bind_deadline = min(
                            deadline,
                            time.monotonic()
                            + MEMORY_HANDOFF_BIND_TIMEOUT_SECONDS,
                        )
                        while time.monotonic() < parent_bind_deadline:
                            verify_wrapper_launch_supervision(
                                wrapper_guard,
                                sampler,
                                "Gradle topology wrapper JVM",
                            )
                            verify_exact_java_memory_envelope(
                                sampler,
                                wrapper_launch_java_targets(
                                    wrapper_guard,
                                    java_target,
                                ),
                            )
                            parent_target = bind_exact_java_identity(
                                sampler,
                                handoff.parent_pid,
                                parent_process_group_id,
                                handoff.parent_executable,
                            )
                            if parent_target is not None:
                                break
                            time.sleep(0.01)
                        detached_observations.append(
                            DetachedJavaObservation(
                                target=parent_target,
                                process_group_id=parent_process_group_id,
                                session_id=parent_session_id,
                            )
                        )
                    if (
                        java_target is None
                        or parent_target is None
                        or handoff.pid in {process.pid, wrapper_target.pid}
                        or handoff.parent_pid != wrapper_target.pid
                        or handoff.parent_executable != str(java_path)
                        or java_process_group_id != process.pid
                        or java_session_id != process.pid
                        or parent_process_group_id != process.pid
                        or parent_session_id != process.pid
                    ):
                        if (
                            java_target is not None
                            and parent_target is not None
                            and sampler.revalidate(java_target) == java_target
                            and sampler.revalidate(parent_target) == parent_target
                            and sampler.revalidate(wrapper_target) == wrapper_target
                        ):
                            pre_acknowledgement_error: BaseException | None = None
                            try:
                                verify_wrapper_launch_supervision(
                                    wrapper_guard,
                                    sampler,
                                    "Gradle topology wrapper JVM",
                                )
                                verify_exact_java_memory_envelope(
                                    sampler,
                                    wrapper_launch_java_targets(
                                        wrapper_guard,
                                        parent_target,
                                        java_target,
                                    ),
                                )
                            except BaseException as exception:
                                pre_acknowledgement_error = exception
                            write_bytes_exclusive(
                                acknowledgement_path,
                                f"token={run_token}\n".encode("ascii"),
                            )
                            if pre_acknowledgement_error is not None:
                                raise pre_acknowledgement_error
                        raise E2EError(
                            "The Gradle topology JavaExec escaped the wrapper "
                            "process group, session, or direct parent"
                        )
                    if sampler.revalidate(wrapper_target) != wrapper_target:
                        raise E2EError(
                            "The Gradle topology wrapper changed before "
                            "acknowledgement"
                        )
                    pre_acknowledgement_error = None
                    try:
                        verify_wrapper_launch_supervision(
                            wrapper_guard,
                            sampler,
                            "Gradle topology wrapper JVM",
                        )
                        verify_exact_java_memory_envelope(
                            sampler,
                            wrapper_launch_java_targets(
                                wrapper_guard,
                                java_target,
                            ),
                        )
                    except BaseException as exception:
                        pre_acknowledgement_error = exception
                    write_bytes_exclusive(
                        acknowledgement_path,
                        f"token={run_token}\n".encode("ascii"),
                    )
                    if pre_acknowledgement_error is not None:
                        raise pre_acknowledgement_error
                    break
                time.sleep(PROCESS_POLL_INTERVAL_SECONDS)
            else:
                raise E2EError(
                    "Timed out waiting for the Gradle topology JavaExec handoff"
                )
            exit_code = wait_for_bounded_process(
                process,
                output_path,
                run_deadline=deadline,
                wrapper_guard=wrapper_guard,
                optional_java_targets=(java_target,),
                sampler=sampler,
                additional_runtime_owners=(topology_runtime_owner,),
                output_descriptor=output_handle.fileno(),
            )
        if exit_code != 0:
            raise E2EError(
                f"The Gradle topology preflight exited with {exit_code}: "
                f"{read_process_tail(output_path)}"
            )
        if java_target is None or handoff is None or wrapper_target is None:
            raise E2EError("The Gradle topology preflight did not bind both JVMs")
        for description, target in (
            ("JavaExec", java_target),
            ("wrapper", wrapper_target),
        ):
            sample = sampler.sample(target, time.monotonic_ns())
            if sample.status is not macos_guarded_java.SampleStatus.MISSING:
                raise E2EError(
                    "The Gradle topology preflight ended without proving the "
                    f"{description} JVM stopped: {sample.status.value}"
                )
        if launch_anchor is None:
            raise CleanupUncertainError(
                "The Gradle topology lost its launch anchor before cleanup"
            )
        verify_gradle_launch_anchor_guard(launch_anchor, sampler)
        verify_java_process_inventory((launch_anchor.target,))
        verify_launch_group_contains_only_anchor(launch_anchor)
        cleanup_wrapper_java_guard(
            process,
            wrapper_guard,
            sampler,
            state_root,
            require_normal_watchdog_exit=True,
        )
        cleanup_confirmed = True
    except BaseException as topology_exception:
        cleanup_error: BaseException | None = None
        if process is not None:
            try:
                if wrapper_guard is None:
                    if launch_anchor is not None:
                        cleanup_gradle_launch_anchor_without_wrapper(
                            launch_anchor,
                            sampler,
                            state_root,
                        )
                    else:
                        if process.poll() is None:
                            stop_process_group(process)
                        else:
                            stop_direct_spawned_process(process)
                        globally_absent = wait_for_global_java_absence()
                        raise CleanupUncertainError(
                            "The topology launch never exposed a guarded exact "
                            "identity; "
                            + (
                                "global Java absence was observed"
                                if globally_absent
                                else "global Java absence was not proved"
                            )
                        )
                else:
                    exact_group_members = tuple(
                        dict.fromkeys(
                            target
                            for target in (
                                wrapper_guard.anchor_target,
                                wrapper_guard.target,
                                java_target,
                                parent_target,
                            )
                            if target is not None
                        )
                    )
                    stop_owned_launch_group(
                        process,
                        sampler,
                        exact_group_members,
                    )
                    observations = [
                        DetachedJavaObservation(
                            target=target,
                            process_group_id=process.pid,
                            session_id=process.pid,
                        )
                        for target in wrapper_launch_java_targets(wrapper_guard)
                    ]
                    if handoff_seen:
                        if (
                            handoff is None
                            or len(detached_observations)
                            != required_observation_count
                        ):
                            raise CleanupUncertainError(
                                "The topology handoff did not yield an exhaustive "
                                "identity set"
                            )
                        observations.extend(detached_observations)
                        stop_detached_java_observations(
                            tuple(detached_observations),
                            sampler,
                        )
                    if not wait_for_detached_java_absence(
                        tuple(observations),
                        sampler,
                    ):
                        raise CleanupUncertainError(
                            "The topology child, parent, or escaped process group "
                            "could not be proved absent"
                        )
                    cleanup_wrapper_java_guard(
                        process,
                        wrapper_guard,
                        sampler,
                        state_root,
                        require_normal_watchdog_exit=False,
                    )
            except BaseException as cleanup_exception:
                cleanup_error = cleanup_exception
        if process is None:
            cleanup_confirmed = True
        elif cleanup_error is None:
            cleanup_confirmed = True
        else:
            raise CleanupUncertainError(
                "The Gradle topology preflight failed and cleanup is "
                f"uncertain; its runtime was retained: {cleanup_error}"
            ) from topology_exception
        raise
    finally:
        if output_descriptor is not None:
            os.close(output_descriptor)
        try:
            if cleanup_confirmed:
                retire_owned_runtime_directory(topology_runtime_owner)
        finally:
            close_owned_runtime_directory(topology_runtime_owner)
            if wrapper_guard is not None:
                close_owned_runtime_directory(wrapper_guard.runtime)
            elif launch_anchor is not None:
                close_owned_runtime_directory(launch_anchor.runtime)


def execute_probe(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> dict[str, object]:
    run_token = secrets.token_hex(32)
    run_lock = acquire_run_lock(configuration, state_root, run_token)
    try:
        java_path, command = verify_environment(
            configuration,
            state_root,
            run_lock,
        )
        target_root = runtime_root(configuration, state_root)
        try:
            sampler = macos_guarded_java.MacOsProcessMemorySampler.native()
        except (
            macos_guarded_java.MemorySamplingError,
            macos_guarded_java.MemorySamplingUnavailable,
            OSError,
        ) as exception:
            raise E2EError(
                "Authoritative macOS physical-memory sampling is unavailable: "
                f"{exception}"
            ) from exception
        verify_owned_run_lock(run_lock)
        require_no_retained_launch_runtime(state_root)
        verify_gradle_launcher_topology(
            configuration,
            java_path,
            sampler,
            state_root,
            run_token,
            run_lock,
        )
        configuration, java_path, command = revalidate_after_gradle_topology(
            configuration,
            java_path,
            command,
            run_lock,
            state_root,
        )
    except CleanupUncertainError:
        close_owned_run_lock(run_lock)
        raise
    except BaseException:
        try:
            release_owned_run_lock(run_lock)
        finally:
            close_owned_run_lock(run_lock)
        raise
    attempt_path = run_attempt_path(configuration, state_root)
    attempt_content = (
        f"profile_id={PROFILE_ID}\nscenario={SCENARIO_ID}\npid={os.getpid()}\n"
    ).encode("utf-8")
    attempt_marker: OwnedLaunchFile | None = None
    try:
        attempt_marker = create_owned_launch_file(attempt_path, attempt_content)
    except CleanupUncertainError:
        close_owned_run_lock(run_lock)
        raise
    except FileExistsError as exception:
        try:
            raise E2EError(
                "The dedicated-server profile already has a launch attempt and is "
                f"consumed: {attempt_path}"
            ) from exception
        finally:
            try:
                release_owned_run_lock(run_lock)
            finally:
                close_owned_run_lock(run_lock)
    except OSError as exception:
        try:
            raise E2EError(
                f"Cannot durably record the dedicated-server launch attempt: {exception}"
            ) from exception
        finally:
            try:
                release_owned_run_lock(run_lock)
            finally:
                close_owned_run_lock(run_lock)
    except BaseException:
        try:
            release_owned_run_lock(run_lock)
        finally:
            close_owned_run_lock(run_lock)
        raise
    if attempt_marker is None:
        close_owned_run_lock(run_lock)
        raise CleanupUncertainError(
            "The dedicated-server launch attempt was not pinned"
        )
    output_path: Path | None = None
    process_log: OwnedLaunchFile | None = None
    output_descriptor: int | None = None
    process: subprocess.Popen[bytes] | None = None
    launch_anchor: GradleLaunchAnchor | None = None
    wrapper_guard: WrapperJavaGuard | None = None
    server_guard: ServerJavaGuard | None = None
    detached_observation: DetachedJavaObservation | None = None
    pinned_watchdog_evidence: PinnedWatchdogEvidence | None = None
    cleanup_confirmed = False
    run_succeeded = False
    try:
        verify_gradle_probe_definition(configuration)
        verify_runtime(configuration, state_root)
        verify_gradle_user_home(gradle_user_home(state_root))
        verify_owned_run_lock(run_lock)
        process_log = create_owned_launch_file(
            target_root
            / f".forge-server-gradle.{secrets.token_hex(16)}.log",
            b"",
        )
        output_path = process_log.path
        output_descriptor = os.dup(process_log.descriptor)
        environment = gradle_launch_environment(
            java_path,
            state_root=state_root,
        )
        environment[RUN_TOKEN_ENVIRONMENT_VARIABLE] = run_token
        handoff_path, acknowledgement_path = server_memory_handoff_paths(
            target_root
        )
        environment[MEMORY_HANDOFF_ENVIRONMENT_VARIABLE] = str(handoff_path)
        environment[MEMORY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE] = str(
            acknowledgement_path
        )
        run_deadline = time.monotonic() + RUN_TIMEOUT_SECONDS
        output_handle = os.fdopen(output_descriptor, "wb", buffering=0)
        output_descriptor = None
        with output_handle:
            try:
                final_pre_spawn_revalidation(
                    configuration,
                    java_path,
                    command,
                    run_lock,
                    attempt_marker,
                    process_log,
                    environment,
                    state_root,
                    run_token,
                )
                launch_anchor = prepare_gradle_launch_anchor(
                    configuration,
                    java_path,
                    sampler,
                    state_root,
                    run_deadline,
                    output_handle,
                    environment,
                    watchdog_runtime_directory=target_root,
                    watchdog_runtime_directory_descriptor=(
                        process_log.directory_descriptor
                    ),
                )
                process = launch_anchor.process
            except GradleLaunchAnchorStartError as exception:
                launch_anchor = exception.launch
                process = launch_anchor.process
                raise
            except OSError as exception:
                raise E2EError(
                    f"Cannot start the named Forge server probe: {exception}"
                ) from exception
            verify_owned_gradle_process_group(process)
            try:
                if launch_anchor is None:
                    raise E2EError("The Forge launch anchor was not retained")
                wrapper_guard = start_anchored_wrapper_java_guard(
                    launch_anchor,
                    java_path,
                    sampler,
                    run_deadline,
                    output_handle,
                )
            except WrapperGuardStartError as exception:
                wrapper_guard = exception.wrapper_guard
                raise
            try:
                server_guard = start_server_java_guard(
                    process,
                    output_path,
                    target_root,
                    run_token,
                    sampler,
                    run_deadline,
                    configuration.repository_root,
                    output_handle,
                    wrapper_guard,
                    output_descriptor=process_log.descriptor,
                )
            except ServerGuardStartError as exception:
                server_guard = exception.server_guard
                raise
            except DetachedJavaLaunchError as exception:
                detached_observation = exception.observation
                raise
            exit_code = wait_for_bounded_process(
                process,
                output_path,
                run_deadline=run_deadline,
                wrapper_guard=wrapper_guard,
                server_guard=server_guard,
                sampler=sampler,
                output_descriptor=process_log.descriptor,
            )
        require_guarded_server_stopped(
            process,
            server_guard,
            sampler,
            wrapper_guard,
        )
        pinned_watchdog_evidence = cleanup_server_launch(
            process,
            server_guard,
            sampler,
            wrapper_guard=wrapper_guard,
            launch_anchor=launch_anchor,
            state_root=state_root,
            detached_observation=detached_observation,
            require_normal_watchdog_exit=exit_code == 0,
            watchdog_runtime_directory_descriptor=(
                process_log.directory_descriptor
            ),
        )
        cleanup_confirmed = True
        if exit_code != 0:
            raise E2EError(
                f"The named Forge server probe exited with {exit_code}: "
                f"{read_process_tail(output_path)}"
            )

        if process_log is None:
            raise CleanupUncertainError("The generated runtime lost its process log")
        if pinned_watchdog_evidence is None:
            raise CleanupUncertainError(
                "The generated runtime lost its pinned watchdog evidence"
            )
        verify_generated_runtime(
            configuration,
            target_root,
            process_log,
            pinned_watchdog_evidence,
        )
        report_path = evidence_path(configuration, "report", target_root)
        ensure_no_symlink_components(report_path, target_root)
        ensure_regular_unlinked_file(report_path, "Dedicated-server probe report")
        reports_directory = report_path.parent
        logs_directory = evidence_path(configuration, "server_log", target_root).parent
        if {entry.name for entry in reports_directory.iterdir()} != {report_path.name}:
            raise E2EError("The probe published an unexpected report payload")
        if any(logs_directory.iterdir()):
            raise E2EError("The probe published an unexpected log payload")
        report = load_json_object(report_path, "dedicated-server probe report")
        validate_probe_report(report, configuration)
        source_log = game_directory(configuration, target_root) / "logs" / "latest.log"
        log_content = validate_server_log(source_log)
        validate_world_save(configuration, target_root)

        copied_log = evidence_path(configuration, "server_log", target_root)
        publish_bytes_exclusive(copied_log, log_content)
        if copied_log.read_bytes() != log_content:
            raise E2EError("The copied dedicated-server log differs from latest.log")
        result = launcher_result(
            configuration,
            copied_log,
            pinned_watchdog_evidence,
            process_log.directory_descriptor,
        )
        publish_json_exclusive(
            evidence_path(configuration, "launcher_result", target_root), result
        )
        publish_bytes_exclusive(
            evidence_path(configuration, "completion_marker", target_root),
            COMPLETION_MARKER_CONTENT,
        )
        run_succeeded = True
        return result
    except BaseException as run_exception:
        if not cleanup_confirmed:
            try:
                handoff_seen = False
                if process is not None:
                    handoff_path, _acknowledgement_path = server_memory_handoff_paths(
                        target_root
                    )
                    handoff_seen = handoff_path.exists() or handoff_path.is_symlink()
                pinned_watchdog_evidence = cleanup_server_launch(
                    process,
                    server_guard,
                    sampler,
                    wrapper_guard=wrapper_guard,
                    launch_anchor=launch_anchor,
                    state_root=state_root,
                    detached_observation=detached_observation,
                    unbound_handoff_seen=handoff_seen,
                    watchdog_runtime_directory_descriptor=(
                        process_log.directory_descriptor
                        if process_log is not None
                        else None
                    ),
                )
            except BaseException as cleanup_exception:
                raise E2EError(
                    "The Forge server run failed and cleanup ownership is uncertain; "
                    f"the run lock and process log were retained: {cleanup_exception}"
                ) from run_exception
            cleanup_confirmed = True
        raise
    finally:
        try:
            if output_descriptor is not None:
                os.close(output_descriptor)
            if cleanup_confirmed:
                if run_succeeded and process_log is not None:
                    unlink_owned_launch_file(process_log)
                release_owned_run_lock(run_lock)
        finally:
            try:
                close_owned_run_lock(run_lock)
            finally:
                try:
                    if wrapper_guard is not None:
                        close_owned_runtime_directory(wrapper_guard.runtime)
                    elif launch_anchor is not None:
                        close_owned_runtime_directory(launch_anchor.runtime)
                finally:
                    try:
                        if pinned_watchdog_evidence is not None:
                            close_pinned_watchdog_evidence(
                                pinned_watchdog_evidence
                            )
                    finally:
                        try:
                            if process_log is not None:
                                close_owned_launch_file(process_log)
                        finally:
                            if attempt_marker is not None:
                                close_owned_launch_file(attempt_marker)


def validate_command() -> int:
    configuration = load_configuration()
    verify_gradle_probe_definition(configuration)
    print(
        "Validated Forge dedicated-server profile: "
        f"{PROFILE_ID} / Minecraft 1.20.1 / Forge 47.4.9 / Java 17"
    )
    print(f"Named Loom task: {TASK_PATH}")
    print(f"Runtime root: {runtime_root(configuration)}")
    print("External game profiles consulted: 0")
    return 0


def provision_command() -> int:
    configuration = load_configuration()
    provision_profile(configuration)
    print(f"Provisioned repository-owned Forge server runtime: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Evidence root: {evidence_root(configuration)}")
    print("Gradle and Minecraft were not launched; external game profiles consulted: 0")
    return 0


def check_command() -> int:
    configuration = load_configuration()
    run_token = secrets.token_hex(32)
    run_lock = acquire_run_lock(configuration, STATE_ROOT, run_token)
    try:
        try:
            java_path, command = verify_environment(
                configuration,
                STATE_ROOT,
                run_lock,
            )
        except CleanupUncertainError:
            raise
        except BaseException:
            release_owned_run_lock(run_lock)
            raise
        release_owned_run_lock(run_lock)
    finally:
        close_owned_run_lock(run_lock)
    print(
        "Ready: Minecraft 1.20.1 / Forge 47.4.9 / "
        "dedicated server Java 17 / exact Gradle host JDK 21"
    )
    print(f"Runtime root: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Scenario: {SCENARIO_ID}")
    print(f"Gradle host Java: {java_path}")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    print("External game profiles consulted: 0")
    return 0


def run_command() -> int:
    configuration = load_configuration()
    result = execute_probe(configuration)
    print(f"Completed isolated Forge dedicated-server scenario: {SCENARIO_ID}")
    print(f"Exit code: {result['exit_code']}; timed out: {result['timed_out']}")
    print(f"Evidence root: {evidence_root(configuration)}")
    print("Completion marker was published last")
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Provision and run Etherology's repository-owned Forge 1.20.1 "
            "dedicated-server probe without consulting launcher profiles."
        )
    )
    parser.add_argument("action", choices=("validate", "provision", "check", "run"))
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    actions = {
        "validate": validate_command,
        "provision": provision_command,
        "check": check_command,
        "run": run_command,
    }
    try:
        return actions[arguments.action]()
    except E2EError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
