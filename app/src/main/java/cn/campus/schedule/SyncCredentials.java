package cn.campus.schedule;

import android.content.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class SyncCredentials {
    static void save(Context c,JSONObject tokens)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretStore.key());String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(tokens.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!c.getSharedPreferences("sync_credentials",0).edit().putString("session",value).commit())throw new java.io.IOException("登录信息保存失败");}
    static JSONObject read(Context c)throws Exception{String value=c.getSharedPreferences("sync_credentials",0).getString("session","");if(value.isEmpty())return new JSONObject();String[] parts=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretStore.key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));}
    static void clear(Context c){c.getSharedPreferences("sync_credentials",0).edit().clear().commit();}
}
