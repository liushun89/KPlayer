package com.xk.player.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;

import sun.misc.BASE64Encoder;

import com.xk.player.lrc.XRCLine;
import com.xk.player.tools.SongSeacher.SearchInfo;

public class NetEasySource implements IDownloadSource {

	private BasicCookieStore cookieStore = new BasicCookieStore();
	private CloseableHttpClient client = HttpClientUtils.createSSLClientDefault(cookieStore);
	private BASE64Encoder encoder = new BASE64Encoder();
	public NetEasySource() {
		cookieStore.addCookie(new BasicClientCookie("appver", "1.5.6"));
	}
	
	private String encodeSong(String id) {
		byte[] magic = "3go8&$8*3*3h0k(2)2".getBytes(StandardCharsets.UTF_8);
		byte[] songID = id.getBytes(StandardCharsets.UTF_8);
		int magicLength = magic.length;
		for(int i = 0; i < songID.length; i++) {
			songID[i] = (byte) (songID[i] ^ magic[i % magicLength]);
		}
		byte[] md5 = Md5.MD5(songID);
		String result = encoder.encode(md5);
		result = result.replace("/", "_");
		result = result.replace("+", "_");
		return result;
	}
	
	private List<SearchInfo> getUrl(List<String> ids) {
		if(null == ids || ids.isEmpty()) {
			return new ArrayList<SongSeacher.SearchInfo>();
		}
		List<SearchInfo> info = new ArrayList<SongSeacher.SearchInfo>();
		String url = String.format("http://music.163.com/api/song/detail?ids=[%s]", Util.join(ids, ","));
		String data = get(url);
		Map<String, Object> result = JSONUtil.fromJson(data);
		if(null != result) {
			List<Map<String, Object>> songs = (List<Map<String, Object>>) result.get("songs");
			if(null != songs) {
				for(Map<String, Object> song : songs) {
					SearchInfo si = new SearchInfo() {
						@Override
						public String getUrl() {
							if(urlFound) {
								return this.url;
							}
							Map<String, Object> mMusic = JSONUtil.fromJson(this.url);
							String song_id = mMusic.get("id").toString();
							String enc_id = encodeSong(song_id);
							String songUrl = String.format("http://m1.music.126.net/%s/%s.mp3", enc_id, song_id);
							urlFound = true;
							return songUrl;
						}
						
					};
					si.name = (String) song.get("name");
					List<Map<String, Object>> artists = (List<Map<String, Object>>) song.get("artists");
					if(null != artists && artists.size() > 0) {
						si.singer = (String) artists.get(0).get("name");
					}
					Map<String, Object> album = (Map<String, Object>) song.get("album");
					if(null != album) {
						si.album = (String) album.get("name");
					}
					Map<String, Object> mMusic = (Map<String, Object>) song.get("mMusic");
					if(null != mMusic) {
						mMusic.put("dfsId", song.get("id"));
						si.url = JSONUtil.toJson(mMusic);
						si.type = (String) mMusic.get("extension");
					}else {
						continue;
					}
					info.add(si);
					
				}
			}
		}
		return info;
	}
	
	private void addCookies(HttpResponse response) {
		Header[] headers = response.getHeaders("set-cookie");
		if(null != headers) {
			for(Header header : headers) {
				cookieStore.addCookie(new BasicClientCookie(header.getName(), header.getValue()));
			}
		}
	}
	
	private void addHeaders(HttpRequestBase httppost) {
		httppost.addHeader("Accept", "*/*");
		httppost.addHeader("Accept-Encoding", "gzip,deflate,sdch");
		httppost.addHeader("Accept-Language", "zh-CN,zh;q=0.8,gl;q=0.6,zh-TW;q=0.4");
		httppost.addHeader("Connection", "keep-alive");
		httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		httppost.addHeader("Host", "music.163.com");
		httppost.addHeader("Referer", "http://music.163.com");
		httppost.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36");
	}
	
	private String get(String url) {
		StringBuffer result=new StringBuffer();
		HttpGet httppost = new HttpGet(url);
		addHeaders(httppost);
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(2000).setConnectTimeout(2000).build();//设置请求和传输超时时间
		httppost.setConfig(requestConfig);
		CloseableHttpResponse response = null;
		try {
			response = client.execute(httppost);  
			addCookies(response);
			HttpEntity entity = response.getEntity();  
			InputStream instream=entity.getContent();
			BufferedReader br = new BufferedReader(new InputStreamReader(instream,StandardCharsets.UTF_8));  
			String temp = "";  
			while ((temp = br.readLine()) != null) {  
			    result.append(temp);  
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			try {
				response.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
        
        return result.toString();
	}
	
	private String post(String url, List<BasicNameValuePair> formparams) {
		UrlEncodedFormEntity entity1 = new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);  
		
		//新建Http  post请求  
		HttpPost httppost = new HttpPost(url);  
		httppost.setEntity(entity1);  
		addHeaders(httppost);
		//处理请求，得到响应  
		CloseableHttpResponse response = null;
		StringBuilder result = new StringBuilder();
		try {
			response = client.execute(httppost);  
			addCookies(response);
			//打印返回的结果  
			HttpEntity entity = response.getEntity();  
			
			if (entity != null) {  
				InputStream instream = entity.getContent();  
				BufferedReader br = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));  
				String temp = "";  
				while ((temp = br.readLine()) != null) {  
					result.append(temp);  
				}  
			}  
			httppost.releaseConnection();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				response.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return result.toString();
	}
	
	
	
	@Override
	public List<XRCLine> parse(String content) {
		Map<String, Object> result = JSONUtil.fromJson(content);
		if(null != result && null != result.get("klyric")) {
			Map<String, Object> lrcs = (Map<String, Object>) result.get("klyric");
			String krc = (String) lrcs.get("lyric");
			if(null != krc) {
				StringReader reader = new StringReader(krc);
				return KrcText.fromReader(reader, "\\([0-9]*,[0-9]*\\)(\\w+'*\\w*\\s*|[\u4E00-\u9FA5]{1})", "(", ")", 2, true);
			}
		} 
		if(null != result && null != result.get("lrc")) {
			Map<String, Object> lrcs = (Map<String, Object>) result.get("lrc");
			String lrc = (String) lrcs.get("lyric");
			StringReader in = new StringReader(lrc);
			return new LrcParser((long)Integer.MAX_VALUE).parserToXrc(in);
		}
		return null;
	}

	@Override
	public List<SearchInfo> getLrc(String name) {
		List<SearchInfo> info = new ArrayList<SongSeacher.SearchInfo>();
		Map<String, Object> result = search(name, "1");
		if(null != result && null != result.get("result")) {
			Map<String, Object> rst = (Map<String, Object>) result.get("result");
			if(null != rst) {
				List<Map<String, Object>> songs = (List<Map<String, Object>>) rst.get("songs");
				if(null != songs) {
					for(Map<String, Object> song : songs) {
						SearchInfo si = new SearchInfo() {
							@Override
							public String getUrl() {
								if(urlFound) {
									return this.url;
								}
								Map<String, Object> mMusic = JSONUtil.fromJson(this.url);
								Object id = mMusic.get("id");
								urlFound = true;
								return "http://music.163.com/api/song/lyric?os=osx&id=" + id + "&lv=-1&kv=-1&tv=-1";
							}
							
						};
						si.name = (String) song.get("name");
						List<Map<String, Object>> artists = (List<Map<String, Object>>) song.get("ar");
						if(null != artists && artists.size() > 0) {
							si.singer = (String) artists.get(0).get("name");
						}
						Map<String, Object> album = (Map<String, Object>) song.get("al");
						if(null != album) {
							si.album = (String) album.get("name");
						}
						si.type = "mp3";
						Map<String, Object> mMusic = (Map<String, Object>) song.get("m");
						if(null != mMusic) {
							mMusic.put("id", song.get("id"));
							si.url = JSONUtil.toJson(mMusic);
						}else {
							continue;
						}
						info.add(si);
					}
				}
			}
			
		}
		return info;
	}

	@Override
	public List<SearchInfo> getMV(String name) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}

	/**
	 * @param name 名称
	 * @param type 单曲(1)，歌手(100)，专辑(10)，歌单(1000)，用户(1002) *(type)*
	 * @return
	 * @author o-kui.xiao
	 */
	private Map<String, Object> search(String name, String type) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("s", name);
		params.put("offset", "0");
		params.put("type", type);
		params.put("limit", "30");
		String json = JSONUtil.toJson(params);
		Map<String, String> p = EncryptUtils.encrypt(json);
		List<BasicNameValuePair> fromparams = new ArrayList<BasicNameValuePair>();
		fromparams.add(new BasicNameValuePair("params", p.get("params")));
		fromparams.add(new BasicNameValuePair("encSecKey", p.get("encSecKey")));
		String url = "http://music.163.com/weapi/cloudsearch/get/web";
		String data = post(url, fromparams);
		Map<String, Object> result = JSONUtil.fromJson(data);
		return result;
	}
	
	
	@Override
	public List<SearchInfo> getSong(String name) {
		List<SearchInfo> info = new ArrayList<SongSeacher.SearchInfo>();
		Map<String, Object> result = search(name, "1");
		if(null != result && null != result.get("result")) {
			Map<String, Object> rst = (Map<String, Object>) result.get("result");
			if(null != rst) {
				List<Map<String, Object>> songs = (List<Map<String, Object>>) rst.get("songs");
				if(null != songs) {
					for(Map<String, Object> song : songs) {
						SearchInfo si = new SearchInfo() {
							@Override
							public String getUrl() {
								if(urlFound) {
									return this.url;
								}
								Map<String, Object> mMusic = JSONUtil.fromJson(this.url);
								Map<String, Object> params = new HashMap<String, Object>();
								List<Object> ids = new ArrayList<Object>();
								ids.add(mMusic.get("id"));
								params.put("ids", ids);
								params.put("br", mMusic.get("br"));
								params.put("csrf_token", "");
								String json = JSONUtil.toJson(params);
								Map<String, String> p = EncryptUtils.encrypt(json);
								List<BasicNameValuePair> fromparams = new ArrayList<BasicNameValuePair>();
								fromparams.add(new BasicNameValuePair("params", p.get("params")));
								fromparams.add(new BasicNameValuePair("encSecKey", p.get("encSecKey")));
								String target = "http://music.163.com/weapi/song/enhance/player/url";
								String rst = post(target, fromparams);
								Map<String, Object> result = JSONUtil.fromJson(rst);
								if(null != result) {
									List<Map<String, Object>> urls = (List<Map<String, Object>>) result.get("data");
									if(null != urls && !urls.isEmpty()) {
										urlFound = true;
										return (String) urls.get(0).get("url");
									}
								}
								return "";
							}
							
						};
						si.name = (String) song.get("name");
						List<Map<String, Object>> artists = (List<Map<String, Object>>) song.get("ar");
						if(null != artists && artists.size() > 0) {
							si.singer = (String) artists.get(0).get("name");
						}
						Map<String, Object> album = (Map<String, Object>) song.get("al");
						if(null != album) {
							si.album = (String) album.get("name");
						}
						si.type = "mp3";
						Map<String, Object> mMusic = (Map<String, Object>) song.get("m");
						if(null != mMusic) {
							mMusic.put("id", song.get("id"));
							si.url = JSONUtil.toJson(mMusic);
						}else {
							continue;
						}
						info.add(si);
					}
				}
			}
			
		}
		return info;
//		return getUrl(ids);
	}

	@Override
	public List<SearchInfo> getSong(String name, String type) {
		return getSong(name);
	}

	@Override
	public Map<String, String> fastSearch(String name) {
		// TODO Auto-generated method stub
		return Collections.emptyMap();
	}

	@Override
	public String getArtist(String name) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public static void main(String[] args) {
		NetEasySource source = new NetEasySource();
		source.getSong("凉凉");
	}

	@Override
	public SongLocation getInputStream(String url) {
//		try {
//			HttpGet httppost = new HttpGet(url);  
//			addHeaders(httppost);
//			RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(2000).setConnectTimeout(2000).build();//设置请求和传输超时时间
//			httppost.setConfig(requestConfig);
//			CloseableHttpResponse response = client.execute(httppost);  
//			HttpEntity entity = response.getEntity();  
//			SongLocation location=new SongLocation();
//	    	location.length=entity.getContentLength();
//	    	location.input=entity.getContent();
//			return location;
//			
//		} catch (Exception e) {
//			System.out.println(e.getMessage());
//			return null;
//		}
		return HTTPUtil.getInstance("player").getInputStream(url);
	}

}