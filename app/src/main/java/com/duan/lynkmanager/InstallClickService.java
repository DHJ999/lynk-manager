package com.duan.lynkmanager;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * 安装助手无障碍服务：替用户点系统安装器上的「安装」按钮。
 *
 * 为什么必须这么做：
 * 这台车机的 PackageInstallerActivity 里，「安装」按钮设了防点击劫持
 * （filterTouchesWhenObscured），而同屏的「取消」没有。实测：
 *   input tap (831,847) 点「取消」→ 对话框关闭，有效
 *   input tap (1021,847) 点「安装」→ 完全无响应，系统日志无任何安装动作
 * 两按钮同一行、只差 190px，说明触摸被按钮自身丢弃。
 * performAction(ACTION_CLICK) 走无障碍通道，直接调 View 的 performClick，
 * 不经过触摸事件分发，是应用层唯一能绕过该限制的手段。
 *
 * 时序说明：服务是系统按需绑定的，可能在安装器弹出之后才连上，
 * 因此不能只依赖 onAccessibilityEvent。这里用「武装标志 + 轮询」：
 * 应用点记录时把 armed 置 true 并 trigger()，服务若已连上立刻开始轮询，
 * 若还没连上，则在 onServiceConnected 里发现 armed 后自行开始。
 */
public class InstallClickService extends AccessibilityService {

    private static final String PKG_INSTALLER = "com.android.packageinstaller";
    private static final long POLL_INTERVAL = 400L;
    private static final int MAX_TRIES = 75; // 400ms * 75 = 30s

    /** 待点击武装标志。只有应用主动发起安装后才为 true，平时完全不干预。 */
    static volatile boolean armed = false;

    private static InstallClickService instance;

    private Handler handler;
    private int tries;
    private boolean clicked;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!armed) {
                return;
            }
            if (scanAndClick()) {
                armed = false;
                clicked = true;
                return;
            }
            if (++tries < MAX_TRIES) {
                handler.postDelayed(this, POLL_INTERVAL);
            } else {
                android.util.Log.w("LynkMgr", "无障碍点击超时，放弃");
                armed = false;
            }
        }
    };

    /** 由 Activity 调用：武装并立即开始轮询。 */
    static void trigger() {
        armed = true;
        InstallClickService s = instance;
        if (s != null) {
            s.startPolling();
        }
    }

    /** 服务当前是否已连接（可以真正执行点击）。 */
    static boolean isReady() {
        return instance != null;
    }

    private void startPolling() {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        tries = 0;
        clicked = false;
        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, 300L);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        android.util.Log.i("LynkMgr", "安装助手无障碍服务已连接");
        // 服务晚于安装器弹出时，这里补上
        if (armed) {
            android.util.Log.i("LynkMgr", "服务连接时已武装，开始轮询");
            startPolling();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 事件只作为加速：真正的兜底是轮询
        if (!armed || clicked) {
            return;
        }
        CharSequence pkg = event == null ? null : event.getPackageName();
        if (pkg == null || !PKG_INSTALLER.contentEquals(pkg)) {
            return;
        }
        if (scanAndClick()) {
            armed = false;
            clicked = true;
        }
    }

    /**
     * 遍历所有窗口，找系统安装器的根节点并点「安装」。
     * 用 getWindows() 而不是 getRootInActiveWindow()：
     * 安装器是覆盖在应用之上的小窗口，按窗口遍历更稳。
     */
    private boolean scanAndClick() {
        // 路径1：先看当前活动窗口。安装器是 focused+active 的窗口，
        // 刚绑定的服务在 getWindows() 里可能还看不到它，这条更可靠。
        AccessibilityNodeInfo active = null;
        try {
            active = getRootInActiveWindow();
            if (active != null) {
                CharSequence p = active.getPackageName();
                if (tries % 10 == 0) {
                    android.util.Log.d("LynkMgr", "activeRoot pkg=" + p);
                }
                if (p != null && PKG_INSTALLER.contentEquals(p)) {
                    if (tryClickInstall(active)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("LynkMgr", "取活动窗口失败", t);
        } finally {
            if (active != null) {
                try {
                    active.recycle();
                } catch (Throwable ignored) {
                }
            }
        }

        // 路径2：遍历所有窗口兜底
        List<AccessibilityWindowInfo> wins;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            return false;
        }
        if (wins == null || wins.isEmpty()) {
            if (tries % 10 == 0) {
                android.util.Log.d("LynkMgr", "getWindows 为空");
            }
            return false;
        }
        if (tries % 10 == 0) {
            android.util.Log.d("LynkMgr", "窗口数=" + wins.size());
        }
        for (AccessibilityWindowInfo w : wins) {
            if (w == null) {
                continue;
            }
            AccessibilityNodeInfo root = null;
            try {
                root = w.getRoot();
                if (root == null) {
                    continue;
                }
                CharSequence p = root.getPackageName();
                if (p == null || !PKG_INSTALLER.contentEquals(p)) {
                    continue;
                }
                if (tryClickInstall(root)) {
                    return true;
                }
            } catch (Throwable t) {
                // 单个窗口异常不影响其它窗口
            } finally {
                if (root != null) {
                    try {
                        root.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return false;
    }

    /** 找到文本恰为「安装」的可点击 Button，执行无障碍点击。 */
    private boolean tryClickInstall(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes;
        try {
            nodes = root.findAccessibilityNodeInfosByText("安装");
        } catch (Throwable t) {
            return false;
        }
        if (nodes == null || nodes.isEmpty()) {
            if (tries % 10 == 0) {
                android.util.Log.d("LynkMgr", "未找到含「安装」的节点");
            }
            return false;
        }
        for (AccessibilityNodeInfo n : nodes) {
            if (n == null) {
                continue;
            }
            try {
                CharSequence txt = n.getText();
                CharSequence cls = n.getClassName();
                // 严格匹配，排除「安装未知应用」「安装包」之类
                boolean exact = txt != null && "安装".equals(txt.toString().trim());
                boolean isButton = cls != null
                        && "android.widget.Button".equals(cls.toString());
                if (!exact || !isButton) {
                    if (tries % 10 == 0) {
                        android.util.Log.d("LynkMgr", "跳过节点 text=" + txt + " cls=" + cls);
                    }
                    continue;
                }
                AccessibilityNodeInfo target = n;
                if (!target.isClickable()) {
                    AccessibilityNodeInfo parent = target.getParent();
                    int guard = 0;
                    while (parent != null && !parent.isClickable() && guard++ < 5) {
                        parent = parent.getParent();
                    }
                    if (parent != null && parent.isClickable()) {
                        target = parent;
                    }
                }
                if (target.isClickable()) {
                    boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    android.util.Log.i("LynkMgr", "无障碍点击安装 → " + ok);
                    if (ok) {
                        return true;
                    }
                }
            } finally {
                try {
                    n.recycle();
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        armed = false;
        if (handler != null) {
            handler.removeCallbacks(pollTask);
        }
        return super.onUnbind(intent);
    }
}
