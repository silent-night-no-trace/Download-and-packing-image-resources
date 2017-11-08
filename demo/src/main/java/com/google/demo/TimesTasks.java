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
     * 获取图片地址
     */
    public  void getImgUrl(){
        int begin=0;
        while (true) {
         
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

}




