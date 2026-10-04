package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.service.writing.DoubaoWebSearchProvider;
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
    private final DoubaoWebSearchProvider search;
    public MechanicalPaperPhotoService(DoubaoWebSearchProvider search) { this.search = search; }
    public record Photo(String file, String pageUrl, String imageUrl) {}
    public Photo find(String title, Path directory) throws Exception {
        for (var source : search.searchMechanicalImagePages(title)) {
            try {
                String html = new String(download(URI.create(source.url()), 2_000_000), StandardCharsets.UTF_8);
                for (URI image : imageCandidates(URI.create(source.url()), html)) {
                    try {
                        byte[] bytes = download(image, 12_000_000);
                        var bitmap = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (bitmap == null || bitmap.getWidth() < 400 || bitmap.getHeight() < 300) continue;
                        Path file = directory.resolve("photo.png");
                        ImageIO.write(bitmap, "png", file.toFile());
                        return new Photo(file.getFileName().toString(), source.url(), image.toString());
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException("Doki 未找到可下载的实物照片，请重试实物图任务");
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
