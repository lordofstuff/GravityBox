/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.gm2.gravitybox;

import com.ceco.gm2.gravitybox.ledcontrol.LedSettings;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLedControl {
    private static final String TAG = "GB:ModLedControl";
    private static final boolean DEBUG = false;
    private static final String CLASS_USER_HANDLE = "android.os.UserHandle";

    private static XSharedPreferences mPrefs;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote() {
        mPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                    String.class, int.class, Notification.class, notifyHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notifyAsUser",
                    String.class, int.class, Notification.class, CLASS_USER_HANDLE, notifyHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static XC_MethodHook notifyHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                mPrefs.reload();
                if (mPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false)) {
                    if (DEBUG) log("Ultimate notification control feature locked.");
                    return;
                }

                final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                final String pkgName = context.getPackageName();
                final LedSettings ls = LedSettings.deserialize(mPrefs.getStringSet(pkgName, null));
                if (DEBUG) log(pkgName + ": " + ls.toString());
                if (!ls.getEnabled()) return;

                Notification n = (Notification) param.args[2];
                if (((n.flags & Notification.FLAG_ONGOING_EVENT) == Notification.FLAG_ONGOING_EVENT) &&
                        !ls.getOngoing()) {
                    if (DEBUG) log("Ongoing led control disabled. Forcing LED Off");
                    return;
                }

                // lights
                n.defaults &= ~Notification.DEFAULT_LIGHTS;
                n.flags |= Notification.FLAG_SHOW_LIGHTS;
                n.ledOnMS = ls.getLedOnMs();
                n.ledOffMS = ls.getLedOffMs();
                n.ledARGB = ls.getColor();

                // sound
                if (ls.getSoundOverride()) {
                    n.defaults &= ~Notification.DEFAULT_SOUND;
                    n.sound = ls.getSoundUri();
                }
                if (ls.getSoundOnlyOnce()) {
                    n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
                } else {
                    n.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
                }
                if (ls.getInsistent()) {
                    n.flags |= Notification.FLAG_INSISTENT;
                } else {
                    n.flags &= ~Notification.FLAG_INSISTENT;
                }

                // vibration
                if (ls.getVibrateOverride() && ls.getVibratePattern() != null) {
                    n.defaults &= ~Notification.DEFAULT_VIBRATE;
                    n.vibrate = ls.getVibratePattern();
                }

                if (DEBUG) log("Notification info: defaults=" + n.defaults + "; flags=" + n.flags);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };
}
