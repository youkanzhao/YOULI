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

public class DataUtil {
	private static String LOGIN_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/login";
	private static String DATA_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/project/posts?inputValue=&province=&city=&town=&firstIndustry=&secondIndustry=&paymentMethod=&winBidTime=&investmentScale=&isAllowUnion=&procurementMode=&isAttachFile=&page=";
	private static String DETAIL_DATA_URL = "http://api.youli.test.xinyuapp.net/api/web/v1/project/show?id=";

	private static String TARGET_DATA_PATH;

	private static String TOKEN;
	private static int MAX_PAGE;
	private static CookieStore cookieStore = null;
	private static HttpClientContext context = null;

	private static List<DataBean> PPP_DATA = new ArrayList<DataBean>();

	static {
		String basePath = DataUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		if (basePath.endsWith("jar")) {
			basePath = basePath.replaceAll("YOULI\\.jar", "");
			TARGET_DATA_PATH = basePath;
			MAX_PAGE = Integer.MAX_VALUE;
		} else {
			TARGET_DATA_PATH = DataUtil.class.getResource("/").getPath();
			MAX_PAGE = 2;
		}
	}

	public static void start() throws Exception {
		CloseableHttpClient client = HttpClients.createDefault();

		try {
			refreshToken(client);
			setContext();
			getPPPData(client);

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

	private static void getInitData() {
		try {

			File folder = new File(TARGET_DATA_PATH + "data");
			if (folder.isDirectory()) {
				String[] files = folder.list();
				for (String fileName : files) {
					if (fileName.endsWith("json")) {
						String jsonFilePath = TARGET_DATA_PATH + "data" + File.separator + fileName;
						File file = new File(jsonFilePath);
						String jsonStr = FileUtils.readFileToString(file, "UTF-8");
						JSONObject json = JSONObject.fromObject(jsonStr);
						parseToBean(json);
					}
				}
			}

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

	public static void getPPPData(CloseableHttpClient client) {
		try {
//			getInitData();
			String[] ids = getInitListData();
			for(String id : ids) {
				File file = new File(TARGET_DATA_PATH +"data" + File.separator + id + ".json");
				if(!file.exists()) {
					getDetailData(client, id);
				}
				
			}
			writeToExcel();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String[] getInitListData() {
		try {
			StringBuffer sb = new StringBuffer();
			File file = new File(TARGET_DATA_PATH + "ids.json");
			LineIterator lineIterator = FileUtils.lineIterator(file, "UTF-8");
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                sb.append(line);
            }
			String content = sb.toString();
			
			return content.split(";");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	private static void saveListDataToDisk(JSONArray listJSON) {
		File file = new File(TARGET_DATA_PATH + "listData2.json");
		StringBuffer sb = new StringBuffer();
		int len = listJSON.size();
		for(int i = 0; i < len; i++) {
			JSONObject temp = listJSON.getJSONObject(i);
			String id = temp.getString("id");
			sb.append(id);
			if(i < len - 1) {
				sb.append(";");
			}
		}
		
		try {
			FileUtils.writeStringToFile(file, sb.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void saveDetailDataToDisk(String  id, JSONObject json) {
		File file = new File(TARGET_DATA_PATH +"data" + File.separator + id + ".json");
		
		try {
			FileUtils.writeStringToFile(file, json.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void getDetailData(CloseableHttpClient client, String id) {
		String url = DETAIL_DATA_URL + id + "&token=" + TOKEN;
		String html = "";
		try {
			sendListOptions(client, url);
			HttpGet httpGet = new HttpGet(url);
			HttpResponse httpResponse = client.execute(httpGet);
			html = getResponseContent(httpResponse);
			JSONObject json = JSONObject.fromObject(html);
			if ("000000".equals(json.getString("code"))) {
				saveDetailDataToDisk(id, json);
			}
			
			
			parseToBean(json);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(html);
		}
	}

	private static void parseListToBean(JSONObject target) {
		if (target != null) {

			DataBean bean = new DataBean();
			String xiangMuMingZi = target.getString("project_name");
			bean.setXiangMuMingZi(xiangMuMingZi);

			String xiangMuZhongBiaoShiJian = target.getString("win_bid_time");
			bean.setXiangMuZhongBiaoShiJian(xiangMuZhongBiaoShiJian);

			String diQu = getDiQu(target);
			bean.setDiQu(diQu);

			String suoShuHangYe = getSuoShuHangYe(target);
			bean.setSuoShuHangYe(suoShuHangYe);

			String touZiGuiMo = target.getString("investment_scale");
			bean.setTouZiGuiMo(touZiGuiMo);

			String xiangMuQuYuGuiMo = "-";
			bean.setXiangMuQuYuGuiMo(xiangMuQuYuGuiMo);

			String heZuoQi = "";
			bean.setHeZuoQi(heZuoQi);

			String yunZuoMoShi = getYunZuoMoShi(target);
			bean.setYunZuoMoShi(yunZuoMoShi);

			String fuFeiFangShi = getFuFeiFangShi(target);
			bean.setFuFeiFangShi(fuFeiFangShi);

			String zhaoBiaoDanWei = "";
			bean.setZhaoBiaoDanWei(zhaoBiaoDanWei);

			String caiGouFangShi = "";
			bean.setCaiGouFangShi(caiGouFangShi);

			String daiLiJiGou = "";
			bean.setDaiLiJiGou(daiLiJiGou);

			String xiangMuGaiKuang = "";
			bean.setXiangMuGaiKuang(xiangMuGaiKuang);

			List<String> zhongBiaoRen = getZhongBiaoRen(target);
			bean.setZhongBiaoRen(zhongBiaoRen);

			List<Map<String, String>> biaoDi = null;
			bean.setBiaoDi(biaoDi);

			List<Map<String, String>> fuJian = getFuJian(target);
			bean.setFuJian(fuJian);

			PPP_DATA.add(bean);
		}
	}

	private static void parseToBean(JSONObject json) {
		if ("000000".equals(json.getString("code"))) {
			JSONArray list = json.getJSONArray("data");
			if (list != null) {

				if (list.size() > 0) {
					DataBean bean = new DataBean();
					JSONObject target = (JSONObject) list.get(0);
					String xiangMuMingZi = target.getString("project_name");
					bean.setXiangMuMingZi(xiangMuMingZi);

					String xiangMuZhongBiaoShiJian = target.getString("win_bid_time");
					bean.setXiangMuZhongBiaoShiJian(xiangMuZhongBiaoShiJian);

					String diQu = getDiQu(target);
					bean.setDiQu(diQu);

					String suoShuHangYe = getSuoShuHangYe(target);
					bean.setSuoShuHangYe(suoShuHangYe);

					String touZiGuiMo = target.getString("investment_scale");
					bean.setTouZiGuiMo(touZiGuiMo);

					String xiangMuQuYuGuiMo = target.getString("region_scale");
					bean.setXiangMuQuYuGuiMo(xiangMuQuYuGuiMo);

					String heZuoQi = target.getString("cooperation_phase");
					bean.setHeZuoQi(heZuoQi);

					String yunZuoMoShi = getYunZuoMoShi(target);
					bean.setYunZuoMoShi(yunZuoMoShi);

					String fuFeiFangShi = getFuFeiFangShi(target);
					bean.setFuFeiFangShi(fuFeiFangShi);

					String zhaoBiaoDanWei = getZhaoBiaoDanWei(target);
					bean.setZhaoBiaoDanWei(zhaoBiaoDanWei);

					String caiGouFangShi = getCaiGouFangShi(target);
					bean.setCaiGouFangShi(caiGouFangShi);

					String daiLiJiGou = getDaiLiJiGou(target);
					bean.setDaiLiJiGou(daiLiJiGou);

					String xiangMuGaiKuang = target.getString("project_survey");
					bean.setXiangMuGaiKuang(xiangMuGaiKuang);

					List<String> zhongBiaoRen = getZhongBiaoRen(target);
					bean.setZhongBiaoRen(zhongBiaoRen);

					List<Map<String, String>> biaoDi = getBiaoDi(target);
					bean.setBiaoDi(biaoDi);

					List<Map<String, String>> fuJian = getFuJian(target);
					bean.setFuJian(fuJian);

					PPP_DATA.add(bean);
				}
			}
		}
	}

	private static List<Map<String, String>> getFuJian(JSONObject target) {
		List<Map<String, String>> result = new ArrayList<Map<String, String>>();
		// String p = target.getString("notice_url");
		// JSONArray json = JSONArray.fromObject(p);
		// if (json.size() > 0) {
		// int len = json.size();
		// for (int i = 0; i < len; i++) {
		// JSONObject temp = (JSONObject) json.get(i);
		// Map<String, String> map = new HashMap<String, String>();
		// map.put("name", temp.getString("name"));
		// map.put("url", temp.getString("url"));
		// result.add(map);
		// }
		// }
		return result;
	}

	private static List<Map<String, String>> getBiaoDi(JSONObject target) {
		List<Map<String, String>> result = new ArrayList<Map<String, String>>();
		JSONArray p = target.getJSONArray("bid");
		if (p.size() > 0) {
			int len = p.size();
			for (int i = 0; i < len; i++) {
				JSONObject temp = (JSONObject) p.get(i);
				if(temp.containsKey("bid_type")) {
					JSONObject bidTypeJson = temp.getJSONObject("bid_type");
					Map<String, String> map = new HashMap<String, String>();
					map.put("name", bidTypeJson.getString("value"));
					String bidValue = temp.getString("value");
					JSONObject sysDictionary = temp.getJSONObject("sys_dictionary");
					if(sysDictionary != null && sysDictionary.containsKey("value")) {
						bidValue = bidValue + sysDictionary.getString("value");
					}
					

					map.put("value", bidValue);

					result.add(map);
				}
				
			}
		}
		return null;
	}

	private static String getFuFeiFangShi(JSONObject target) {

		String result = "";
		JSONArray p = target.getJSONArray("sys_dictionary_payment_method");
		if (p.size() > 0) {
			result = p.getJSONObject(0).getString("value");
		}
		return result;
	}

	private static List<String> getZhongBiaoRen(JSONObject target) {
		List<String> result = new ArrayList<String>();
		JSONArray p = target.getJSONArray("view_company_project");
		if (p.size() > 0) {
			int len = p.size();
			for (int i = 0; i < len; i++) {
				JSONObject temp = (JSONObject) p.get(i);
				result.add(temp.getString("name"));
			}
		}
		return result;
	}

	private static String getDaiLiJiGou(JSONObject target) {
		String result = "";
		JSONObject p = target.getJSONObject("agency_company");
		if (p != null && p.containsKey("name")) {
			result = p.getString("name");
		}
		return result;
	}

	private static String getCaiGouFangShi(JSONObject target) {
		String result = "";
		JSONArray p = target.getJSONArray("procurement_mode");
		if (p.size() > 0) {
			result = p.getJSONObject(0).getString("value");
		}
		return result;
	}

	private static String getZhaoBiaoDanWei(JSONObject target) {
		String result = "";
		JSONArray p = target.getJSONArray("bid_company_id");
		if (p.size() > 0) {
			result = p.getJSONObject(0).getString("name");
		}
		return result;
	}

	private static String getYunZuoMoShi(JSONObject target) {
		String result = "";
		JSONArray p = target.getJSONArray("sys_dictionary_operation_pattern");
		if (p.size() > 0) {
			result = p.getJSONObject(0).getString("value");
		}
		return result;
	}

	private static String getSuoShuHangYe(JSONObject target) {
		String result = "";
		JSONArray firstArr = target.getJSONArray("first_industry");
		if (firstArr.size() > 0) {
			result = firstArr.getJSONObject(0).getString("value");
		}

		JSONArray secondArr = target.getJSONArray("second_industry");
		if (secondArr.size() > 0) {
			result += "-" + secondArr.getJSONObject(0).getString("value");
		}
		return result;
	}

	private static String getDiQu(JSONObject target) {
		// TODO Auto-generated method stub
		String result = "";
		JSONArray provinceArr = target.getJSONArray("province");
		if (provinceArr.size() > 0) {
			result += provinceArr.getJSONObject(0).getString("name");
		}

		JSONArray cityArr = target.getJSONArray("city");
		if (cityArr.size() > 0) {
			result += "-" + cityArr.getJSONObject(0).getString("name");
		}

		String townStr = target.getString("town");
		if (!"未知".equals(townStr.trim())) {
			JSONArray townArr = JSONArray.fromObject(townStr);
			if (townArr.size() > 0) {
				result += "-" + townArr.getJSONObject(0).getString("name");
			}

		}

		return result;
	}

	private static JSONArray getListJSONData(CloseableHttpClient client) {
		JSONArray result = new JSONArray();
		int page = 1;
		boolean isContinue = true;

		while (isContinue) {

			if (page > MAX_PAGE) {
				break;
			}

			if (page % 5 == 0) {
				refreshToken(client);
			}

			String url = DATA_URL + page + "&token=" + TOKEN;
			sendListOptions(client, url);
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

	private static void filterData() {
		// Collections.sort(PPP_DATA, new Comparator<DataBean>() {
		// @Override
		// public int compare(DataBean b1, DataBean b2) {
		// return b1.getCityCode().compareTo(b2.getCityCode());
		// }
		//
		// });

		List<DataBean> tempData = new ArrayList<DataBean>();
		for (DataBean bean : PPP_DATA) {
			if (!isDuplicate(bean, tempData)) {
				tempData.add(bean);
			}
		}
		PPP_DATA = tempData;
	}

	private static boolean isDuplicate(DataBean bean, List<DataBean> tempData) {
		for (DataBean db : tempData) {
			// if (db.getProjectName().equals(bean.getProjectName()) &&
			// db.getArea().equals(bean.getArea())
			// && db.getCapital().equals(bean.getCapital()) &&
			// db.getProgress().equals(bean.getProgress())) {
			// return true;
			// }
		}
		return false;
	}

	private static void writeToExcel() {
		Date d = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		String excelFilePath = TARGET_DATA_PATH + "data_" + sdf.format(d) + ".xls";
		ExcelUtil.writeExcel(PPP_DATA, excelFilePath);
		System.out.println(excelFilePath);
		System.out.println("成功写入excel文件，本次抓取的数据总数=" + PPP_DATA.size());
	}

	public static void main(String[] args) throws Exception {
		start();
	}

}
