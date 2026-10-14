package com.javaaddonengine.resourcepack;

import com.javaaddonengine.core.JaLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Minimal local HTTP server for delivering generated resource packs.
 *
 * <p>Serves {@code .zip} files from the packs directory. Opt-in only:
 * enabled via {@code resource-pack.http.enabled} in config.yml. Never
 * exposes anything outside the packs directory (path traversal rejected),
 * binds only where configured, and logs every request.
 */
public final class PackServer {

    private final File packsDir;
    private final String host;
    private final int port;
    private final JaLogger log;
    private HttpServer server;

    public PackServer(File packsDir, String host, int port, JaLogger log) {
        this.packsDir = packsDir;
        this.host = host == null || host.isEmpty() ? "127.0.0.1" : host;
        this.port = port;
        this.log = log;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        packsDir.mkdirs();
        server = HttpServer.create(new InetSocketAddress(host, port), 8);
        server.createContext("/packs/", new PackHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "JAE-pack-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        log.info("Pack HTTP server listening on " + host + ":" + port + " (/packs/)");
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            log.info("Pack HTTP server stopped.");
        }
    }

    public String urlFor(String fileName) {
        return "http://" + host + ":" + port + "/packs/" + fileName;
    }

    private final class PackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String prefix = "/packs/";
            String name = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
            if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
                send(exchange, 400, "Bad request".getBytes());
                return;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".zip")) {
                send(exchange, 403, "Forbidden".getBytes());
                return;
            }
            File file = new File(packsDir, name);
            String canonicalPacks;
            String canonicalFile;
            try {
                canonicalPacks = packsDir.getCanonicalPath() + File.separator;
                canonicalFile = file.getCanonicalPath();
            } catch (IOException e) {
                send(exchange, 500, "Error".getBytes());
                return;
            }
            if (!canonicalFile.startsWith(canonicalPacks) || !file.isFile()) {
                send(exchange, 404, "Not found".getBytes());
                return;
            }
            byte[] data = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            log.info("Serving pack " + name + " (" + data.length + " bytes) to "
                    + exchange.getRemoteAddress());
            send(exchange, 200, data);
        }

        private void send(HttpExchange exchange, int code, byte[] data) throws IOException {
            exchange.sendResponseHeaders(code, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }
}
