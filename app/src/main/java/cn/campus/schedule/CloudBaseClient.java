package cn.campus.schedule;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

class CloudBaseClient {
    static final int LIMIT=16*1024*1024;
    static class Failure extends IOException {final int status;Failure(int status,String message){super(message);this.status=status;}}
    final String origin,device;
    CloudBaseClient(String env,String device){if(!env.matches("[A-Za-z0-9][A-Za-z0-9-]{2,100}"))throw new IllegalArgumentException("请输入有效的 CloudBase 环境 ID");origin="https://"+env+".api.tcloudbasegateway.com";this.device=device;}
    byte[] exchange(String method,String path,String token,byte[] body,String type)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(origin+path).openConnection();try{c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-device-id",device);if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",type);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int status=c.getResponseCode();if(status<200||status>=300){String stage=path.contains("/auth/v1/token")?"登录续期":path.contains("/auth/")?"账号登录":path.contains("/storages/")?(method.equals("GET")?"照片下载":"照片上传"):path.contains("/rpc/")?"记录上传":"记录读取";String code="";try(InputStream err=c.getErrorStream()){if(err!=null){byte[] buf=new byte[4096];int n=err.read(buf);if(n>0){JSONObject detail=new JSONObject(new String(buf,0,n,StandardCharsets.UTF_8));String v=detail.optString("error_code",detail.optString("code",detail.optString("error")));if(v.matches("[A-Za-z0-9_.-]{1,80}"))code=" · "+v;}}}catch(Exception ignored){}throw new Failure(status,stage+"失败（HTTP "+status+code+"）"+(stage.equals("登录续期")?"，请用原账号重新登录":stage.equals("照片下载")&&status==404?"，云端照片缺失或不可访问":"，本机内容已保留"));}try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>LIMIT)throw new IOException("同步文件过大");out.write(buf,0,n);}return out.toByteArray();}
        }finally{c.disconnect();}
    }
    JSONObject json(String path,String token,JSONObject body)throws Exception{byte[] response=exchange("POST",path,token,body.toString().getBytes(StandardCharsets.UTF_8),"application/json");return new JSONObject(new String(response,StandardCharsets.UTF_8));}
    JSONObject login(String username,String password)throws Exception{return json("/auth/v1/signin","",AiProtocol.obj("username",username,"password",password));}
    JSONObject refresh(String refresh)throws Exception{return json("/auth/v1/token","",AiProtocol.obj("grant_type","refresh_token","refresh_token",refresh));}
    JSONArray pull(String token,long after)throws Exception{byte[] response=exchange("GET","/v1/rdb/rest/xq_sync_records?select=record_key,payload,deleted,revision,mutation_id&revision=gt."+after+"&order=revision.asc&limit=100",token,null,"");return new JSONArray(new String(response,StandardCharsets.UTF_8));}
    JSONObject push(String token,SyncLedger.Row r)throws Exception{return json("/v1/rdb/rest/rpc/xq_sync_put",token,AiProtocol.obj("p_key",r.key,"p_payload",r.value==null?JSONObject.NULL:r.value,"p_deleted",r.value==null,"p_expected",r.revision,"p_mutation",r.mutation));}
    String photoPath(String uid,String name){if(!name.matches("[A-Za-z0-9._-]{1,160}")||name.contains(".."))throw new IllegalArgumentException("照片名称无效");return "/v1/storages/object/xq-photos/"+encode(uid)+"/"+encode(name);}
    static String encode(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
    void upload(String token,String uid,String name,byte[] data)throws Exception{try{exchange("POST",photoPath(uid,name),token,data,"application/octet-stream");}catch(Failure e){if(e.status!=409)throw e;}}
    byte[] download(String token,String uid,String name)throws Exception{return exchange("GET",photoPath(uid,name),token,null,"");}
}
