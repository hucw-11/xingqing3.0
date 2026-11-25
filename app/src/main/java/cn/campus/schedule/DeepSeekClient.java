package cn.campus.schedule;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

class DeepSeekClient {
    volatile HttpURLConnection connection;
    static class ServiceException extends IOException {final String code;final boolean retryable;ServiceException(String message){this("service",message,false);}ServiceException(String code,String message,boolean retry){super(message);this.code=code;this.retryable=retry;}}
    static JSONObject decode(JSONObject response)throws Exception{JSONObject choice=response.getJSONArray("choices").getJSONObject(0);String finish=choice.optString("finish_reason");if(!finish.equals("stop")&&!finish.equals("tool_calls"))throw new ServiceException("truncated","AI 回复未完整返回，请简化请求后重试",false);JSONObject message=choice.getJSONObject("message");String text=message.isNull("content")?"":message.optString("content");JSONArray calls=message.optJSONArray("tool_calls"),actions=new JSONArray();if(calls!=null)for(int i=0;i<calls.length();i++){JSONObject function=calls.getJSONObject(i).getJSONObject("function");if(!function.optString("name").equals("propose_records"))throw new ServiceException("format","AI 返回了不支持的操作，请重试",false);JSONArray items=new JSONObject(function.getString("arguments")).getJSONArray("actions");for(int j=0;j<items.length();j++)actions.put(items.getJSONObject(j));}if(text.trim().isEmpty()&&actions.length()==0)throw new ServiceException("empty","AI 返回了空内容",true);if(text.trim().isEmpty())text="我拟好了建议，请检查并确认后保存。";return AiProtocol.obj("reply",text,"actions",actions);}
    JSONObject call(String key,JSONObject request)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();connection=c;
        try{c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status!=200)throw new ServiceException("http_"+status,status==401?"API Key 无效，请检查设置":status==402?"DeepSeek 账户余额不足":status==429?"请求过于频繁，请稍后重试":"DeepSeek 请求失败（HTTP "+status+"），请稍后重试",status==429||status>=500);try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(b.size()+n>4*1024*1024)throw new ServiceException("回复过大，请缩小请求");b.write(chunk,0,n);}return decode(new JSONObject(b.toString("UTF-8")));}}finally{c.disconnect();connection=null;}
    }
    void cancel(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
}
