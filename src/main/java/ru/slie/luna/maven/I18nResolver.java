package ru.slie.luna.maven;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class I18nResolver {
    ResourceBundle bundle = ResourceBundle.getBundle("messages");

    public String t(String message, Object ...args) {
        if (args.length > 0) {
            return MessageFormat.format(bundle.getString(message), args);
        }

        return bundle.getString(message);
    }
}
