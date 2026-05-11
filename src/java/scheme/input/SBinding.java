package scheme.input;

import arc.input.*;

public class SBinding {
    public static final KeyBind

    teleportBind = KeyBind.add("scheme_teleport",  KeyCode.unset, "SchemeSize"),
    despawnBind = KeyBind.add("scheme_despawn",   KeyCode.unset, "SchemeSize"),
    teamBind = KeyBind.add("scheme_team",   KeyCode.unset, "SchemeSize"),
    aiBind = KeyBind.add("scheme_ai",   KeyCode.unset, "SchemeSize"),
    coreBind = KeyBind.add("scheme_core",   KeyCode.unset, "SchemeSize"),
    unitBind = KeyBind.add("scheme_unit",   KeyCode.unset, "SchemeSize"),
    unitSpawnBind = KeyBind.add("scheme_unit_spawn",   KeyCode.unset, "SchemeSize"),
    effectBind = KeyBind.add("scheme_effect",   KeyCode.unset, "SchemeSize"),
    itemBind = KeyBind.add("scheme_item",   KeyCode.unset, "SchemeSize"),
    deletePLayer = KeyBind.add("scheme_delete_player",   KeyCode.unset, "SchemeSize");
    public static void load(){
        for(KeyBind bind : KeyBind.all) bind.load();
    }
}
