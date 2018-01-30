import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpecFactory;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class EnterpriseUtil {
	private static String LOGIN_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/login";
	private static String DATA_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/Enterprise/index?companyName=&province=&city=&town=&secondIndustry=&nature=&isListed=&procurement_mode=&page=";
	private static String DETAIL_DATA_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/project/show?id=";

	private static String TARGET_DATA_PATH;

	private static String TOKEN;
	private static int MAX_PAGE;
	private static CookieStore cookieStore = null;
	private static HttpClientContext context = null;

	private static List<EnterpriseBean> ALL_DATA = new ArrayList<EnterpriseBean>();

	static {
		String basePath = EnterpriseUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		if (basePath.endsWith("jar")) {
			basePath = basePath.replaceAll("YOULI\\.jar", "");
			TARGET_DATA_PATH = basePath;
			MAX_PAGE = Integer.MAX_VALUE;
		} else {
			TARGET_DATA_PATH = EnterpriseUtil.class.getResource("/").getPath();
			MAX_PAGE = 2;
		}
	}
	
	private static void saveListDataToDisk(JSONArray listJSON) {
		File file = new File(TARGET_DATA_PATH + "EnterpriseData.json");
		
		try {
			FileUtils.writeStringToFile(file, listJSON.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void start() throws Exception {
		CloseableHttpClient client = HttpClients.createDefault();

		try {
			refreshToken(client);
			setContext();
			getEnterpriseData(client);
			writeToExcel();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				// 关闭流并释放资源
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	private static void getEnterpriseData(CloseableHttpClient client) {
		JSONArray jsonArray = getListJSONDataFromLocal();
		saveListDataToDisk(jsonArray);
		int len = jsonArray.size();
		for(int i = 0; i < len; i++) {
			JSONObject json = jsonArray.getJSONObject(i);
			parseToBean(json);
		}
	}
	
	private static JSONArray getListJSONDataFromLocal() {
		File file = new File(TARGET_DATA_PATH + "EnterpriseData.json");
		
		try {
			String content = FileUtils.readFileToString(file, "UTF-8");
			return JSONArray.fromObject(content);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private static void parseToBean(JSONObject json) {
		try {
			EnterpriseBean bean = new EnterpriseBean();
			String name = json.getString("name");
			bean.setName(name);
			
			String nature = "-";
			if(json.containsKey("nature")) {
				String natureJsonStr = json.getString("nature");
				if("未知".equals(natureJsonStr)) {
					nature = "未知";
				}
				else {
					JSONObject natureJson = json.getJSONObject("nature");
					nature = natureJson.getString("value");
				}
			}
			
			bean.setNature(nature);
			String isListed = json.getString("is_listed");
			bean.setIsListed(isListed);
			String projectCount = json.getString("projectcount");
			bean.setProjectCount(projectCount);
			String projectScale = json.getString("projectscale");
			bean.setProjectScale(projectScale);
			
			ALL_DATA.add(bean);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}


	private static void sendListOptions(CloseableHttpClient client, String url) {
		HttpResponse httpResponse = null;
		HttpOptions httpOptions = new HttpOptions(url);

		try {
			httpResponse = client.execute(httpOptions);
			String html = getResponseContent(httpResponse);
			System.out.println(html);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private static JSONArray getListJSONData(CloseableHttpClient client) {
		JSONArray result = new JSONArray();
		int page = 1;
		boolean isContinue = true;

		while (isContinue) {

			if (page > MAX_PAGE) {
				break;
			}

			if (page % 50 == 0) {
				refreshToken(client);
			}

			String url = DATA_URL + page + "&token=" + TOKEN;
//			sendListOptions(client, url);
			isContinue = false;
			HttpGet httpGet = new HttpGet(url);
			try {
				System.out.println("page=" + page);
				HttpResponse httpResponse = client.execute(httpGet);
				String html = getResponseContent(httpResponse);
				JSONObject json = JSONObject.fromObject(html);
				if ("000000".equals(json.getString("code"))) {
					JSONObject data = json.getJSONObject("data");
					if (data != null) {
						JSONArray list = data.getJSONArray("data");
						if (list.size() > 0) {
							result.addAll(list);
							isContinue = true;
							page++;
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	

	private static void refreshToken(CloseableHttpClient client) {
		client = HttpClients.createDefault();
		HttpResponse httpResponse = null;
		HttpOptions httpOptions = new HttpOptions(LOGIN_URL);

		try {
			httpResponse = client.execute(httpOptions);
		} catch (Exception e) {
			e.printStackTrace();
		}

		HttpPost httpPost = new HttpPost(LOGIN_URL);

		httpPost.addHeader("User-Agent",
				"Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv\",\"58.0) Gecko/20100101 Firefox/58.0");
		httpPost.addHeader("Accept", "application/json, text/plain, */*");
		httpPost.addHeader("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
		httpPost.addHeader("Accept-Encoding", " gzip, deflate");
		httpPost.addHeader("Referer", "http://login.youlidata.com/");
		httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		httpPost.addHeader("Authorization",
				"Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOjE4MjcsImlzcyI6Imh0dHA6Ly9hcGkueW91bGkudGVzdC54aW55dWFwcC5uZXQvYXBpL3dlYi92MS9sb2dpbiIsImlhdCI6MTUxNjkzOTQ0OCwiZXhwIjoxNTE2OTQzMDQ4LCJuYmYiOjE1MTY5Mzk0NDgsImp0aSI6InpVdGR6OE5ZVlFEUjhKWWkifQ.oZ3x2gfAAsQr9r-8A1hmeL-wSnbWujllTUzeu4pcMds");
		httpPost.addHeader("Origin", " http://login.youlidata.com");
		httpPost.addHeader("Connection", "keep-alive");
		httpPost.addHeader("Pragma", "no-cache");
		httpPost.addHeader("Cache-Control", "no-cache");

		Map parameterMap = new HashMap();

		parameterMap.put("password", "123456");
		parameterMap.put("serial", "T108130323");
		parameterMap.put("tenant_id", "T108130323");
		parameterMap.put("user_name", "user1");

		try {
			// 执行post请求
			UrlEncodedFormEntity postEntity = new UrlEncodedFormEntity(getParam(parameterMap), "UTF-8");
			httpPost.setEntity(postEntity);
			try {
				httpResponse = client.execute(httpPost);
			} catch (Exception e) {
				System.out.println("失败重试--1");
				httpResponse = client.execute(httpPost);
			}

			if (httpResponse == null) {
				System.out.println("失败重试--2");
				httpResponse = client.execute(httpPost);
			}

			// cookie store
			setCookieStore(httpResponse);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	public static List<NameValuePair> getParam(Map parameterMap) {
		List<NameValuePair> param = new ArrayList<NameValuePair>();
		Iterator it = parameterMap.entrySet().iterator();
		while (it.hasNext()) {
			Entry parmEntry = (Entry) it.next();
			param.add(new BasicNameValuePair((String) parmEntry.getKey(), (String) parmEntry.getValue()));
		}
		return param;
	}

	public static String getResponseContent(HttpResponse httpResponse) throws ParseException, IOException {
		// 获取响应消息实体
		HttpEntity entity = httpResponse.getEntity();
		// 响应状态
		// 判断响应实体是否为空
		if (entity != null) {
			String responseString = EntityUtils.toString(entity);
			return responseString;
		}
		return null;
	}

	public static void setContext() {
		// System.out.println("----setContext");
		context = HttpClientContext.create();
		Registry<CookieSpecProvider> registry = RegistryBuilder.<CookieSpecProvider>create()
				.register(CookieSpecs.BEST_MATCH, new BestMatchSpecFactory())
				.register(CookieSpecs.BROWSER_COMPATIBILITY, new BrowserCompatSpecFactory()).build();
		context.setCookieSpecRegistry(registry);
		context.setCookieStore(cookieStore);
	}

	public static void setCookieStore(HttpResponse httpResponse) {
		// System.out.println("----setCookieStore");

		try {
			cookieStore = new BasicCookieStore();
			String html = getResponseContent(httpResponse);
			JSONObject json = JSONObject.fromObject(html);
			BasicClientCookie cookie1 = new BasicClientCookie("access", "0");
			cookie1.setVersion(0);

			TOKEN = json.getJSONObject("data").getString("token");
			BasicClientCookie cookie2 = new BasicClientCookie("login_token", TOKEN);
			cookie2.setVersion(0);
			// cookie1.setDomain("www.bridata.com");

			BasicClientCookie cookie3 = new BasicClientCookie("user", "admin");
			cookie3.setVersion(0);

			BasicClientCookie cookie4 = new BasicClientCookie("waitingTime", "1725");
			cookie4.setVersion(0);

			cookieStore.addCookie(cookie1);
			cookieStore.addCookie(cookie2);
			cookieStore.addCookie(cookie3);
			cookieStore.addCookie(cookie4);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	private static void writeToExcel() {
		Date d = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		String excelFilePath = TARGET_DATA_PATH +"Enterprise_" + sdf.format(d) + ".xls";
		EnterpriseExcelUtil.writeExcel(ALL_DATA, excelFilePath);
		System.out.println(excelFilePath);
		System.out.println("成功写入excel文件，本次抓取的数据总数=" + ALL_DATA.size());
	}

	public static void main(String[] args) throws Exception {
		start();
	}

}
