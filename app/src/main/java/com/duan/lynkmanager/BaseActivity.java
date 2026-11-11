package com.duan.lynkmanager;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/**
 * 统一把本应用的资源密度放大。
 *
 * 车机主屏 2560x1600 @ densityDpi=160，系统密度下 1dp = 1px，
 * 整套 UI 在实车上看会非常小。这里在 Activity 的 attachBaseContext 阶段
 * 换成放大后的 Configuration，dp / sp / 字号 / 网格列宽一起变大。
 *
 * 注意：必须做在 Activity 上。Application.attachBaseContext 里改配置
 * 不会传递给 Activity（Activity 的 Resources 由 ActivityThread 提供），
 * 之前那样写等于没生效。
 */
public class BaseActivity extends Activity {

    /** 相对系统密度放大倍数。1.75 → 逻辑宽度 2560/1.75 ≈ 1463dp。 */
    protected static final float UI_SCALE = 1.75f;

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration c = new Configuration(newBase.getResources().getConfiguration());
        c.densityDpi = Math.round(c.densityDpi * UI_SCALE);
        super.attachBaseContext(newBase.createConfigurationContext(c));
    }
}
