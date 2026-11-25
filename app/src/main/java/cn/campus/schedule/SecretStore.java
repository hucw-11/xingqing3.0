package cn.campus.schedule;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    static final String ALIAS="student-schedule-deepseek";
    static javax.crypto.SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();}
    static void save(Context c,String secret)throws Exception{if(secret.isEmpty()){c.getSharedPreferences("ai_credentials",0).edit().clear().commit();return;}Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!c.getSharedPreferences("ai_credentials",0).edit().putString("key",value).commit())throw new java.io.IOException("密钥保存失败");}
    static String read(Context c)throws Exception{String value=c.getSharedPreferences("ai_credentials",0).getString("key","");if(value.isEmpty())return "";String[] parts=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);}
}
