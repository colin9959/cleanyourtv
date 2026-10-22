package com.gathin.tvhome;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal LAN-only HTTP server for the phone upload page. Serves one upload form on GET / and saves
 * raw POST bodies (the phone sends file bytes directly, no multipart parsing) into the uploads/ folder
 * the screensaver rotates. Single-threaded on purpose: this is for one household phone.
 */
final class UploadServer {

    static final int PORT = 8080;

    private final File uploadsDir;
    private ServerSocket socket;
    private Thread thread;
    private volatile boolean running;
    private int saved;

    UploadServer(File uploadsDir) {
        this.uploadsDir = uploadsDir;
        uploadsDir.mkdirs();
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "tvhome-upload");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        thread = null;
    }

    int savedCount() {
        return saved;
    }

    private void loop() {
        try {
            socket = new ServerSocket(PORT);
        } catch (Exception e) {
            return;
        }
        while (running) {
            try (Socket client = socket.accept()) {
                handle(client);
            } catch (Exception ignored) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket client) throws Exception {
        client.setTcpNoDelay(true);
        DataInputStream in = new DataInputStream(client.getInputStream());
        DataOutputStream out = new DataOutputStream(client.getOutputStream());

        String requestLine = in.readLine();
        if (requestLine == null) return;
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "GET";
        String path = parts.length > 1 ? parts[1] : "/";
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine()).isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(),
                    line.substring(colon + 1).trim());
        }

        if ("GET".equals(method)) {
            html(out);
        } else if ("POST".equals(method) && "/upload".equals(path)) {
            long len = parseLong(headers.get("content-length"));
            String ext = extOf(headers.getOrDefault("x-filename", "photo.jpg"));
            File target = new File(uploadsDir, System.currentTimeMillis() + "_" + ext);
            saveBody(in, len, target);
            saved++;
            plain(out, 200, "OK " + saved);
        } else {
            plain(out, 404, "not found");
        }
        out.flush();
    }

    private void saveBody(DataInputStream in, long len, File target) throws Exception {
        byte[] buffer = new byte[8192];
        long left = len;
        try (FileOutputStream fos = new FileOutputStream(target)) {
            while (left > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (n < 0) break;
                fos.write(buffer, 0, n);
                left -= n;
            }
        }
    }

    private void html(DataOutputStream out) throws Exception {
        String page = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>上传屏保照片</title>"
                + "<body style='margin:0;background:#101418;color:#eee;font-family:system-ui;display:flex;min-height:100vh;align-items:center;justify-content:center'>"
                + "<div style='max-width:420px;padding:24px;text-align:center'>"
                + "<h2>上传到电视屏保</h2>"
                + "<p style='color:#9aa5b1'>选择相册图片，自动加入电视轮播。仅局域网，不会上传到网络。</p>"
                + "<input type=file id=f multiple accept='image/*' style='width:100%;padding:12px'>"
                + "<p id=status style='color:#7fd489'></p>"
                + "<script>const f=document.getElementById('f'),s=document.getElementById('status');"
                + "f.onchange=async()=>{let n=0;for(const file of f.files){const r=await fetch('/upload',{method:'POST',headers:{'X-Filename':file.name},body:file});if(r.ok)n++;}s.textContent='已上传 '+n+' 张';}</script>"
                + "</div></body>";
        byte[] b = page.getBytes(StandardCharsets.UTF_8);
        out.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n");
        out.write(b);
    }

    private void plain(DataOutputStream out, int code, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.writeBytes("HTTP/1.1 " + code + " OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n");
        out.write(b);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot).toLowerCase() : ".jpg";
        if (!ext.matches("\\.(jpg|jpeg|png|webp)")) ext = ".jpg";
        return "photo" + ext;
    }
}
