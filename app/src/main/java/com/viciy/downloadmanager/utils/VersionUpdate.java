package com.viciy.downloadmanager.utils;

import com.viciy.downloadmanager.presenter.VersionUpdateImpl;

import java.io.File;

public class VersionUpdate {

    /**
     * 请求服务器，检查版本是否可以更新
     *
     * @param versionUpdate
     */
     public static void checkVersion(final VersionUpdateImpl versionUpdate, String apkUrl) {
         //删除上次更新存储在本地的apk
         removeOldApk();
         //从网络请求获取到的APK下载路径，此处是随便找的链接
         versionUpdate.bindService(apkUrl);
     }

    /**
     * 删除上次更新存储在本地的apk
     */
    private static void removeOldApk() {
        //获取老ＡＰＫ的存储路径
        File fileName = new File(SPUtil.getString(Constant.SP_DOWNLOAD_PATH, ""));

        if (fileName != null && fileName.exists() && fileName.isFile()) {
            fileName.delete();
            System.out.println("存储器内存在老APK，进行删除操作");
        }
    }
}
