package org.rsscount.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Downloads remote images to local storage and serves them via a static path.
 * Gracefully degrades by returning the original URL on any failure.
 */
@ApplicationScoped
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private static final int MAX_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int TIMEOUT_MS = 10_000;

    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "avif", "tiff", "tif"
    );

    @ConfigProperty(name = "image.storage.path", defaultValue = "/app/data/img")
    String storagePath;

    /**
     * Download image from remote URL, save to local, return local URL.
     * If download fails, returns the original URL (graceful degradation).
     *
     * @param originalUrl remote image URL
     * @return local URL path (e.g. /static/images/{hash}.{ext}) or original URL on failure
     */
    public String saveImg(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return originalUrl;
        }
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            return originalUrl;
        }

        try {
            String filename = UUID.randomUUID().toString();
            Path storageDir = Path.of(storagePath);
            Files.createDirectories(storageDir);

            // Download bytes with streaming size check
            byte[] bytes = downloadBytes(originalUrl);

            // Determine extension from actual content (magic bytes) or URL
            String ext = guessExtension(originalUrl, bytes);
            String fullName = filename + "." + ext;
            Path targetFile = storageDir.resolve(fullName);
            Files.write(targetFile, bytes);

            String localPath = "/static/images/" + fullName;
            log.debug("Downloaded image: {} -> {}", originalUrl, localPath);
            return localPath;
        } catch (Exception e) {
            log.warn("Failed to download image: {} ({})", originalUrl, e.getMessage());
            return originalUrl;
        }
    }

    /**
     * Download all bytes from a URL with timeout and max-size guard.
     */
    private byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; RSSCount/1.0)");

        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_SIZE) {
                    throw new IOException("Image exceeds max size of " + MAX_SIZE + " bytes");
                }
            }
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Determine image file extension. Tries magic bytes first (most reliable),
     * then falls back to URL extension, then defaults to "jpg".
     * <p>
     * Package-private for testability.
     */
    String guessExtension(String url, byte[] bytes) {
        String magicExt = detectFormat(bytes);
        if (magicExt != null) return magicExt;

        String urlExt = extFromUrl(url);
        if (urlExt != null) return urlExt;

        return "jpg";
    }

    /**
     * Detect image format from magic bytes (file signature).
     */
    private String detectFormat(byte[] bytes) {
        if (bytes.length < 4) return null;

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "png";
        }
        // GIF: 47 49 46 38
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) {
            return "gif";
        }
        // WEBP: RIFF (52 49 46 46) .... WEBP (57 45 42 50 at offset 8)
        if (bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "webp";
        }
        // BMP: 42 4D
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return "bmp";
        }
        // ICO: 00 00 01 00
        if (bytes[0] == 0x00 && bytes[1] == 0x00 && bytes[2] == 0x01 && bytes[3] == 0x00) {
            return "ico";
        }

        return null;
    }

    /**
     * Extract file extension from URL path. Returns null if unrecognized.
     */
    private String extFromUrl(String url) {
        int dot = url.lastIndexOf('.');
        if (dot < 0 || dot == url.length() - 1) return null;

        String ext = url.substring(dot + 1).toLowerCase();
        // Strip query parameters and fragments
        int q = ext.indexOf('?');
        if (q >= 0) ext = ext.substring(0, q);
        int h = ext.indexOf('#');
        if (h >= 0) ext = ext.substring(0, h);

        return IMAGE_EXTS.contains(ext) ? ext : null;
    }
}
