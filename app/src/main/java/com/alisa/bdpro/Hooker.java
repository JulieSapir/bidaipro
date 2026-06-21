package com.alisa.bdpro;

import org.json.JSONObject;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hooker implements IXposedHookLoadPackage {
    private static final String TAG = "BDAIHook";
    private static final String TARGET_PACKAGE = "com.gaojiua.bd";
    private static final String TARGET_API = "/release/api/user/data/core/";
    private static final String OKHTTP_PACKAGE = "dc.squareup.okhttp3";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!TARGET_PACKAGE.equals(loadPackageParam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Hooking " + TARGET_PACKAGE + "...");
        hookRealCall(loadPackageParam.classLoader);
    }

    private void hookRealCall(final ClassLoader loader) {
        try {
            Class<?> realCallClass = XposedHelpers.findClass(OKHTTP_PACKAGE + ".RealCall", loader);
            XposedHelpers.findAndHookMethod(realCallClass, "getResponseWithInterceptorChain", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object request = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                    Object response = param.getResult();

                    if (request == null || response == null || !isTargetRequest(request)) {
                        return;
                    }

                    Object newResponse = buildVipResponse(response, loader);
                    if (newResponse != null) {
                        param.setResult(newResponse);
                    }
                }
            });

            XposedBridge.log(TAG + ": Hooked " + OKHTTP_PACKAGE + ".RealCall#getResponseWithInterceptorChain");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking OkHttp: " + t);
        }
    }

    private boolean isTargetRequest(Object request) {
        Object url = XposedHelpers.callMethod(request, "url");
        return url != null && url.toString().contains(TARGET_API);
    }

    private Object buildVipResponse(Object response, ClassLoader loader) throws Throwable {
        Object body = XposedHelpers.callMethod(response, "body");
        if (body == null) {
            XposedBridge.log(TAG + ": Response body is null");
            return null;
        }

        String content = (String) XposedHelpers.callMethod(body, "string");
        XposedBridge.log(TAG + ": Original Response: " + content);

        JSONObject jsonObject = new JSONObject(content);
        jsonObject.put("user_data_by_key", createUserDataByKey());

        String newContent = jsonObject.toString();
        XposedBridge.log(TAG + ": Modified Response: " + newContent);

        Object mediaType = XposedHelpers.callMethod(body, "contentType");
        Class<?> responseBodyClass = XposedHelpers.findClass(OKHTTP_PACKAGE + ".ResponseBody", loader);
        Object newBody = XposedHelpers.callStaticMethod(responseBodyClass, "create", mediaType, newContent);

        Object responseBuilder = XposedHelpers.callMethod(response, "newBuilder");
        XposedHelpers.callMethod(responseBuilder, "body", newBody);
        return XposedHelpers.callMethod(responseBuilder, "build");
    }

    private JSONObject createUserDataByKey() throws Throwable {
        JSONObject userDataByKey = new JSONObject();
        userDataByKey.put("vip", true);
        userDataByKey.put("vip_price", 1);
        userDataByKey.put("vip_date_start", 0);
        userDataByKey.put("vip_date_end", 2147483647);
        userDataByKey.put("vip_end", 2147483647);
        userDataByKey.put("xkw_vip_info", createVipInfo());
        userDataByKey.put("pan_vip_info", createVipInfo());
        userDataByKey.put("print_vip_info", createVipInfo());
        userDataByKey.put("super_vip_info", createVipInfo());
        return userDataByKey;
    }

    private JSONObject createVipInfo() throws Throwable {
        JSONObject vipInfo = new JSONObject();
        vipInfo.put("is_vip", true);
        vipInfo.put("remain_use_count", 2147483647);
        vipInfo.put("create_time", "2026-01-01 00:00:00");
        vipInfo.put("expire_time", "2038-01-01 00:00:00");
        return vipInfo;
    }
}

