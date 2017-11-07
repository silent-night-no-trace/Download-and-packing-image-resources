package com.google.demo;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;

import com.sun.deploy.association.utility.AppConstants;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.swing.plaf.synth.SynthScrollBarUI;

import static java.lang.System.out;
import static java.util.regex.Pattern.*;

/**
 * @author
 */

@Component
public class TimesTasks {
    private int threadNum =0;
    private int MaxThreadNum =10;

    //需要打包文件
    @Value("${config.zipSrcFilePath}")
    private String srcZipFilePath ;
    //zip生成位置
    @Value("${config.zipToFilePath}")
    private String zipToFilePath;
    @Value("${config.fileSize}")
    private long fileSize;
    //图片保存保存
    @Value("${config.imagePath}")
    private String imagePath;
    private static ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<String>();
    private static List<String > imagePaths = new ArrayList<>();
    /**
     *  初始化线程
     *
     */

    ExecutorService es = Executors.newFixedThreadPool(5);

    @Scheduled(initialDelay = 5 * 1000, fixedDelay = 5 * 60 * 1000)
    public void downloadImageFile() {

        es.execute(new DownloadThread(queue));
        getImgUrl();

    }

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 5 * 60 * 1000)
    public void zipFile() {
        System.out.println("===========================压缩文件开始========================");
        if(getTotalSizeOfFilesInDir(new File(srcZipFilePath))>fileSize){
            //大于500M 停止执行
            out.println("图片获取完成！！！！！");
            //终止线程执行
            if(!es.isShutdown()){
                es.shutdownNow();
            }
            if(new File(zipToFilePath).exists()){
                //大于设定值自动打包
                fileToZip(srcZipFilePath,zipToFilePath,"images");
                out.println("打包完成！！！！！");
                out.println("状态情况："+es.isShutdown());
            }else{
                new File(zipToFilePath).mkdir();
                //大于设定值自动打包
                fileToZip(srcZipFilePath,zipToFilePath,"images");
                out.println("打包完成！！！！！");
                out.println("状态情况："+es.isShutdown());
            }


        }
    }


    /**
     * 下载操作类
     *
     *
     */
    class DownloadThread implements Runnable  {
        private ConcurrentLinkedQueue<String> queue;
        String imageUrl =null;
        public DownloadThread(ConcurrentLinkedQueue<String> queue){
            this.queue = queue;
        }

        @Override
        public void run() {
            while(true){

                imageUrl = queue.poll();
                out.println("url: ==="+imageUrl);
                while (imageUrl == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        imageUrl = queue.poll();
                        out.println("===================url================="+imageUrl);
                    }
                }
                String uuid = UUID.randomUUID().toString();
                String fileName = "D://image/"+uuid+".jpg";

                Download download = new Download(imageUrl,fileName);
                download.downloadFile();
                //保存一份图片路径
                download.saveImagePath();

            }

        }


    }

    /**
     * 下载文件 相关类
     *
     */
    class Download {
        String imageUrl;
        String filePath;
        public Download(String imgeUrl,String filePath)  {
            this.imageUrl=imgeUrl ;
            this.filePath=filePath;
        }
        InputStream  in =null;
        FileOutputStream   fos =null;
        HttpURLConnection   conn =null;
        byte[] bys = new byte[1024];

        public void downloadFile(){
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(5000);
                InputStream in = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(new File(filePath));
                int len =0;
                while ((len=in.read(bys))!=-1){
                        fos.write(bys,0,len);
                        fos.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                    if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        /**
         * 序列化图片地址 到文件
         */
        public  void saveImagePath(){

            BufferedWriter fos =null;
            try {
                //字节写入流
                fos = new BufferedWriter(new FileWriter(imagePath));
                if (imagePath!=null&&imagePaths.size()>0){

                    for(String s:imagePaths){
                        fos.write("小图片地址："+s+"            ");
                        fos.flush();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                if(fos !=null){
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }


    }

    /**
     *  未使用
     * @return
     */
    public String getImagePath(){
        WebClient webClient = new WebClient();
        webClient.getOptions().setCssEnabled(true);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.waitForBackgroundJavaScript(600 * 1000);

        String url = "http://image.baidu.com/";

        HtmlPage page = null;
        try {
            page = webClient.getPage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        sop("get page success...");
        out.println("get page success...");
        final HtmlForm form = page.getFormByName("f1");
        final HtmlTextInput textField = form.getInputByName("word");
        textField.setValueAttribute("张学友");

        List list = page.getByXPath("//form/span/input[@type=\"submit\"]");
        HtmlSubmitInput go =null;
        if(list!=null&&list.size()>0){
            go = (HtmlSubmitInput) list.get(0);
        }
        HtmlPage p = null;
        if(go!=null){
            try {
                p = (HtmlPage) go.click();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        webClient.waitForBackgroundJavaScript(3 * 1000);
        List imgList =null;
        if(p!=null){
            imgList = p.getByXPath("//div[@class='list']/div/div[@class='imgshadow']");
        }

        HtmlDivision imgDiv = null;
        HtmlAnchor link = null;
        HtmlElement element = null;
        String str = null;
        int begin = 0;
        int end = 0;
        int k = 1;
        if(imgList!=null&&imgList.size()>0){
            for (int i = 0; i < imgList.size()-1; i++) {
                imgDiv = (HtmlDivision) imgList.get(i);
                element = (HtmlElement) imgDiv.getLastElementChild().getLastElementChild();
                str = element.toString();
                if (str.contains("url") && str.contains(".jpg")) {
                    begin = str.indexOf("url") + 4;
                    end = str.indexOf(".jpg") + 4;
                    str = str.substring(begin, end);
                    str = URLDecoder.decode(str);
//                download(str,"C:\\Users\\kendy\\DeskTop\\SpiderFromBD\\");
//                sop("下载成功：");
                    return str;
                } else {
                    str = "";
                }
                if (!str.equals("")) {
                    out.println("百度图片地址" + k++ + ": " + str);
                }
            }
        }

        return "";
    }


    /**
     * 获取图片地址
     */
    public  void getImgUrl(){
        int begin=0;
        while (true) {
            //http://image.baidu.com/search/avatarjson?tn=resultjsonavatarnew&ie=utf-8&word=%E4%B8%9D%E8%A2%9C%E7%BE%8E%E5%A5%B3&cg=girl&pn="+begin+"&rn=30&itg=0&z=0&fr=&lm=-1&ic=0&s=0&st=-1&gsm=4d0d0000005a
            if (getTotalSizeOfFilesInDir(new File(srcZipFilePath)) > fileSize) {
                    //终止线程执行
                    if(!es.isShutdown()){
                        es.shutdownNow();
                    }

                    System.out.println("====================设置值已达到不在获取新图片地址=====================");
            } else {
                String url = "http://image.baidu.com/search/avatarjson?tn=resultjsonavatarnew&ie=utf-8&word=%E4%B8%9D%E8%A2%9C%E7%BE%8E%E5%A5%B3&cg=girl&pn=" + begin + "&rn=30&itg=0&z=0&fr=&lm=-1&ic=0&s=0&st=-1&gsm=4d0d0000005a";
                try {
                    Connection con = Jsoup.connect(url);
                    Document doc = con.ignoreContentType(true).timeout(30000).get();
                    String json = doc.text();

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(json);

                    JsonNode imgs = node.get("imgs");
                    if (imgs.size() == 0) {
                        break;
                    }
                    for (JsonNode item : imgs) {
                        String temp_url = item.get("objURL").asText();
                        if (!temp_url.equals("")) {

                            //保存图片大于 设置值不在添加图片到队列中
                            if (getTotalSizeOfFilesInDir(new File(srcZipFilePath)) > fileSize) {
                                out.println("-----设置值已经达到---");
                            } else {
                                //添加图片到队列中
                                imagePaths.add(temp_url);
                                queue.offer(temp_url);
                            }
                            out.println("=======图片路径======：" + temp_url);

                        } else {
                            out.println("空连接！！！！");
                        }
                    }
                    begin = begin + 30;
                    out.println("完成一百！");
                    if (!queue.isEmpty()) {
                        Thread.sleep(1000);
                        out.println("等待一秒！！！");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    out.println(url);
                    begin = begin + 30;
                }
            }
            //http://image.baidu.com/search/avatarjson?tn=resultjsonavatarnew&ie=utf-8&word=%E4%B8%9D%E8%A2%9C%E7%BE%8E%E5%A5%B3&cg=girl&pn="+begin+"&rn=30&itg=0&z=0&fr=&lm=-1&ic=0&s=0&st=-1&gsm=4d0d0000005a
            //https://image.baidu.com/search/index?tn=baiduimage&ipn=r&ct=201326592&cl=2&lm=-1&st=-1&fm=index&fr=&hs=0&xthttps=111111&sf=1&fmq=&pv=&ic=0&nc=1&z=&se=1&showtab=0&fb=0&width=&height=&face=0&istype=2&ie=utf-8&word=%E5%88%98%E5%BE%B7%E5%8D%8E&oq=%E5%88%98%E5%BE%B7%E5%8D%8E&rsp=-1
        }



    }


    /**
     * 获取文件 大小
     * @param file
     * @return
     */
    private static long getTotalSizeOfFilesInDir(final File file) {
        if (file.isFile()){
            return file.length();
        }

        final File[] children = file.listFiles();
        long total = 0;
        if (children != null){
            for (final File child : children){
                total += getTotalSizeOfFilesInDir(child);
            }

        }

        return total/(1024*1024);
    }
    /**
     * zip 打包
     * @param sourceFilePath
     * @param zipFilePath
     * @param fileName
     * @return
     */
    public synchronized  boolean fileToZip(String sourceFilePath,String zipFilePath,String fileName){
        boolean flag = false;
        File sourceFile = new File(sourceFilePath);
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        ZipOutputStream zos = null;

        if(sourceFile.exists() == false){
            out.println("待压缩的文件目录："+sourceFilePath+"不存在.");
        }else{
            try {
                File zipFile = new File(zipFilePath + "/" + fileName +".zip");
                if(zipFile.exists()){
                    zipFile.delete();
                    out.println(zipFilePath + "目录下存在名字为:" + fileName +".zip" +"打包文件.已经进行删除");
                }
                    File[] sourceFiles = sourceFile.listFiles();
                    if(null == sourceFiles || sourceFiles.length<1){
                        out.println("待压缩的文件目录：" + sourceFilePath + "里面不存在文件，无需压缩.");
                    }else{
                        fos = new FileOutputStream(zipFile);
                        zos = new ZipOutputStream(new BufferedOutputStream(fos));
                        byte[] bufs = new byte[1024*10];
                        for(int i=0;i<sourceFiles.length;i++){
                            //创建ZIP实体，并添加进压缩包
                            ZipEntry zipEntry = new ZipEntry(sourceFiles[i].getName());
                            zos.putNextEntry(zipEntry);
                            //读取待压缩的文件并写进压缩包里
                            fis = new FileInputStream(sourceFiles[i]);
                            bis = new BufferedInputStream(fis, 1024*10);
                            int read = 0;
                            while((read=bis.read(bufs, 0, 1024*10)) != -1){
                                zos.write(bufs,0,read);
                            }
                        }
                        flag = true;
                    }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally{
                //关闭流
                try {
                    if(null != bis){
                        bis.close();
                    }
                    if(null != zos){
                        zos.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
        return flag;
    }

    /**
     *  //async=content
     //q=美女 搜索关键字
     //first=118 开始条数
     //count=35 显示数量
     private static final String BING_IMAGE_SEARCH_URL = "http://www.bing.com/images/async?async=content&q=%s&first=%d&count=%d";
     private static final int PAGE_SIZE = 35;
     private static final String IMAGE_URL_REG = "imgurl:&quot;(https?://[^,]+)&quot;";
     private static final Pattern IMAGE_PATTERN = Pattern.compile(IMAGE_URL_REG);

     @Override
     public String getSearchUrl(String keyword, int page) {
     int begin = page * PAGE_SIZE;
     return String.format(BING_IMAGE_SEARCH_URL, keyword, begin, PAGE_SIZE);
     }

     @Override
     public int parseImageUrl(ConcurrentLinkedQueue<String> queue, StringBuffer data) {
     int count = 0;
     Matcher matcher = IMAGE_PATTERN.matcher(data);
     while (matcher.find()) {
     queue.offer(matcher.group(1));
     count++;
     }
     return count;
     }
     */

    //async=content
    //q=美女 搜索关键字
    //first=118 开始条数
    //count=35 显示数量

    private static final String BING_IMAGE_SEARCH_URL = "http://www.bing.com/images/async?async=content&q=%s&first=%d&count=%d";
    private static final int PAGE_SIZE = 35;
    private static final String IMAGE_URL_REG = "imgurl:&quot;(https?://[^,]+)&quot;";
    private static final Pattern IMAGE_PATTERN = compile(IMAGE_URL_REG);


    public static String getSearchUrl(String keyword, int page) {
        int begin = page * PAGE_SIZE;
        return String.format(BING_IMAGE_SEARCH_URL, keyword, begin, PAGE_SIZE);
    }


    public int parseImageUrl(ConcurrentLinkedQueue<String> queue, StringBuffer data) {
        int count = 0;
        Matcher matcher = IMAGE_PATTERN.matcher(data);
        while (matcher.find()) {
            queue.offer(matcher.group(1));
            count++;
        }
        return count;
    }
    public static  void main(String []args){

        int sum = 0;

        //首先得到10个页面
        List<String> urlMains = CreateMainUrl();


        //使用Jsoup和FastJson解析出所有的图片源链接
        List<String> imageUrls  = CreateImageUrl(urlMains);

        for(String imageUrl : imageUrls) {
            out.println(imageUrl);
        }


    }

    /**
     * 获取图片 从 url
     * @param urlstr
     * @param savepath
     * @return
     */
    public static String getImgFromUrl(String urlstr, String savepath)
    {
        int num = urlstr.indexOf('/',8);
        int extnum = urlstr.lastIndexOf('.');
        String u = urlstr.substring(0,num);
        String ext = urlstr.substring(extnum+1,urlstr.length());
        try{
            long curTime = System.currentTimeMillis();
            Random random = new Random(100000000);
            String fileName = String.valueOf(curTime) + "_"
                    + random.nextInt(100000000) + ext;
            // 图片的路径
            String realPath = "D://image2/"+ savepath;

            URL url = new URL(urlstr);
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("referer", u);       //通过这个http头的伪装来反盗链
            BufferedImage image = ImageIO.read(connection.getInputStream());
            FileOutputStream fout=new FileOutputStream(realPath+fileName);
            if("gif".equals(ext)||"png".equals("png"))
            {
                ImageIO.write(image, ext, fout);
            }
            ImageIO.write(image, "jpg", fout);
            fout.flush();
            fout.close();

            return savepath+fileName;
        }
        catch(Exception e)
        {
            out.print(e.getMessage().toString());
        }
        return "";
    }


    /**
     *
     * @param imageUrl
     * @param list
     * @return
     */
    public static List<String> urlParse(List<String> imageUrl, List<String> list) {
        //首先对网页进行链接请求，拿到网页源码
        for(int i = 0; i < list.size(); i++) {
            String html = request(list.get(i));

            out.println(i);
            //对网页源码进行解析，拿到当前页面的所有图片的链接
            imageUrl = getImageUrl(html, imageUrl);
        }

        return imageUrl;
    }

    /**
     * 使用Jsoup和Fastjson对网页源码进行解析，提取出当前页的所有图片源链接
     * @param html
     * @param imageUrl
     * @return
     */

    public static List<String> getImageUrl(String html, List<String> imageUrl) {
        Document document = Jsoup.parse(html);
        String json = null;

        //首先得到图片源链接所在的json字符串，使用正则表达式
        Pattern pattern = compile("flip.setData\\('imgData'.*\\);");
        Matcher matcher = pattern.matcher(document.toString());

        //将得到的东西转换为正确的Json格式
        while (matcher.find()) {
            json = matcher.group().toString();
        }

        int begin = json.indexOf("\"data\":");
        int last = json.indexOf("});");
        json = json.substring(begin+7, last);

        //对json进行解析，拿到当前页面所有的所有图片源链接
        JSONArray jsonArray = JSONArray.parseArray(json);
        for(int i = 0; i < jsonArray.size()-1; i++) {
            JSONObject temp =  jsonArray.getJSONObject(i);
            String url = temp.getString("objURL");

            imageUrl.add(url);
        }

        return imageUrl;
    }

    /**
     * 网络请求
     * @param url
     * @return
     */
    public static String request(String url) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        String entity = null;

        httpGet.setHeader("Accept", "text/html,application/xhtml+xml," +
                "application/xml;q=0.9,image/webp,*/*;q=0.8");
        httpGet.setHeader("Accept-Encoding", "gzip, deflate, sdch, br");
        httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.8");
        httpGet.setHeader("Cache-Control", "max-age=0");
        httpGet.setHeader("Connection", "keep-alive");
        httpGet.setHeader("Cookie", "BDqhfp=dog%26%26NaN%26%260%26%261;" +
                " BDIMGISLOGIN=0; BAIDUID=BF018787B936DB4DFECB09E7B90A78A6:FG=1;" +
                " BIDUPSID=BF018787B936DB4DFECB09E7B90A78A6; PSTM=1494502324;" +
                " BDRCVFR[tox4WRQ4-Km]=mk3SLVN4HKm; BDRCVFR[-pGxjrCMryR]=mk3SLVN4HKm;" +
                " BDRCVFR[CLK3Lyfkr9D]=mk3SLVN4HKm; " +
                "indexPageSugList=%5B%22bird%22%2C%22dog%22%2C%22github%22%5D; " +
                "cleanHistoryStatus=0; BDORZ=FFFB88E999055A3F8A630C64834BD6D0; " +
                "PSINO=1; H_PS_PSSID=; userFrom=null");
        httpGet.setHeader("Host", "image.baidu.com");
        httpGet.setHeader("Upgrade-Insecure-Requests", "1");
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (X11; " +
                "Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/58.0.3029.110 Safari/537.36");

        try {
            //客户端执行httpGet方法，返回响应
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

            //得到服务响应状态码
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                entity = EntityUtils.toString(httpResponse.getEntity(), "utf-8");
            }

            httpResponse.close();
            httpClient.close();
        } catch (ClientProtocolException e) {
            entity = null;
        } catch (IOException e) {
            entity = null;
        }

        return entity;
    }



    //首先构建需要抓取的10页百度图片页面
    public static List<String> CreateMainUrl() {
        Scanner scanner = new Scanner(System.in);
        String urlMain = scanner.nextLine();
        String urlTemp = urlMain;
        List<String> list = new ArrayList<>();

        //构建需要爬取的10页Url
        for(int i= 0; i < 10; i++)
        {
            urlMain = "http://" + urlMain + "pn=" + i*60+"&spn=0&di=0&pi=42852035170&tn=baiduimagedetail&is=0%2C0&ie=utf-8&oe=utf-8&cs=683638762%2C665943893&os=&simid=&adpicid=0&lpn=0&fm=&sme=&cg=&bdtype=-1&oriquery=&objurl=http%3A%2F%2Fc.hiphotos.baidu.com%2Fimage%2Fpic%2Fitem%2F3b292df5e0fe9925de1b729a3da85edf8cb171e0.jpg&fromurl=&gsm=0&catename=pcindexhot";
            System.out.println("ur;:"+urlMain);
            list.add(urlMain);
            urlMain = urlTemp;
        }

        return list;
    }

    //创建每个要爬取图片的Url
    public static List<String> CreateImageUrl(List<String> list) {
        List<String> imagelist = new ArrayList<>();

        imagelist = urlParse(imagelist, list);

        return imagelist;
    }



}




