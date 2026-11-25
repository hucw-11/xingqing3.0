package cn.campus.schedule;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

class DeepSeekClient {
    volatile HttpURLConnection connection;static class ServiceException extends IOException { ServiceException(String message){super(message);} }
    JSONObject call(String key,JSONObject request)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();connection=c;
        try{c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status!=200)throw new ServiceException(status==401?"API Key 无效，请检查设置":status==402?"DeepSeek 账户余额不足":status==429?"请求过于频繁，请稍后重试":"DeepSeek 请求失败（HTTP "+status+"），请稍后重试");try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(b.size()+n>4*1024*1024)throw new ServiceException("回复过大，请缩小请求");b.write(chunk,0,n);}JSONObject response=new JSONObject(b.toString("UTF-8"));JSONObject choice=response.getJSONArray("choices").getJSONObject(0);if(!choice.optString("finish_reason").equals("stop"))throw new ServiceException("AI 回复未完整返回，请重试或简化请求");String text=choice.getJSONObject("message").optString("content");if(text.trim().isEmpty())throw new ServiceException("AI 返回了空内容，请重试");return new JSONObject(text);}}finally{c.disconnect();connection=null;}
    }
    void cancel(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
}
