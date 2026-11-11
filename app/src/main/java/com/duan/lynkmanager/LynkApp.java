package com.duan.lynkmanager;

import android.app.Application;

/**
 * 仅作为应用入口占位。UI 密度放大放在 {@link BaseActivity} 里做，
 * 因为 Application 层改 Configuration 不会传递给 Activity。
 */
public class LynkApp extends Application {
}
