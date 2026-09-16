package com.alameer.station.shifts;

import android.app.Activity;import android.database.Cursor;import android.widget.Toast;import org.json.JSONArray;import org.json.JSONObject;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;

public class Sync {
    private final Activity activity;private final Db db;
    public Sync(Activity a){activity=a;db=new Db(a);}

    public void run(boolean userRequested){
        String url=BuildConfig.SYNC_URL;
        if(url==null||url.trim().isEmpty()){if(userRequested)toast("لم يُفعّل رابط مزامنة Google Sheets بعد.");return;}
        int pending=db.pendingSyncCount();
        if(pending==0){if(userRequested)toast("لا توجد ورديات بانتظار المزامنة.");return;}
        if(userRequested)toast("جاري مزامنة "+pending+" وردية...");
        new Thread(()->{
            int ok=0,total=0;
            try(Cursor c=db.pendingSync()){
                while(c.moveToNext()){
                    total++;long shiftId=c.getLong(0);
                    try{if(post(url,payload(c,shiftId))){db.markSynced(shiftId);ok++;}}catch(Exception ignored){}
                }
            }
            int fOk=ok,fTotal=total;
            activity.runOnUiThread(()->toast(fTotal==0?"لا توجد ورديات بانتظار المزامنة.":"تمت مزامنة "+fOk+" من "+fTotal+" وردية."+(fOk<fTotal?" الباقي سيُعاد إرساله لاحقاً.":"")));
        }).start();
    }

    /** يسحب أسعار الوقود المحدّثة من الشيت ويطبّقها محليًا. */
    public void pullPrices(boolean userRequested){
        String url=BuildConfig.SYNC_URL;
        if(url==null||url.trim().isEmpty()){if(userRequested)toast("لم يُفعّل رابط مزامنة Google Sheets بعد.");return;}
        new Thread(()->{
            try{
                String separator=url.contains("?")?"&":"?";
                JSONObject response=new JSONObject(get(url+separator+"action=prices&secret="+URLEncoder.encode(BuildConfig.SYNC_SECRET,"UTF-8")));
                if(!response.optBoolean("ok",false)){if(userRequested)activity.runOnUiThread(()->toast("تعذر جلب الأسعار من الشيت."));return;}
                JSONObject prices=response.optJSONObject("prices");
                if(prices==null||prices.length()==0){if(userRequested)activity.runOnUiThread(()->toast("لا توجد أسعار منشورة في الشيت."));return;}
                int changed=db.applyPrices(prices);
                if(userRequested||changed>0)activity.runOnUiThread(()->toast(changed==0?"الأسعار لديك محدّثة.":"حُدِّث سعر "+changed+" طرمبة من الشيت."));
            }catch(Exception e){if(userRequested)activity.runOnUiThread(()->toast("تعذر الاتصال بالشيت."));}
        }).start();
    }

    private String get(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept","application/json");
        try{return readAll(c.getInputStream());}finally{c.disconnect();}
    }

    /**
     * يسحب ورديات العامل من الشيت ويحفظها في الأرشيف بانتظار المراجعة.
     * لا تُرحّل ولا تُقيَّد؛ تُفتح من الأرشيف وتُعدَّل ثم تُعتمد.
     */
    public void pullShifts(boolean userRequested){
        String url=BuildConfig.SYNC_URL;
        if(url==null||url.trim().isEmpty()){if(userRequested)toast("لم يُفعّل رابط المزامنة بعد.");return;}
        if(userRequested)toast("جاري جلب ورديات العامل...");
        new Thread(()->{
            try{
                String separator=url.contains("?")?"&":"?";
                JSONObject response=new JSONObject(get(url+separator+"action=shifts&secret="
                        +URLEncoder.encode(BuildConfig.SYNC_SECRET,"UTF-8")));
                if(!response.optBoolean("ok",false)){
                    if(userRequested)activity.runOnUiThread(()->toast("تعذر جلب الورديات من الشيت."));return;
                }
                JSONArray list=response.optJSONArray("shifts");
                if(list==null||list.length()==0){
                    if(userRequested)activity.runOnUiThread(()->toast("لا توجد ورديات جديدة."));return;
                }
                int added=0,skipped=0;
                StringBuilder problems=new StringBuilder();
                for(int i=0;i<list.length();i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item==null)continue;
                    try{
                        if(db.importRemoteShift(item)>0)added++;else skipped++;
                    }catch(Exception e){
                        skipped++;
                        if(problems.length()<300)problems.append("\n• ").append(e.getMessage());
                    }
                }
                final int fAdded=added,fSkipped=skipped;
                final String detail=problems.toString();
                activity.runOnUiThread(()->{
                    if(fAdded>0)toast("وصلت "+fAdded+" وردية إلى الأرشيف للمراجعة.");
                    else if(userRequested)toast(fSkipped>0?"لا جديد. "+fSkipped+" وردية مُتجاهلة."+detail:"لا توجد ورديات جديدة.");
                });
            }catch(Exception e){
                if(userRequested)activity.runOnUiThread(()->toast("تعذر الاتصال بالشيت."));
            }
        }).start();
    }

    private JSONObject payload(Cursor c,long shiftId)throws Exception{
        JSONObject j=new JSONObject();
        j.put("secret",BuildConfig.SYNC_SECRET);
        j.put("shiftId",shiftId);j.put("worker",c.getString(1));j.put("openedAt",c.getString(2));
        j.put("device",db.deviceId());j.put("shiftDate",db.shiftDate(shiftId));
        j.put("closedAt",c.isNull(3)?"":c.getString(3));j.put("status",c.getString(4));
        j.put("sales",c.getDouble(5));j.put("collections",c.getDouble(6));j.put("cashDelivered",c.getDouble(7));
        j.put("debts",c.getDouble(8));j.put("expenses",c.getDouble(9));j.put("balance",c.getDouble(10));
        j.put("differenceReason",c.getString(11));j.put("revision",c.getInt(12));j.put("managerNote",c.getString(13));
        JSONArray readings=new JSONArray();
        try(Cursor r=db.syncReadings(shiftId)){while(r.moveToNext()){JSONObject ro=new JSONObject();ro.put("pump",r.getString(0));ro.put("fuel",r.getString(1));ro.put("previous",r.getDouble(2));ro.put("current",r.getDouble(3));ro.put("price",r.getDouble(4));ro.put("sales",r.getDouble(5));readings.put(ro);}}
        j.put("readings",readings);
        JSONArray movements=new JSONArray();
        try(Cursor m=db.syncMovements(shiftId)){while(m.moveToNext()){JSONObject mo=new JSONObject();mo.put("type",m.getString(0));mo.put("name",m.getString(1));mo.put("amount",m.getDouble(2));movements.put(mo);}}
        j.put("movements",movements);
        return j;
    }

    private boolean post(String urlStr,JSONObject payload)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(urlStr).openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try(OutputStream os=c.getOutputStream()){os.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();
        InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        String resp=readAll(in);c.disconnect();
        if(code<200||code>=300)return false;
        return new JSONObject(resp).optBoolean("ok",false);
    }

    private String readAll(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}
    private void toast(String msg){Toast.makeText(activity,msg,Toast.LENGTH_LONG).show();}
}
