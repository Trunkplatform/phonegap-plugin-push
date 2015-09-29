package com.adobe.phonegap.push;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.adobe.phonegap.push.NotificationObject;

import com.google.android.gcm.GCMBaseIntentService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.google.common.collect.LinkedListMultimap;

import java.util.Random;

import static com.adobe.phonegap.push.PushConstants.*;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

    private static final String LOG_TAG = "PushPlugin_GCMIntentService";
    static LinkedListMultimap<String, NotificationObject> messageList = LinkedListMultimap.create();
    int notificationId = 0;

    public void setNotification(String id, NotificationObject message) {
        if (id != null && !id.isEmpty() && message != null) {
            messageList.put(id, message);
        }
    }

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    public void onRegistered(Context context, String regId) {

        Log.v(LOG_TAG, "onRegistered: " + regId);

        try {
            JSONObject json = new JSONObject().put(REGISTRATION_ID, regId);

            Log.v(LOG_TAG, "onRegistered: " + json.toString());

            PushPlugin.sendEvent(json);
        } catch (JSONException e) {
            // No message to the user is sent, JSON failed
            Log.e(LOG_TAG, "onRegistered: JSON exception");
        }
    }

    @Override
    public void onUnregistered(Context context, String regId) {
        Log.d(LOG_TAG, "onUnregistered - regId: " + regId);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        Log.d(LOG_TAG, "onMessage - context: " + context);

        // Extract the payload from the message
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (PushPlugin.isInForeground()) {
                extras.putBoolean(FOREGROUND, true);
                PushPlugin.sendExtras(extras);
            } else if (extras.containsKey("message") && extras.getString("message").length() != 0) {
                extras.putBoolean(FOREGROUND, false);
                createNotification(context, extras);
            } else {
                clearNotificationRelatedToStream(extras);
                if (messageList.isEmpty()) {
                    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancelAll();
                } else {
                    createNotification(context, extras);
                }
            }
        }
    }

    private void clearNotificationRelatedToStream(Bundle extras) {
        String streamId = extras.getString("payload", "");
        if (!streamId.isEmpty()) {
            messageList.removeAll(streamId);
        }
    }

    public void createNotification(Context context, Bundle extras) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String appName = getAppName(this);
        String packageName = context.getPackageName();
        Resources resources = context.getResources();

        Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra(PUSH_BUNDLE, extras);
        notificationIntent.putExtra(NOT_ID, notificationId);

        int requestCode = new Random().nextInt();
        PendingIntent contentIntent = PendingIntent.getActivity(this, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle(getString(extras, TITLE))
                        .setTicker(getString(extras, TITLE))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true);

        SharedPreferences prefs = context.getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
        String localIcon = prefs.getString(ICON, null);
        String localIconColor = prefs.getString(ICON_COLOR, null);
        boolean soundOption = prefs.getBoolean(SOUND, true);
        boolean vibrateOption = prefs.getBoolean(VIBRATE, true);
        Log.d(LOG_TAG, "stored icon=" + localIcon);
        Log.d(LOG_TAG, "stored iconColor=" + localIconColor);
        Log.d(LOG_TAG, "stored sound=" + soundOption);
        Log.d(LOG_TAG, "stored vibrate=" + vibrateOption);

        /*
         * Notification Vibration
         */

        setNotificationVibration(extras, vibrateOption, mBuilder);

        /*
         * Notification Icon Color
         *
         * Sets the small-icon background color of the notification.
         * To use, add the `iconColor` key to plugin android options
         *
         */
        setNotificationIconColor(getString(extras, "color"), mBuilder, localIconColor);

        /*
         * Notification Icon
         *
         * Sets the small-icon of the notification.
         *
         * - checks the plugin options for `icon` key
         * - if none, uses the application icon
         *
         * The icon value must be a string that maps to a drawable resource.
         * If no resource is found, falls
         *
         */
        setNotificationSmallIcon(context, extras, packageName, resources, mBuilder, localIcon);

        /*
         * Notification Large-Icon
         *
         * Sets the large-icon of the notification
         *
         * - checks the gcm data for the `image` key
         * - checks to see if remote image, loads it.
         * - checks to see if assets image, Loads It.
         * - checks to see if resource image, LOADS IT!
         * - if none, we don't set the large icon
         *
         */
        setNotificationLargeIcon(extras, packageName, resources, mBuilder);

        /*
         * Notification Sound
         */
        if (soundOption) {
            setNotificationSound(context, extras, mBuilder);
        }

        /*
         *  LED Notification
         */
        setNotificationLedColor(extras, mBuilder);

        /*
         *  Priority Notification
         */
        setNotificationPriority(extras, mBuilder);

        /*
         * Notification message
         */
        setNotificationMessage(extras, mBuilder);

        /*
         * Notification count
         */
        setNotificationCount(extras, mBuilder);

        /*
         * Notication add actions
         */
        createActions(extras, mBuilder, resources, packageName);

        mNotificationManager.notify(appName, notificationId, mBuilder.build());
    }

    private void createActions(Bundle extras, NotificationCompat.Builder mBuilder, Resources resources, String packageName) {
        Log.d(LOG_TAG, "create actions");
        String actions = getString(extras, ACTIONS);
        if (actions != null) {
            try {
                JSONArray actionsArray = new JSONArray(actions);
                for (int i = 0; i < actionsArray.length(); i++) {
                    Log.d(LOG_TAG, "adding action");
                    JSONObject action = actionsArray.getJSONObject(i);
                    Log.d(LOG_TAG, "adding callback = " + action.getString(CALLBACK));
                    Intent intent = new Intent(this, PushHandlerActivity.class);
                    intent.putExtra(CALLBACK, action.getString(CALLBACK));
                    intent.putExtra(PUSH_BUNDLE, extras);
                    PendingIntent pIntent = PendingIntent.getActivity(this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                    mBuilder.addAction(resources.getIdentifier(action.getString(ICON), DRAWABLE, packageName),
                            action.getString(TITLE), pIntent);
                }
            } catch (JSONException e) {
                // nope
            }
        }
    }

    private void setNotificationCount(Bundle extras, NotificationCompat.Builder mBuilder) {
        String msgcnt = getString(extras, MSGCNT);
        if (msgcnt == null) {
            msgcnt = getString(extras, BADGE);
        }
        if (msgcnt != null) {
            mBuilder.setNumber(Integer.parseInt(msgcnt));
        }
    }

    private void setNotificationVibration(Bundle extras, Boolean vibrateOption, NotificationCompat.Builder mBuilder) {
        String vibrationPattern = getString(extras, VIBRATION_PATTERN);
        if (vibrationPattern != null) {
            String[] items = vibrationPattern.replaceAll("\\[", "").replaceAll("\\]", "").split(",");
            long[] results = new long[items.length];
            for (int i = 0; i < items.length; i++) {
                try {
                    results[i] = Long.parseLong(items[i]);
                } catch (NumberFormatException nfe) {}
            }
            mBuilder.setVibrate(results);
        } else {
            if (vibrateOption) {
                mBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
            }
        }
    }

    private NotificationObject newNotificationObject(String title, String message) {
        if (title == null || message == null || title.isEmpty() || message.isEmpty()) return null;
        return new NotificationObject(title, message);
    }

    private void setNotificationMessage(Bundle extras, NotificationCompat.Builder mBuilder) {
        String messageText = getMessageText(extras);
        String title = getString(extras, TITLE);
        NotificationObject message = newNotificationObject(title, messageText);

        String style = "inbox";

        if (STYLE_INBOX.equals(style)) {
            String streamId = extras.getString("payload", "");
            setNotification(streamId, message);

            Integer sizeList = messageList.size();
            if (message != null) {
                mBuilder.setContentText(message.getMessage());
            } else if (sizeList == 1) {
                message = messageList.values().get(0);
                mBuilder.setContentText(message.getMessage()).setContentTitle(message.getTitle());
            }

            if (sizeList > 1) {
                String sizeListMessage = sizeList.toString();
                String stacking = sizeList + " new messages";
                if (getString(extras, SUMMARY_TEXT) != null) {
                    stacking = getString(extras, SUMMARY_TEXT);
                    stacking = stacking.replace("%n%", sizeListMessage);
                }
                NotificationCompat.InboxStyle notificationInbox = new NotificationCompat.InboxStyle()
                        .setBigContentTitle(sizeList + " new messages")
                        .setSummaryText(stacking);

                for (int i = sizeList - 1; i >= 0; i--) {
                    message = messageList.values().get(i);
                    notificationInbox.addLine(Html.fromHtml("<b>" + message.getTitle() + "</b> - " + message.getMessage()));
                }

                mBuilder.setStyle(notificationInbox);
            }
        } else if (STYLE_PICTURE.equals(style)) {
            setNotification("", null);

            NotificationCompat.BigPictureStyle bigPicture = new NotificationCompat.BigPictureStyle();
            bigPicture.bigPicture(getBitmapFromURL(getString(extras, PICTURE)));
            bigPicture.setBigContentTitle(title);
            bigPicture.setSummaryText(getString(extras, SUMMARY_TEXT));

            mBuilder.setContentTitle(title);
            mBuilder.setContentText(messageText);

            mBuilder.setStyle(bigPicture);
        } else {
            setNotification("", null);

            NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();

            if (messageText != null) {
                mBuilder.setContentText(Html.fromHtml(messageText));

                bigText.bigText(messageText);
                bigText.setBigContentTitle(title);

                String summaryText = getString(extras, SUMMARY_TEXT);
                if (summaryText != null) {
                    bigText.setSummaryText(summaryText);
                }

                mBuilder.setStyle(bigText);
            }
            /*
            else {
                mBuilder.setContentText("<missing message content>");
            }
            */
        }
    }

    private String getString(Bundle extras, String key) {
        String message = extras.getString(key);
        if (message == null) {
            message = extras.getString(GCM_NOTIFICATION + "." + key);
        }
        return message;
    }

    private String getString(Bundle extras, String key, String defaultString) {
        String message = extras.getString(key);
        if (message == null) {
            message = extras.getString(GCM_NOTIFICATION + "." + key, defaultString);
        }
        return message;
    }

    private String getMessageText(Bundle extras) {
        String message = getString(extras, MESSAGE);
        if (message == null) {
            message = getString(extras, BODY);
        }
        return message;
    }

    private void setNotificationSound(Context context, Bundle extras, NotificationCompat.Builder mBuilder) {
        String soundname = getString(extras, SOUNDNAME);
        if (soundname == null) {
            soundname = getString(extras, SOUND);
        }
        if (soundname != null) {
            Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + context.getPackageName() + "/raw/" + soundname);
            Log.d(LOG_TAG, sound.toString());
            mBuilder.setSound(sound);
        } else {
            mBuilder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
        }
    }

    private void setNotificationLedColor(Bundle extras, NotificationCompat.Builder mBuilder) {
        String ledColor = getString(extras, LED_COLOR);
        if (ledColor != null) {
            // Converts parse Int Array from ledColor
            String[] items = ledColor.replaceAll("\\[", "").replaceAll("\\]", "").split(",");
            int[] results = new int[items.length];
            for (int i = 0; i < items.length; i++) {
                try {
                    results[i] = Integer.parseInt(items[i]);
                } catch (NumberFormatException nfe) {}
            }
            if (results.length == 4) {
                mBuilder.setLights(Color.argb(results[0], results[1], results[2], results[3]), 500, 500);
            } else {
                Log.e(LOG_TAG, "ledColor parameter must be an array of length == 4 (ARGB)");
            }
        }
    }

    private void setNotificationPriority(Bundle extras, NotificationCompat.Builder mBuilder) {
        String priorityStr = getString(extras, PRIORITY);
        if (priorityStr != null) {
            try {
                Integer priority = Integer.parseInt(priorityStr);
                if (priority >= NotificationCompat.PRIORITY_MIN && priority <= NotificationCompat.PRIORITY_MAX) {
                    mBuilder.setPriority(priority);
                } else {
                    Log.e(LOG_TAG, "Priority parameter must be between -2 and 2");
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    private void setNotificationLargeIcon(Bundle extras, String packageName, Resources resources, NotificationCompat.Builder mBuilder) {
        String gcmLargeIcon = getString(extras, IMAGE); // from gcm
        if (gcmLargeIcon != null) {
            if (gcmLargeIcon.startsWith("http://") || gcmLargeIcon.startsWith("https://")) {
                mBuilder.setLargeIcon(getBitmapFromURL(gcmLargeIcon));
                Log.d(LOG_TAG, "using remote large-icon from gcm");
            } else {
                AssetManager assetManager = getAssets();
                InputStream istr;
                try {
                    istr = assetManager.open(gcmLargeIcon);
                    Bitmap bitmap = BitmapFactory.decodeStream(istr);
                    mBuilder.setLargeIcon(bitmap);
                    Log.d(LOG_TAG, "using assets large-icon from gcm");
                } catch (IOException e) {
                    int largeIconId = 0;
                    largeIconId = resources.getIdentifier(gcmLargeIcon, DRAWABLE, packageName);
                    if (largeIconId != 0) {
                        Bitmap largeIconBitmap = BitmapFactory.decodeResource(resources, largeIconId);
                        mBuilder.setLargeIcon(largeIconBitmap);
                        Log.d(LOG_TAG, "using resources large-icon from gcm");
                    } else {
                        Log.d(LOG_TAG, "Not setting large icon");
                    }
                }
            }
        }
    }

    private void setNotificationSmallIcon(Context context, Bundle extras, String packageName, Resources resources, NotificationCompat.Builder mBuilder, String localIcon) {
        int iconId = 0;
        String icon = getString(extras, ICON);
        if (icon != null) {
            iconId = resources.getIdentifier(icon, DRAWABLE, packageName);
            Log.d(LOG_TAG, "using icon from plugin options");
        } else if (localIcon != null) {
            iconId = resources.getIdentifier(localIcon, DRAWABLE, packageName);
            Log.d(LOG_TAG, "using icon from plugin options");
        }
        if (iconId == 0) {
            Log.d(LOG_TAG, "no icon resource found - using application icon");
            iconId = context.getApplicationInfo().icon;
        }
        mBuilder.setSmallIcon(iconId);
    }

    private void setNotificationIconColor(String color, NotificationCompat.Builder mBuilder, String localIconColor) {
        int iconColor = 0;
        if (color != null) {
            try {
                iconColor = Color.parseColor(color);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "couldn't parse color from android options");
            }
        } else if (localIconColor != null) {
            try {
                iconColor = Color.parseColor(localIconColor);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "couldn't parse color from android options");
            }
        }
        if (iconColor != 0) {
            mBuilder.setColor(iconColor);
        }
    }

    public Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getAppName(Context context) {
        CharSequence appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
        return (String) appName;
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.e(LOG_TAG, "onError - errorId: " + errorId);
        // if we are in the foreground, just send the error
        if (PushPlugin.isInForeground()) {
            PushPlugin.sendError(errorId);
        }
    }

    private int parseInt(String value, Bundle extras) {
        int retval = 0;

        try {
            retval = Integer.parseInt(getString(extras, value));
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "Number format exception - Error parsing " + value + ": " + e.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Number format exception - Error parsing " + value + ": " + e.getMessage());
        }

        return retval;
    }
}
