package com.alameer.station.shifts;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
/** Local, non-posted form drafts. Counterpart selection must be confirmed again. */
public final class EntryDraft {
    private EntryDraft(){}
    private static SharedPreferences prefs(Context context,String key){
        return context.getSharedPreferences("entry_draft_"+key,Context.MODE_PRIVATE);
    }
    public static boolean save(Context context,String key,String date,EditText... fields){
        SharedPreferences.Editor editor=prefs(context,key).edit().clear().putBoolean("exists",true).putString("date",date);
        for(int i=0;i<fields.length;i++)editor.putString("field_"+i,fields[i].getText().toString());
        return editor.commit();
    }
    public static boolean restore(Context context,String key,String[] date,EditText... fields){
        SharedPreferences p=prefs(context,key);if(!p.getBoolean("exists",false))return false;
        date[0]=p.getString("date",date[0]);
        for(int i=0;i<fields.length;i++)fields[i].setText(p.getString("field_"+i,""));
        return true;
    }
    public static void clear(Context context,String key){prefs(context,key).edit().clear().commit();}
}
