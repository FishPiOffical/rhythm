package org.b3log.symphony.util;

import org.b3log.symphony.model.Pointtransfer;
import org.json.JSONObject;

/** New rename charges keep the two names in separate bounded ledger columns. */
public final class UserNamePointData {
    public static final String SCENE = "settings.user-name";
    private UserNamePointData() { }

    public static String description(JSONObject record, String template) {
        if (SCENE.equals(record.optString(Pointtransfer.SOURCE_SCENE))) {
            return template.replace("{oldName}", Escapes.escapeHTML(record.optString(Pointtransfer.MEMO)))
                    .replace("{newName}", Escapes.escapeHTML(record.optString(Pointtransfer.DATA_ID)));
        }
        // Preserve the interpretation of historical rows written by the legacy endpoint.
        final String[] names = record.optString(Pointtransfer.DATA_ID).split("-");
        return template.replace("{oldName}", names[0]).replace("{newName}", names[1]);
    }
}
