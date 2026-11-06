package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.service.writing.DoubaoWebSearchProvider;
import com.dropai.rewrite.service.MatrixDesignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

@Service
public class MechanicalPaperPhotoService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MechanicalPaperPhotoService.class);
    private final DoubaoWebSearchProvider search;
    private final MatrixDesignService vision;
    public MechanicalPaperPhotoService(DoubaoWebSearchProvider search,MatrixDesignService vision) { this.search = search; this.vision=vision; }
    public record Photo(String file, String pageUrl, String imageUrl) {}
    public Photo find(String title, Path directory) throws Exception {
        Set<URI> seen=new HashSet<>();int inspected=0;
        for (var source : search.searchMechanicalImagePages(title)) {
            URI page=URI.create(source.url());
            log.info("Mechanical photo checking host {}",page.getHost());
            int pageInspected=0;
            try {
                byte[] pageBytes=download(page,12_000_000);
                var candidates=sourceCandidates(page,pageBytes);
                for (URI image : candidates) {
                    if(pageInspected>=3 || inspected>=12 || !seen.add(image))continue;
                    try {
                        byte[] bytes = image.equals(page)?pageBytes:download(image, 12_000_000);
                        var bitmap = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (bitmap == null || bitmap.getWidth() < 320 || bitmap.getHeight() < 240) continue;
                        pageInspected++;inspected++;
                        // Normalize color mode and bound pixels before sending to the existing vision model.
                        double ratio=Math.min(1,1200.0/Math.max(bitmap.getWidth(),bitmap.getHeight()));
                        var preview=new java.awt.image.BufferedImage((int)(bitmap.getWidth()*ratio),(int)(bitmap.getHeight()*ratio),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics=preview.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,preview.getWidth(),preview.getHeight());graphics.drawImage(bitmap,0,0,preview.getWidth(),preview.getHeight(),null);graphics.dispose();
                        var encoded=new ByteArrayOutputStream();ImageIO.write(preview,"jpeg",encoded);
                        String response=vision.inspectImage("你是实物图核验员，只返回JSON。",
                            "判断图片是否是题目【"+title+"】同类设备或夹具的真实实物照片，用于第一章介绍同类产品，不要求与后续原创设计尺寸或形状完全相同。接受完整实拍钻模或工业钻孔夹具，允许不同被加工工件；拒绝工程图、目录页、渲染图、广告拼图、刀具夹头、单个工具和与钻孔定位夹紧无关的产品。钻孔夹具应有专用夹具体或定位夹紧及导向结构，不是钻头夹头。返回{\"accepted\":true或false,\"reason\":\"理由\"}。",
                            "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(encoded.toByteArray()));
                        int begin=response.indexOf('{'),end=response.lastIndexOf('}');
                        if(begin<0||end<begin)continue;
                        var verdict=new ObjectMapper().readTree(response.substring(begin,end+1));
                        log.info("Mechanical photo candidate accepted={} reason={}",verdict.path("accepted").asBoolean(false),verdict.path("reason").asText());
                        if(!verdict.path("accepted").asBoolean(false))continue;
                        Path file = directory.resolve("photo.png");
                        ImageIO.write(bitmap, "png", file.toFile());
                        return new Photo(file.getFileName().toString(), source.url(), image.toString());
                    } catch (Exception error) { log.debug("Mechanical photo candidate unavailable: {}",error.getClass().getSimpleName()); }
                }
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException("Doki 未找到可下载的实物照片，请重试实物图任务");
    }
    static List<URI> sourceCandidates(URI source,byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes))!=null?List.of(source):imageCandidates(source,new String(bytes,StandardCharsets.UTF_8));
    }
    static List<URI> imageCandidates(URI page, String html) {
        List<URI> result = new ArrayList<>();
        Matcher tags = Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (!tag.matches("(?is).*?(og:image|twitter:image).*")) continue;
            Matcher content = Pattern.compile("(?is)\\bcontent\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(tag);
            if (content.find()) addImage(result, page, content.group(1));
        }
        Matcher imgs = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
        while (imgs.find() && result.size() < 12) {
            String tag = imgs.group();
            if (tag.matches("(?is).*?(logo|icon|avatar|banner|qrcode|qr-code).*")) continue;
            Matcher src = Pattern.compile("(?is)\\b(?:data-src|src)\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(tag);
            if (src.find()) addImage(result, page, src.group(1));
        }
        return result.stream().distinct().limit(10).toList();
    }
    private static void addImage(List<URI> output, URI page, String path) {
        try { URI uri = page.resolve(path.replace("&amp;", "&")); if (List.of("http","https").contains(uri.getScheme())) output.add(uri); } catch (Exception ignored) { }
    }
    static void validatePublic(URI uri) throws Exception {
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) throw new IOException("非公开图片地址");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] raw = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress() || (raw.length == 16 && (raw[0] & 0xfe) == 0xfc)
                || (raw.length == 4 && ((raw[0] & 255) == 0 || (raw[0] & 255) >= 224
                    || ((raw[0] & 255) == 100 && (raw[1] & 255) >= 64 && (raw[1] & 255) <= 127)))) throw new IOException("禁止访问内网地址");
        }
    }
    private static byte[] download(URI initial, int limit) throws Exception {
        URI uri = initial;
        for (int hop = 0; hop < 4; hop++) {
            validatePublic(uri);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Doki-Paper/1.0");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { uri = uri.resolve(connection.getHeaderField("Location")); continue; }
                if (status != 200 || connection.getContentLengthLong() > limit) throw new IOException("图片页面不可访问");
                try (InputStream input = connection.getInputStream()) {
                    byte[] bytes = input.readNBytes(limit + 1);
                    if (bytes.length > limit) throw new IOException("图片过大");
                    return bytes;
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("图片重定向次数过多");
    }
}
