package ru.slie.luna.maven.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PluginResourceType {
    COMP("comp"),
    CSS("css"),
    I18N("i18n"),
    ANY("any");

    private final String type;

    PluginResourceType(String type) {
        this.type = type;
    }

    @JsonCreator
    public static PluginResourceType getForType(String type) {
        for (PluginResourceType resourceType: PluginResourceType.values()) {
            if (type.equalsIgnoreCase(resourceType.type)) {
                return resourceType;
            }
        }

        return PluginResourceType.ANY;
    }

    @JsonValue
    public String getType() {
        return type;
    }
}
