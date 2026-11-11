package com.duan.lynkmanager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 极简 HTTP 服务器：GET / 返回上传页，POST /upload 接收 multipart 文件，
 * GET /dl 返回车机侧文件列表（上传的文件 + 导出的 APK），GET /get 下载单个文件。
 * 不依赖第三方库，只在局域网内使用。
 */
public class SimpleHttpServer {

    public interface Listener {
        void onEvent(String msg);
    }

    private static final long MAX_BODY = 256L * 1024 * 1024;
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};

    private final File dir;
    private final File exportDir;
    private final Listener listener;
    private ServerSocket server;
    private Thread acceptThread;
    private volatile boolean running;

    public SimpleHttpServer(File dir, File exportDir, Listener listener) {
        this.dir = dir;
        this.exportDir = exportDir;
        this.listener = listener;
    }

    public void start() throws IOException {
        IOException last = null;
        for (int port = 8080; port <= 8090; port++) {
            try {
                server = new ServerSocket(port);
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) {
            throw last == null ? new IOException("没有可用端口") : last;
        }
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "lynk-http");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log("服务已启动，端口 " + getPort());
    }

    public void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
        server = null;
        log("服务已停止");
    }

    public boolean isRunning() {
        return running && server != null;
    }

    public int getPort() {
        return server == null ? -1 : server.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                handleAsync(socket);
            } catch (IOException e) {
                if (running) {
                    log("接受连接失败：" + e.getMessage());
                }
            }
        }
    }

    private void handleAsync(final Socket socket) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    handle(socket);
                } catch (Throwable t2) {
                    log("请求异常：" + t2.getClass().getSimpleName());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "/";

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                headers.put(line.substring(0, idx).trim().toLowerCase(Locale.US),
                        line.substring(idx + 1).trim());
            }
        }

        if ("GET".equals(method)) {
            String query = "";
            int qi = path.indexOf('?');
            if (qi >= 0) {
                query = path.substring(qi + 1);
                path = path.substring(0, qi);
            }
            Map<String, String> params = parseQuery(query);

            if (path.equals("/") || path.isEmpty()) {
                byte[] page = PAGE.getBytes(StandardCharsets.UTF_8);
                respond(socket, 200, "text/html; charset=utf-8", page);
                return;
            }
            if (path.equals("/dl")) {
                respond(socket, 200, "text/html; charset=utf-8", dlPage());
                return;
            }
            if (path.equals("/get")) {
                File f = resolve(params.get("d"), params.get("f"));
                if (f == null) {
                    respond(socket, 404, "text/plain; charset=utf-8",
                            "not found".getBytes(StandardCharsets.UTF_8));
                } else {
                    log("下载 " + f.getName() + "（" + (f.length() / 1024) + " KB）");
                    respondFile(socket, f);
                }
                return;
            }
            if (path.equals("/files")) {
                respond(socket, 200, "text/plain; charset=utf-8",
                        listFiles().getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(socket, 404, "text/plain; charset=utf-8",
                    "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        if ("POST".equals(method) && path.startsWith("/upload")) {
            String lenStr = headers.get("content-length");
            long len = lenStr == null ? 0 : Long.parseLong(lenStr);
            if (len <= 0) {
                respond(socket, 400, "text/plain; charset=utf-8",
                        "empty body".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (len > MAX_BODY) {
                respond(socket, 413, "text/plain; charset=utf-8",
                        ("too large: limit " + (MAX_BODY / 1048576) + " MB")
                                .getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = readFully(in, (int) len);

            String contentType = headers.get("content-type");
            String boundary = extractBoundary(contentType);
            String result;
            if (boundary == null) {
                result = "ERROR 缺少 multipart 边界";
            } else {
                List<File> saved = parseMultipart(body, boundary);
                StringBuilder sb = new StringBuilder();
                for (File f : saved) {
                    sb.append("OK ").append(f.getName()).append('\n');
                }
                if (saved.isEmpty()) {
                    sb.append("ERROR 没有解析到文件\n");
                }
                result = sb.toString();
                for (File f : saved) {
                    log("收到 " + f.getName() + "（" + (f.length() / 1024) + " KB）");
                }
            }
            respond(socket, 200, "text/plain; charset=utf-8",
                    result.getBytes(StandardCharsets.UTF_8));
            return;
        }

        respond(socket, 404, "text/plain; charset=utf-8",
                "not found".getBytes(StandardCharsets.UTF_8));
    }

    // ---------------- multipart ----------------

    private String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String seg : contentType.split(";")) {
            String s = seg.trim();
            if (s.toLowerCase(Locale.US).startsWith("boundary=")) {
                String b = s.substring("boundary=".length()).trim();
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() >= 2) {
                    b = b.substring(1, b.length() - 1);
                }
                return b.isEmpty() ? null : b;
            }
        }
        return null;
    }

    private List<File> parseMultipart(byte[] body, String boundary) {
        List<File> saved = new ArrayList<>();
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = 0;
        while (true) {
            int d = indexOf(body, delim, pos);
            if (d < 0) {
                break;
            }
            int after = d + delim.length;
            if (after + 1 < body.length && body[after] == '-' && body[after + 1] == '-') {
                break; // 结束边界
            }
            if (after + 1 < body.length && body[after] == '\r' && body[after + 1] == '\n') {
                after += 2;
            }
            int hdrEnd = indexOf(body, HEADER_END, after);
            if (hdrEnd < 0) {
                break;
            }
            String partHeaders = new String(body, after, hdrEnd - after,
                    StandardCharsets.ISO_8859_1);
            int dataStart = hdrEnd + HEADER_END.length;
            int next = indexOf(body, delim, dataStart);
            if (next < 0) {
                break;
            }
            int dataEnd = next;
            if (dataEnd - 2 >= dataStart && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }
            String name = extractFilename(partHeaders);
            if (name != null) {
                File out = uniqueFile(sanitize(name));
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(out);
                    fos.write(body, dataStart, dataEnd - dataStart);
                    saved.add(out);
                } catch (IOException e) {
                    log("写入失败：" + e.getMessage());
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
            pos = next;
        }
        return saved;
    }

    private String extractFilename(String headers) {
        String key = "filename=\"";
        int i = headers.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = headers.indexOf('"', start);
        if (end <= start) {
            return null;
        }
        return headers.substring(start, end);
    }

    private String sanitize(String name) {
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\x00-\\x1f<>:\"|?*]", "_").trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            base = "upload-" + System.currentTimeMillis() + ".bin";
        }
        return base;
    }

    private File uniqueFile(String name) {
        File f = new File(dir, name);
        if (!f.exists()) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File c = new File(dir, stem + "(" + i + ")" + ext);
            if (!c.exists()) {
                return c;
            }
        }
        return new File(dir, System.currentTimeMillis() + "-" + name);
    }

    private String listFiles() {
        File[] fs = dir.listFiles();
        StringBuilder sb = new StringBuilder();
        if (fs != null) {
            for (File f : fs) {
                sb.append(f.getName()).append("\t").append(f.length()).append('\n');
            }
        }
        return sb.toString();
    }

    // ---------------- 工具 ----------------

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        if (needle.length == 0) {
            return -1;
        }
        byte first = needle[0];
        int limit = hay.length - needle.length;
        outer:
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (hay[i] != first) {
                continue;
            }
            for (int j = 1; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                bos.write(c);
            }
        }
        if (c == -1 && bos.size() == 0) {
            return null;
        }
        return new String(bos.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(buf, off, length - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        if (off < length) {
            byte[] shorter = new byte[off];
            System.arraycopy(buf, 0, shorter, 0, off);
            return shorter;
        }
        return buf;
    }

    private static void respond(Socket socket, int code, String contentType, byte[] body)
            throws IOException {
        OutputStream out = socket.getOutputStream();
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "ERROR") + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private void log(String msg) {
        if (listener != null) {
            listener.onEvent(msg);
        }
    }

    // ---------------- 下载（GET /dl、GET /get） ----------------

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(urlDecode(k), urlDecode(v));
        }
        return map;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    /** d=up 取上传目录，d=ex 取导出目录；拒绝带路径分隔符或 .. 的文件名。 */
    private File resolve(String which, String name) {
        if (name == null) {
            return null;
        }
        String clean = name.replace('\\', '/');
        if (clean.contains("/") || clean.contains("..")) {
            return null;
        }
        File base = "ex".equals(which) ? exportDir : dir;
        if (base == null) {
            return null;
        }
        File f = new File(base, clean);
        return f.isFile() ? f : null;
    }

    private static void respondFile(Socket socket, File f) throws IOException {
        String encoded = urlEncode(f.getName()).replace("+", "%20");
        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: " + f.length() + "\r\n"
                + "Content-Disposition: attachment; filename=\"" + asciiName(f.getName())
                + "\"; filename*=UTF-8''" + encoded + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes(StandardCharsets.UTF_8));
        InputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String asciiName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(c > 31 && c < 128 && c != '"' ? c : '_');
        }
        return sb.toString();
    }

    private byte[] dlPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>车机文件下载</title><style>")
          .append("*{box-sizing:border-box}")
          .append("body{margin:0;padding:20px;background:#101418;color:#e8edf2;")
          .append("font-family:-apple-system,BlinkMacSystemFont,\"PingFang SC\",\"Microsoft YaHei\",sans-serif}")
          .append("h1{font-size:20px;margin:0 0 6px}")
          .append(".sub{color:#8a97a6;font-size:13px;margin-bottom:18px}")
          .append("h2{font-size:14px;color:#8a97a6;margin:22px 0 10px;font-weight:400}")
          .append(".card{background:#1b2128;border-radius:12px;padding:14px;margin-bottom:10px;")
          .append("display:flex;align-items:center;justify-content:space-between;gap:12px}")
          .append(".n{font-size:14px;word-break:break-all}")
          .append(".m{color:#8a97a6;font-size:12px;margin-top:4px}")
          .append("a.dl{flex:none;padding:9px 16px;border-radius:9px;background:#4caf50;color:#fff;")
          .append("text-decoration:none;font-size:13px}")
          .append(".empty{color:#5c6874;font-size:13px;padding:10px}")
          .append("a.back{display:inline-block;margin-top:22px;color:#4caf50;font-size:13px;text-decoration:none}")
          .append("</style></head><body>")
          .append("<h1>车机文件下载</h1>")
          .append("<div class=\"sub\">点右侧「下载」直接存到电脑或手机</div>");
        appendGroup(sb, "上传的文件", dir, "up");
        appendGroup(sb, "导出的 APK", exportDir, "ex");
        sb.append("<a class=\"back\" href=\"/\">← 返回上传页</a></body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendGroup(StringBuilder sb, String title, File folder, String key) {
        sb.append("<h2>").append(title).append("</h2>");
        List<File> files = new ArrayList<>();
        File[] fs = folder == null ? null : folder.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && !f.getName().startsWith(".")) {
                    files.add(f);
                }
            }
        }
        if (files.isEmpty()) {
            sb.append("<div class=\"empty\">暂无文件</div>");
            return;
        }
        files.sort(new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (File f : files) {
            sb.append("<div class=\"card\"><div><div class=\"n\">").append(esc(f.getName()))
              .append("</div><div class=\"m\">").append(sizeText(f.length())).append("</div></div>")
              .append("<a class=\"dl\" href=\"/get?d=").append(key).append("&amp;f=")
              .append(urlEncode(f.getName())).append("\">下载</a></div>");
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    // ---------------- 上传页 ----------------

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>传到车机</title>
            <style>
              *{box-sizing:border-box}
              body{margin:0;padding:20px;background:#101418;color:#e8edf2;
                   font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}
              h1{font-size:20px;margin:0 0 6px}
              .sub{color:#8a97a6;font-size:13px;margin-bottom:20px}
              .card{background:#1b2128;border-radius:14px;padding:18px;margin-bottom:14px}
              label.file{display:block;border:2px dashed #2c343e;border-radius:12px;
                   padding:28px 16px;text-align:center;color:#8a97a6;font-size:14px}
              input[type=file]{display:none}
              .btn{display:block;width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;
                   background:#4caf50;color:#fff;font-size:16px}
              .btn:disabled{background:#2c343e;color:#8a97a6}
              .bar{height:6px;background:#0c1014;border-radius:3px;overflow:hidden;margin-top:14px;display:none}
              .bar i{display:block;height:100%;width:0;background:#4caf50;transition:width .2s}
              .log{font-family:ui-monospace,Consolas,monospace;font-size:12px;color:#a8b4c0;
                   white-space:pre-wrap;word-break:break-all;max-height:200px;overflow:auto}
              .ok{color:#9be08a}
              .err{color:#e05b5b}
              ul.files{list-style:none;margin:14px 0 0;padding:0}
              ul.files li{display:flex;align-items:center;gap:12px;background:#0f1418;
                   border:1px solid #2c343e;border-radius:10px;padding:10px;margin-bottom:8px}
              .thumb{width:40px;height:40px;flex:none;border-radius:8px;background:#232b34;
                   display:flex;align-items:center;justify-content:center;overflow:hidden;
                   font-size:10px;color:#8a97a6;font-weight:700;letter-spacing:.5px}
              .thumb img{width:100%;height:100%;object-fit:cover}
              .meta{flex:1;min-width:0}
              .fname{font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
              .fsize{font-size:11px;color:#8a97a6;margin-top:3px}
              .rm{flex:none;width:30px;height:30px;border:0;border-radius:8px;background:#2c343e;
                   color:#c8d2dc;font-size:17px;line-height:1;cursor:pointer}
              .rm:hover{background:#8f3a3a;color:#fff}
              .empty{color:#5c6874;font-size:13px;text-align:center;padding:8px 0 0}
              label.file:hover{border-color:#4caf50;color:#9be08a}
            </style>
            </head>
            <body>
            <h1>传到车机</h1>
            <div class="sub">选好文件后点上传，同一个 Wi-Fi 下即可 · <a href="/dl" style="color:#4caf50;text-decoration:none">下载车机文件 →</a></div>

            <div class="card">
              <label class="file" for="f" id="pick">点击选择文件（可多选，可多次添加）</label>
              <input id="f" type="file" multiple>
              <ul class="files" id="list"></ul>
              <div class="empty" id="empty">还没有选择文件</div>
              <button class="btn" id="go" disabled>上传</button>
              <div class="bar" id="bar"><i id="fill"></i></div>
            </div>

            <div class="card">
              <div class="log" id="log">等待选择文件…</div>
            </div>

            <script>
            var picked=[];
            var f=document.getElementById('f'),go=document.getElementById('go'),
                pick=document.getElementById('pick'),logEl=document.getElementById('log'),
                bar=document.getElementById('bar'),fill=document.getElementById('fill'),
                listEl=document.getElementById('list'),emptyEl=document.getElementById('empty');

            function say(t,cls){var d=document.createElement('div');
              if(cls)d.className=cls; d.textContent=t;
              logEl.appendChild(d); logEl.scrollTop=logEl.scrollHeight;}

            function sizeText(n){
              if(n<1024){return n+' B';}
              if(n<1048576){return (n/1024).toFixed(1)+' KB';}
              return (n/1048576).toFixed(1)+' MB';
            }

            function extOf(name){var i=name.lastIndexOf('.');
              return i>0?name.slice(i+1).toUpperCase().slice(0,4):'FILE';}

            function render(){
              listEl.innerHTML='';
              emptyEl.style.display=picked.length?'none':'block';
              for(var idx=0;idx<picked.length;idx++){(function(file){
                var li=document.createElement('li');
                var th=document.createElement('div'); th.className='thumb';
                if(file.type&&file.type.indexOf('image/')===0){
                  var img=document.createElement('img');
                  img.src=URL.createObjectURL(file);
                  th.appendChild(img);
                }else{
                  th.textContent=extOf(file.name);
                }
                var meta=document.createElement('div'); meta.className='meta';
                var nm=document.createElement('div'); nm.className='fname';
                nm.textContent=file.name; nm.title=file.name;
                var sz=document.createElement('div'); sz.className='fsize';
                sz.textContent=sizeText(file.size);
                meta.appendChild(nm); meta.appendChild(sz);
                var rm=document.createElement('button'); rm.className='rm'; rm.textContent='×';
                rm.title='移除这个文件';
                rm.onclick=function(){var k=picked.indexOf(file);
                  if(k>=0){picked.splice(k,1); render();}};
                li.appendChild(th); li.appendChild(meta); li.appendChild(rm);
                listEl.appendChild(li);
              })(picked[idx]);}
              go.disabled=picked.length===0;
              go.textContent=picked.length?('上传 '+picked.length+' 个文件'):'上传';
              pick.textContent=picked.length?'继续添加文件':'点击选择文件（可多选，可多次添加）';
            }

            f.onchange=function(){
              for(var i=0;i<f.files.length;i++){
                var nf=f.files[i],dup=false;
                for(var j=0;j<picked.length;j++){
                  if(picked[j].name===nf.name&&picked[j].size===nf.size){dup=true;break;}
                }
                if(!dup){picked.push(nf);}
              }
              f.value='';
              render();
            };

            go.onclick=async function(){
              if(!picked.length){return;}
              go.disabled=true; bar.style.display='block';
              var total=picked.length,done=0;
              while(picked.length){
                var file=picked[0];
                var fd=new FormData(); fd.append('file',file,file.name);
                fill.style.width=(done/total*100)+'%';
                say('上传 '+file.name+' …');
                try{
                  var r=await fetch('/upload',{method:'POST',body:fd});
                  var t=(await r.text()).trim();
                  say(t,t.indexOf('OK')===0?'ok':'err');
                  if(t.indexOf('OK')===0){picked.splice(0,1); render();}
                }catch(e){ say('失败：'+e.message,'err'); break; }
                done++;
              }
              fill.style.width='100%';
              setTimeout(function(){bar.style.display='none';fill.style.width='0';},800);
              render();
            };

            render();
            </script>
            </body>
            </html>
            """;
}
