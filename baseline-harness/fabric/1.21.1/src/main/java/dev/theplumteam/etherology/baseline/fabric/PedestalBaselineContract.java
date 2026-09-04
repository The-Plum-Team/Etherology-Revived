package dev.theplumteam.etherology.baseline.fabric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable published-0.1.7 Pedestal expectations shared by the native scenario.
 */
final class PedestalBaselineContract {

    static final String SCENARIO_ID = "pedestal-baseline";
    static final String WORLD_DIRECTORY_NAME =
            "etherology-original-pedestal-baseline-world";
    static final String WORLD_DISPLAY_NAME =
            "Etherology Original 0.1.7 Pedestal";
    static final long WORLD_SEED = 0x4554485045443131L;
    static final int EXPECTED_STATE_COUNT = 1024;
    static final int EXPECTED_MULTIPART_CLAUSE_COUNT = 68;
    static final int REQUIRED_COMPLETED_RENDERS = 120;
    static final int REQUIRED_LIGHT_READY_CLIENT_TICKS = 20;
    static final List<String> SCREENSHOT_FILE_NAMES = List.of(
            "pedestal-gallery.png",
            "pedestal-transition-drops.png",
            "pedestal-persistence-initial.png",
            "pedestal-persistence-reopened.png"
    );
    static final List<String> CAPTURE_STEPS = List.of(
            "gallery",
            "transition-drops",
            "persistence-initial",
            "persistence-reopened"
    );
    static final List<String> INTERACTION_STEPS = List.of(
            "red-carpet-stack-place",
            "different-carpet-stack-noop",
            "single-carpet-swap",
            "same-carpet-retrieve",
            "diamond-place",
            "different-item-noop",
            "full-same-item-noop",
            "same-item-retrieve",
            "empty-hand-item-first",
            "empty-hand-carpet-second"
    );
    static final List<String> ITEM_DISPENSER_DIRECTIONS = List.of(
            "down", "up", "north", "south", "west", "east"
    );
    static final List<String> CARPET_DISPENSER_DIRECTIONS = List.of(
            "north", "south", "west", "east"
    );
    static final List<String> GUARDED_CARPET_DISPENSER_DIRECTIONS = List.of(
            "down", "up"
    );
    static final String VERTICAL_CARPET_LIMITATION =
            "hash-pinned-published-0.1.7-bytecode-not-executed-safety-guard: "
                    + "empty-carpet-slot vertical direction reaches the "
                    + "horizontal-only facing property";
    static final Map<String, ResourcePin> RESOURCE_PINS = createResourcePins();

    private PedestalBaselineContract() {
    }

    static List<String> assertionNames() {
        List<String> names = new java.util.ArrayList<>(List.of(
                "fabric_mod_loaded:etherology",
                "pedestal_resources_exact",
                "pedestal_blockstate_multipart_count_exact",
                "registry:block:etherology:pedestal",
                "registry:item:etherology:pedestal",
                "registry:block_entity_type:etherology:pedestal_block_entity",
                "pedestal_runtime_block_class_exact",
                "pedestal_runtime_block_entity_class_exact",
                "pedestal_block_item_mapping_exact",
                "pedestal_translation_exact",
                "pedestal_default_properties_exact",
                "pedestal_state_count_exact",
                "pedestal_state_network_ids_exact",
                "pedestal_horizontal_facing_values_exact",
                "pedestal_pickaxe_tag_exact",
                "pedestal_recipe_exact",
                "pedestal_advancement_exact",
                "pedestal_loot_table_exact",
                "pedestal_self_drop_exact",
                "pedestal_native_standalone_placement_exact",
                "pedestal_native_waterlogged_placement_exact",
                "pedestal_outline_shapes_exact",
                "pedestal_stack_shape_transitions_exact",
                "pedestal_block_entity_presence_by_shape_exact",
                "pedestal_interaction_sequence_exact",
                "pedestal_inventory_two_max_one_slots_exact",
                "pedestal_sided_inventory_closed_exact",
                "pedestal_nbt_items_exact",
                "pedestal_nbt_removed_flag_exact",
                "pedestal_item_dispenser_all_six_directions_exact",
                "pedestal_carpet_dispenser_horizontal_directions_exact",
                "pedestal_occupied_carpet_falls_through_to_display_exact",
                "pedestal_full_target_falls_through_to_generic_item_ejection_exact",
                "pedestal_stack_transition_drops_exact",
                "pedestal_stack_transition_stale_block_entity_removed",
                "pedestal_stack_transition_client_block_entity_removed",
                "pedestal_replacement_drops_exact",
                "pedestal_replacement_stale_block_entity_removed",
                "pedestal_replacement_client_block_entity_removed",
                "pedestal_gallery_server_snapshot_exact",
                "pedestal_transition_server_snapshot_exact",
                "pedestal_persistence_initial_server_snapshot_exact",
                "pedestal_forced_world_save_exact",
                "pedestal_full_restart_completed",
                "pedestal_reopened_server_snapshot_exact",
                "pedestal_restart_persistence_exact",
                "packaged_root_jar:etherology",
                "packaged_root_jar:etherology_original_baseline_harness",
                "live_world_identity",
                "isolated_save_directory_present"
        ));
        for (String step : CAPTURE_STEPS) {
            names.add("client_snapshot_exact:" + step);
            names.add("native_framebuffer_dimensions:" + step);
            names.add("completed_world_renders_before_capture:" + step);
            names.add("capture_render_ready:" + step);
            names.add("capture_camera_exact:" + step);
            names.add("native_screenshot_written:" + step);
        }
        return List.copyOf(names);
    }

    private static Map<String, ResourcePin> createResourcePins() {
        Map<String, ResourcePin> pins = new LinkedHashMap<>();
        add(pins, "assets/etherology/blockstates/pedestal.json", 9856,
                "db63a8b2b8006b8c7d74cae8f3a7e8fe06cc33d41a30c9f13f5c9b15c3040426");
        add(pins, "assets/etherology/lang/en_us.json", 11371,
                "5ec70bb4100e112b984d9b79ebf7ca9fd5c1d77522894211658d27952fa7a315");
        add(pins, "assets/etherology/lang/ru_ru.json", 14436,
                "9f675b42ae503d32399d978f1eeb470e8181ba7503a73b3798426e74176e67f8");
        add(pins, "assets/etherology/models/block/pedestal.json", 1488,
                "3c204c693e8a480947b64837c5972b5ab0383ca148a793b8eaa3d706a3e4256f");
        add(pins, "assets/etherology/models/block/pedestal_black.json", 515,
                "9f5ee4bcd6ff4b7b4110c584269aa1793e55f97c9463ae0ee0ad6b08104a1b61");
        add(pins, "assets/etherology/models/block/pedestal_blue.json", 512,
                "6ea36c4dcdfb40612d51cc2f71e0932d5aa083f5132bb6f102d9e1d0960f4192");
        add(pins, "assets/etherology/models/block/pedestal_bottom.json", 1501,
                "b2bb22bf4410b024ce5c3d765b2abb4e27d34e30d7e38466a93f1caef2f58616");
        add(pins, "assets/etherology/models/block/pedestal_brown.json", 515,
                "2dd219781db7a7fb1dbcfdfc37f0941a01a05b731877b92523332f0b6c59d86d");
        add(pins, "assets/etherology/models/block/pedestal_column.json", 1092,
                "9b574dfa2302f085e447c32fc2d1ccab0993bdabc5efe83d4a24e68f90397863");
        add(pins, "assets/etherology/models/block/pedestal_cyan.json", 512,
                "1b768f236f122bf2c7002c6038f8a3c28dfac6f5e5bb686e67ec25053cd16673");
        add(pins, "assets/etherology/models/block/pedestal_gray.json", 512,
                "cfc48aa14cbcb90ed9b867536bb9573916876beb74c6ffccd2d920caa40d8ea1");
        add(pins, "assets/etherology/models/block/pedestal_green.json", 515,
                "cdc9f0914aaa9d9401195e7493ddd16d85b45f79c14faabc0f0da1949757d0ae");
        add(pins, "assets/etherology/models/block/pedestal_light_blue.json", 530,
                "e49105d1a0c0f76e841aa015c75c52947edc68c388df27bfef8ef7382323c8ce");
        add(pins, "assets/etherology/models/block/pedestal_light_gray.json", 530,
                "6666718c294c0d6ad734762532267ad03afb06080ad302d1f8e14793559d68cb");
        add(pins, "assets/etherology/models/block/pedestal_lime.json", 512,
                "efad7ce916fca70606e7e6d8429ac97275479bd0abba86667325da2047f7e420");
        add(pins, "assets/etherology/models/block/pedestal_magenta.json", 521,
                "fc5c9df704fefc07b53a75f4be1e67dcc3b3a3adfcf1822bd5dc5726f736260d");
        add(pins, "assets/etherology/models/block/pedestal_orange.json", 518,
                "f5d7fb5e27a2f0db80964f79378e1c2ff3b6e3c7aab4c9629fe87e9f6aad80d3");
        add(pins, "assets/etherology/models/block/pedestal_pink.json", 512,
                "8b60b0194b96a86b1b7a5eed43d1ca2abaaea502df3aea6cb8f46f5d9a9dd3f7");
        add(pins, "assets/etherology/models/block/pedestal_purple.json", 518,
                "450d32ef09ec164179f2acbb7fe0415d0c8d0cc9cbd91663753cfe56397ca146");
        add(pins, "assets/etherology/models/block/pedestal_red.json", 509,
                "4a9d87f2e533c342c4b6c00cf601b94956cfd22cd80449e651ab6db363bf13e6");
        add(pins, "assets/etherology/models/block/pedestal_top.json", 1489,
                "5c695e4c806f62420ac9f7c426917aa6b14cb19139d47983fbcf22f46f54ce2e");
        add(pins, "assets/etherology/models/block/pedestal_white.json", 515,
                "7c97f1c78ebb0bba7157c06f7752b1d1d86c8977f7cd7e0a1685da42c4200cab");
        add(pins, "assets/etherology/models/block/pedestal_yellow.json", 518,
                "8516bcf02362423947c24989dcabb476908556c35742bebfe669a0530a3db7ea");
        add(pins, "assets/etherology/models/item/pedestal.json", 43,
                "b8a810118418a416ee14bbb4ef10ae12b94bdef25e5485274cadb4d8d7a663d7");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side.png", 259,
                "e4def33b8d4e0ece19ea4156fdc3a2411d9b21d6eb2b653e73f65ec189c234c3");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_black.png", 236,
                "cfaa18fd7f5a844f8d788051c40043d3b6a3dcfda6fc964dd64a87f705bd0096");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_blue.png", 227,
                "e0cf356f6959bb10dd2d30ff422c2d10380ea41b98949eddf83d3357d76b6458");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_bottom.png", 229,
                "b9b6acb5e3efb56151d0f829b066d3dc1f8490477e433b8630733287a4d944cd");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_brown.png", 224,
                "a2b0d92ea3f8760c4816e2e2d442e358e607047e2088c5c1c1ee641858dd3e01");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_column.png", 204,
                "8870293b6cce511ca06a455f6250d85b7ef589ef42d5d4c8170d45eba366b501");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_cyan.png", 226,
                "dbb1d2e2778d1d6c01f468a916e6774bf972e5d72f1dfd47a4f9060aa4aedffe");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_gray.png", 220,
                "2578f2173a31480817804450a78b490c11b468749dbf16a50c5110367093c9e1");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_green.png", 218,
                "be0af581f36f06ec5159315336ae526e126eadcc43729e51d9467f467f1fd6ba");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_light_blue.png", 217,
                "9c5781ab162bbc9b96e33a31bfaf93a01759bb0b42d05d374293203b7e59e419");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_light_gray.png", 272,
                "e24724223acc3ac3b0daf0f72305882345c01140cea0bea8e43955b04db7d1cc");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_lime.png", 229,
                "33019ea73ee53878a8651fe2e7f021eba580cdd30db6cec7d90c3cfaf316127b");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_magenta.png", 222,
                "3f837e1a46c92f86df6cd5e8267237c04e1e9909bd73c5daf28c0da8eb0bc81c");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_orange.png", 239,
                "e2d3ce3a1b8776919ed72153aad936ecc18cf689a9f4e3cbded1a8defe746382");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_pink.png", 239,
                "86b625f9c8367a1640a3f4bc5297908bcc39d1d567e9369cb6e5c99459039fab");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_purple.png", 218,
                "98dab9faed07098b21baa42edb44520f21d93ed5ebbe4776a384a4e795699f40");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_red.png", 232,
                "c3df2cacd323dee36811f0b527aa603d9ebaba2da405366928401e7d5445bb26");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_top.png", 248,
                "ca26b7b90d8bad45845d993daae53b7b4875e604a591fcffb7a7b724d11aa1c5");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_white.png", 236,
                "487c8dbd5488c614ec60e1f86afbeafbcabd0d3bcd21fa51da2558d89bdc10f7");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_side_yellow.png", 235,
                "1b81740f636512b5b07e3c599d122a0eb1f9a811c462658c80786e4151b0ced5");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top.png", 682,
                "77ee8f79b7907af2e1eed7853f6b7acd001e3c80ff0e61c69c87c5df0534bdd2");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_black.png", 589,
                "3f88b1dfd4cf50f12ca5736c97af5aab63cd7d6a39ec15760672a011defac1ae");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_blue.png", 589,
                "88f3b43fe46d8107732bbe180127aaa912f8c1a552f06bf00e6bb3b48b3630e2");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_brown.png", 590,
                "b78f61691fb53ae27fd85fccbcfc581c9aa5b4ae5254958c720c765d39f3105b");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_cyan.png", 585,
                "1c441f4012923127c25ca9012545b8aa4754dabe7522065a6a0620e3dd952af3");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_gray.png", 590,
                "81417206bd9c33bf20cdffefdbec6e30230a32c25b331685a997879907db7d68");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_green.png", 594,
                "8e822def5fb114ab07ddd9a0d1e0320c3dc68905feb78b00f4d6cf56448d30f1");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_light_blue.png", 593,
                "c88abf148e42b201199617b19ff7140923f878bdce79aa7b4097e486b3a026bb");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_light_gray.png", 644,
                "6c925999b0354dca4a06ffb8a62c015fe67e148cfdc0ecd6bc22bf041f71d57c");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_lime.png", 588,
                "4a7b50f71bad15393db2cba68b3ec531611a94bc1a99fba68bf5c5146a078769");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_magenta.png", 590,
                "30a2bafd83eb2582afea54760f040b06edbd732160f8af7e1ad5c068ecca80f8");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_orange.png", 596,
                "14f86bbca2d21ae00ac839a7001eeed5e198540c9a602851a0f2757de6d0429b");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_pink.png", 565,
                "14658a775881b79e7300af3e448d857b34371910811e21ae32792475a2584a28");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_purple.png", 597,
                "e6750987ca0043d9107c1cc204671c77a4e89332dcb5d1cf605039bcb4dc98ca");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_red.png", 573,
                "a2f6367dfb9940b0f33e812c2793799e2d2e238a7278ef22cf9bd0b53720e067");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_white.png", 586,
                "d96f5a027e0a439c3a60155af47324f77b4a70ed998a0a22f4b4efb59133a37b");
        add(pins, "assets/etherology/textures/block/pedestal_ethereal_top_yellow.png", 580,
                "a127b7fc3a2c869d137c801ecb79cf612375836557ae65f2bf56436a21363866");
        add(pins, "data/etherology/advancement/recipes/decorations/pedestal.json", 569,
                "9e2cfbf250471f39fba010710b993de77d6b9d689db316b4dbf01b87a09c90cc");
        add(pins, "data/etherology/loot_table/blocks/pedestal.json", 335,
                "e4a0dbb0b45a5a9036fb0eba4ea23352aa19c15e618121179c368356650483c5");
        add(pins, "data/etherology/recipe/pedestal.json", 326,
                "1bbcbacd6f771b2b69dbe50006613a28655e6aac0b9c5cd4497f5dd93f86928a");
        if (pins.size() != 64) {
            throw new IllegalStateException("Pedestal resource pin inventory drifted");
        }
        return Collections.unmodifiableMap(pins);
    }

    private static void add(
            Map<String, ResourcePin> pins,
            String path,
            long size,
            String sha256
    ) {
        if (pins.put(path, new ResourcePin(size, sha256)) != null) {
            throw new IllegalStateException("Duplicate Pedestal resource pin: " + path);
        }
    }

    record ResourcePin(long size, String sha256) {
    }
}
