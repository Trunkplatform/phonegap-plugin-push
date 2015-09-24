package com.adobe.phonegap.push;

import java.lang.Override;
import java.lang.String;

public class NotificationObject {
    private String title;
    private String message;

    public NotificationObject(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return title + " - " + message;
    }
}
