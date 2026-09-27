package de.robv.android.xposed;

/**
 * 编译桩：真实实现由 LSPosed/Xposed 运行时注入，不会打包进 APK。
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
