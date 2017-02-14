package com.viciy.downloadmanager.greendao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;

import de.greenrobot.dao.query.Query;


/**
 * Created by bai-qiang.yang on 2017/2/14.
 */

public class DbInstallTaskHelper {
    public final static String dbName = "install_task";
    private Cursor cursor;
    //用于聊天对象和群名的保存
    private DaoMaster.DevOpenHelper helper;
    private SQLiteDatabase db;
    private DaoMaster               master;
    private DaoSession              daoSession;


    public DbInstallTaskHelper(Context context) {
        //初始化数据库
        setupDatabase(context);
        String orderBy = InstallTaskDao.Properties.Id.columnName + " DESC";
        //查询，得到cursor
        cursor = getDb().query(getDaoSession().getInstallTaskDao().getTablename(),
                getDaoSession().getInstallTaskDao().getAllColumns(), null, null, null, null, orderBy);
    }

    public void setupDatabase(Context context) {
        //创建数据库
        helper = new DaoMaster.DevOpenHelper(context, DbInstallTaskHelper.dbName, null);
        //得到数据库连接对象
        db = helper.getWritableDatabase();
        //得到数据库管理者
        master = new DaoMaster(db);
        //得到daoSession，可以执行增删改查操作
        daoSession = master.newSession();
    }


    public DaoSession getDaoSession() {
        return daoSession;
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    /**
     * 添加
     */
    public void add(String apkUrl, String downloadId) {
        InstallTask installTask = new InstallTask(null, apkUrl, downloadId);
        //面向对象添加表数据
        getDaoSession().getInstallTaskDao().insert(installTask);
        cursor.requery();//刷新
    }

    /**
     * 根据apkUrl删除
     */
    public void delete(String apkUrl, String downloadId) {
        InstallTask installTask = new InstallTask(null, apkUrl, downloadId);
        getDaoSession().getInstallTaskDao().delete(installTask);
        cursor.requery();
    }

    /**
     * 更新
     */
    public void update(String apkUrl, String downloadId) {
        InstallTask installTask = getDaoSession().getInstallTaskDao().queryBuilder().where(InstallTaskDao.Properties.ApkUrl.eq(apkUrl)).build().unique();
        installTask.setDownloadId(downloadId);
        getDaoSession().getInstallTaskDao().update(installTask);
        cursor.requery();
    }

    /**
     * 查询
     * @ param apkUrl
     */
    public List<InstallTask> query(String apkUrl) {

        // Query 类代表了一个可以被重复执行的查询
        Query<InstallTask> query = getDaoSession().getInstallTaskDao().queryBuilder()
                .where(InstallTaskDao.Properties.ApkUrl.eq(apkUrl))
                .orderAsc(InstallTaskDao.Properties.Id)
                .build();
        // 查询结果以 List 返回
        List<InstallTask> count = query.list();

        if (count.size() > 0) {
            return count;
        } else {
            return null;
        }

    }

    /**
     * 关闭cursor
     */
    public void close() {
        cursor.close();
    }

}
